import 'package:dio/dio.dart';
import '../../../core/network/session_manager.dart';
import '../models/auth_models.dart';

class AuthRepository {
  final Dio _dio;
  final SessionManager _sessionManager;

  AuthRepository(this._dio, this._sessionManager);

  Future<TokenPair> login(LoginCredentials credentials) async {
    final response = await _dio.post('auth/login', data: {
      'email': credentials.email,
      'password': credentials.password,
    });
    final tokens = TokenPair.fromJson(response.data as Map<String, dynamic>);
    await _sessionManager.saveTokenPair(
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    );
    return tokens;
  }

  Future<bool> hasValidToken() async {
    return _sessionManager.hasValidSession();
  }

  Future<void> logout() async {
    await _sessionManager.clearSession();
  }
}
