import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../core/network/network_error_mapper.dart';
import '../models/auth_models.dart';
import '../repository/auth_repository.dart';
import '../../notifications/repository/push_notification_repository.dart';

// Events
abstract class AuthEvent {}

class AppStarted extends AuthEvent {}
class LoginRequested extends AuthEvent {
  final LoginCredentials credentials;
  LoginRequested(this.credentials);
}
class LogoutRequested extends AuthEvent {}

// States
abstract class AuthState {}

class AuthInitial extends AuthState {}
class AuthLoading extends AuthState {}
class AuthAuthenticated extends AuthState {
  final String userId;
  AuthAuthenticated(this.userId);
}
class AuthUnauthenticated extends AuthState {}
class AuthFailure extends AuthState {
  final String message;
  AuthFailure(this.message);
}

// BLoC
class AuthBloc extends Bloc<AuthEvent, AuthState> {
  final AuthRepository _repository;
  final FlutterSecureStorage _storage;
  final PushNotificationRepository _pushRepository;

  AuthBloc(this._repository, this._storage, this._pushRepository) : super(AuthInitial()) {
    on<AppStarted>(_onAppStarted);
    on<LoginRequested>(_onLoginRequested);
    on<LogoutRequested>(_onLogoutRequested);
  }

  Future<void> _onAppStarted(AppStarted event, Emitter<AuthState> emit) async {
    final hasToken = await _repository.hasValidToken();
    if (hasToken) {
      final userId = await _storage.read(key: 'user_id') ?? '';
      _pushRepository.startTokenRefreshRegistration();
      await _pushRepository.registerCurrentDevice();
      emit(AuthAuthenticated(userId));
    } else {
      emit(AuthUnauthenticated());
    }
  }

  Future<void> _onLoginRequested(LoginRequested event, Emitter<AuthState> emit) async {
    emit(AuthLoading());
    try {
      await _repository.login(event.credentials);
      final userId = await _storage.read(key: 'user_id') ?? '';
      _pushRepository.startTokenRefreshRegistration();
      await _pushRepository.registerCurrentDevice();
      emit(AuthAuthenticated(userId));
    } catch (e) {
      emit(AuthFailure(mapNetworkError(e)));
    }
  }

  Future<void> _onLogoutRequested(LogoutRequested event, Emitter<AuthState> emit) async {
    await _pushRepository.unregisterCurrentDevice();
    await _repository.logout();
    emit(AuthUnauthenticated());
  }

  @override
  Future<void> close() async {
    await _pushRepository.dispose();
    return super.close();
  }
}
