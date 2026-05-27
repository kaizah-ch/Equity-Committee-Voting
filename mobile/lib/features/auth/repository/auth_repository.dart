import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../core/constants/app_constants.dart';
import '../models/auth_models.dart';

class AuthRepository {
  final Dio _dio;
  final FlutterSecureStorage _storage;

  AuthRepository(this._dio, this._storage);

  Future<TokenPair> login(LoginCredentials credentials) async {
    final response = await _dio.post('/api/auth/login', data: {
      'email': credentials.email,
      'password': credentials.password,
    });
    final tokens = TokenPair.fromJson(response.data as Map<String, dynamic>);
    await _storage.write(
        key: AppConstants.accessTokenKey, value: tokens.accessToken);
    await _storage.write(
        key: AppConstants.refreshTokenKey, value: tokens.refreshToken);
    final claims = _claimsFromToken(tokens.accessToken);
    final userId = claims?['sub'] as String?;
    final role = claims?['role'] as String?;
    if (userId != null) {
      await _storage.write(key: AppConstants.userIdKey, value: userId);
    }
    if (role != null) {
      await _storage.write(key: AppConstants.userRoleKey, value: role);
    }
    return tokens;
  }

  Future<bool> hasValidToken() async {
    final token = await _storage.read(key: AppConstants.accessTokenKey);
    if (token == null || token.isEmpty) {
      return false;
    }
    final claims = _claimsFromToken(token);
    final userId = claims?['sub'] as String?;
    final role = claims?['role'] as String?;
    if (userId != null) {
      await _storage.write(key: AppConstants.userIdKey, value: userId);
    }
    if (role != null) {
      await _storage.write(key: AppConstants.userRoleKey, value: role);
    }
    return true;
  }

  Future<void> logout() async {
    await _storage.deleteAll();
  }

  Map<String, dynamic>? _claimsFromToken(String token) {
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
