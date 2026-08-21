import 'package:dio/dio.dart';
import 'api_url_builder.dart';
import 'network_error_mapper.dart';
import 'session_manager.dart';

Dio createDioClient(SessionManager sessionManager) {
  final dio = Dio(BaseOptions(
    baseUrl: ApiUrlBuilder.dioBaseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 30),
    headers: {'Content-Type': 'application/json'},
  ));

  dio.interceptors.add(InterceptorsWrapper(
    onRequest: (options, handler) async {
      if (!Uri.parse(options.path).hasScheme) {
        options.path = ApiUrlBuilder.endpoint(options.path);
      }
      final isAuthEndpoint = options.path.contains('/auth/login') ||
          options.path.contains('/auth/refresh');
      final token = isAuthEndpoint
          ? null
          : await sessionManager.getValidAccessToken();
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
      handler.next(options);
    },
    onError: (error, handler) async {
      final statusCode = error.response?.statusCode;
      final req = error.requestOptions;
      final isAuthEndpoint = req.path.contains('/auth/login') ||
          req.path.contains('/auth/refresh');
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
        final accessToken =
            await sessionManager.getValidAccessToken(forceRefresh: true);
        if (accessToken != null && accessToken.isNotEmpty) {
          req.headers['Authorization'] = 'Bearer $accessToken';
          req.extra['retried'] = true;

          final retryResponse = await dio.fetch(req);
          handler.resolve(retryResponse);
          return;
        }
      }

      handler.next(error);
    },
  ));

  return dio;
}
