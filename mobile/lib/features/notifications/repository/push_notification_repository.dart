import 'dart:async';

import 'package:dio/dio.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

class PushNotificationRepository {
  final Dio _dio;
  final StreamController<String> _caseOpenController =
      StreamController<String>.broadcast();
  StreamSubscription<String>? _tokenRefreshSubscription;
  StreamSubscription<RemoteMessage>? _foregroundSubscription;
  StreamSubscription<RemoteMessage>? _tapSubscription;

  PushNotificationRepository(this._dio);

  Stream<String> get caseOpenRequests => _caseOpenController.stream;

  Future<void> configureMessageHandlers({
    required VoidCallback onForegroundNotification,
  }) async {
    try {
      await FirebaseMessaging.instance
          .setForegroundNotificationPresentationOptions(
        alert: true,
        badge: true,
        sound: true,
      );

      _foregroundSubscription ??=
          FirebaseMessaging.onMessage.listen((message) {
        onForegroundNotification();
      });
      _tapSubscription ??=
          FirebaseMessaging.onMessageOpenedApp.listen(_handleMessageOpen);

      final initialMessage =
          await FirebaseMessaging.instance.getInitialMessage();
      if (initialMessage != null) {
        _handleMessageOpen(initialMessage);
      }
    } catch (_) {
      // Firebase may be unavailable in local/dev builds without project files.
    }
  }

  Future<void> registerCurrentDevice() async {
    try {
      await FirebaseMessaging.instance.requestPermission();
      final token = await FirebaseMessaging.instance.getToken();
      if (token == null || token.isEmpty) return;
      await _registerToken(token);
    } catch (_) {
      // Firebase may be unavailable in local/dev builds without project files.
    }
  }

  Future<void> unregisterCurrentDevice() async {
    try {
      final token = await FirebaseMessaging.instance.getToken();
      if (token == null || token.isEmpty) return;
      await _dio.delete('/api/me/device-token', data: {
        'token': token,
        'platform': _platform,
      });
    } catch (_) {
      // Logout should not fail if Firebase or the network is unavailable.
    }
  }

  void startTokenRefreshRegistration() {
    _tokenRefreshSubscription ??=
        FirebaseMessaging.instance.onTokenRefresh.listen((token) {
      if (token.isEmpty) return;
      _registerToken(token);
    });
  }

  Future<void> dispose() async {
    await _tokenRefreshSubscription?.cancel();
    await _foregroundSubscription?.cancel();
    await _tapSubscription?.cancel();
    _tokenRefreshSubscription = null;
    _foregroundSubscription = null;
    _tapSubscription = null;
  }

  Future<void> _registerToken(String token) async {
    await _dio.put('/api/me/device-token', data: {
      'token': token,
      'platform': _platform,
    });
  }

  void _handleMessageOpen(RemoteMessage message) {
    final caseId = message.data['caseId'];
    if (caseId is String && caseId.isNotEmpty) {
      _caseOpenController.add(caseId);
    }
  }

  String get _platform {
    if (kIsWeb) return 'web';
    return defaultTargetPlatform.name;
  }
}
