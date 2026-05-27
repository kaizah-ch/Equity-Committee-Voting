import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../constants/app_constants.dart';
import 'network_error_mapper.dart';

Dio createDioClient(FlutterSecureStorage storage) {
  final dio = Dio(BaseOptions(
    baseUrl: AppConstants.baseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 30),
    headers: {'Content-Type': 'application/json'},
  ));

  dio.interceptors.add(InterceptorsWrapper(
    onRequest: (options, handler) async {
      final token = await storage.read(key: AppConstants.accessTokenKey);
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
      handler.next(options);
    },
    onError: (error, handler) async {
      final statusCode = error.response?.statusCode;
      final req = error.requestOptions;
      final isAuthEndpoint = req.path.contains('/api/auth/login') ||
          req.path.contains('/api/auth/refresh');
      final alreadyRetried = req.extra['retried'] == true;
      final networkRetried = req.extra['networkRetried'] == true;

      if (req.method.toUpperCase() == 'GET' &&
          !networkRetried &&
          isTransientNetworkError(error)) {
        try {
          await Future<void>.delayed(const Duration(milliseconds: 500));
          req.extra['networkRetried'] = true;
          final retryResponse = await dio.fetch(req);
          handler.resolve(retryResponse);
          return;
        } catch (_) {
          handler.next(error);
          return;
        }
      }

      if (statusCode == 401 && !isAuthEndpoint && !alreadyRetried) {
        final refreshToken =
            await storage.read(key: AppConstants.refreshTokenKey);
        if (refreshToken == null || refreshToken.isEmpty) {
          await storage.deleteAll();
          handler.next(error);
          return;
        }

        try {
          final refreshClient = Dio(BaseOptions(
            baseUrl: AppConstants.baseUrl,
            connectTimeout: const Duration(seconds: 15),
            receiveTimeout: const Duration(seconds: 30),
            headers: {'Content-Type': 'application/json'},
          ));

          final refreshResp =
              await refreshClient.post('/api/auth/refresh', data: {
            'refreshToken': refreshToken,
          });
          final accessToken = refreshResp.data['accessToken'] as String;
          final newRefreshToken = refreshResp.data['refreshToken'] as String;

          await storage.write(
              key: AppConstants.accessTokenKey, value: accessToken);
          await storage.write(
              key: AppConstants.refreshTokenKey, value: newRefreshToken);

          req.headers['Authorization'] = 'Bearer $accessToken';
          req.extra['retried'] = true;

          final retryResponse = await dio.fetch(req);
          handler.resolve(retryResponse);
          return;
        } catch (_) {
          await storage.deleteAll();
          handler.next(error);
          return;
        }
      }

      handler.next(error);
    },
  ));

  return dio;
}
