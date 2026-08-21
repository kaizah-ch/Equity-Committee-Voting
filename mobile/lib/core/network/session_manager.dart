import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../constants/app_constants.dart';
import 'api_url_builder.dart';

class SessionManager {
  final FlutterSecureStorage _storage;
  final StreamController<void> _expiredController =
      StreamController<void>.broadcast();
  Future<String?>? _refreshInFlight;

  SessionManager(this._storage);

  Stream<void> get sessionExpired => _expiredController.stream;

  Future<String?> getValidAccessToken({bool forceRefresh = false}) async {
    final accessToken = await _storage.read(key: AppConstants.accessTokenKey);
    if (!forceRefresh &&
        accessToken != null &&
        accessToken.isNotEmpty &&
        !_isExpired(accessToken, skew: const Duration(seconds: 30))) {
      return accessToken;
    }

    return refreshAccessToken();
  }

  Future<bool> hasValidSession() async {
    final token = await getValidAccessToken();
    return token != null && token.isNotEmpty;
  }

  Future<void> saveTokenPair({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _storage.write(key: AppConstants.accessTokenKey, value: accessToken);
    await _storage.write(
        key: AppConstants.refreshTokenKey, value: refreshToken);
    await _persistUserClaims(accessToken);
  }

  Future<void> clearSession({bool notify = false}) async {
    await _storage.deleteAll();
    if (notify) {
      _expiredController.add(null);
    }
  }

  Future<String?> refreshAccessToken() {
    final inFlight = _refreshInFlight;
    if (inFlight != null) {
      return inFlight;
    }

    final refreshFuture = _refreshAccessTokenInternal();
    _refreshInFlight = refreshFuture;
    refreshFuture.whenComplete(() => _refreshInFlight = null);
    return refreshFuture;
  }

  Future<void> dispose() async {
    await _expiredController.close();
  }

  Future<String?> _refreshAccessTokenInternal() async {
    final refreshToken = await _storage.read(key: AppConstants.refreshTokenKey);
    if (refreshToken == null ||
        refreshToken.isEmpty ||
        _isExpired(refreshToken)) {
      await clearSession(notify: true);
      return null;
    }

    try {
      final refreshClient = Dio(BaseOptions(
        baseUrl: ApiUrlBuilder.dioBaseUrl,
        connectTimeout: const Duration(seconds: 15),
        receiveTimeout: const Duration(seconds: 30),
        headers: {'Content-Type': 'application/json'},
      ));

      final response = await refreshClient.post(
        ApiUrlBuilder.endpoint('auth/refresh'),
        data: {'refreshToken': refreshToken},
      );
      final accessToken = response.data['accessToken'] as String;
      final newRefreshToken = response.data['refreshToken'] as String;
      await saveTokenPair(
        accessToken: accessToken,
        refreshToken: newRefreshToken,
      );
      return accessToken;
    } catch (_) {
      await clearSession(notify: true);
      return null;
    }
  }

  Future<void> _persistUserClaims(String token) async {
    final claims = claimsFromToken(token);
    final userId = claims?['sub'] as String?;
    final role = claims?['role'] as String?;
    if (userId != null) {
      await _storage.write(key: AppConstants.userIdKey, value: userId);
    }
    if (role != null) {
      await _storage.write(key: AppConstants.userRoleKey, value: role);
    }
  }

  bool _isExpired(String token, {Duration skew = Duration.zero}) {
    final claims = claimsFromToken(token);
    final exp = claims?['exp'];
    if (exp is! int) {
      return true;
    }
    final expiresAt = DateTime.fromMillisecondsSinceEpoch(exp * 1000);
    return !expiresAt.isAfter(DateTime.now().add(skew));
  }

  static Map<String, dynamic>? claimsFromToken(String token) {
    final parts = token.split('.');
    if (parts.length != 3) return null;

    try {
      final payload =
          utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
      return jsonDecode(payload) as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }
}
