import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/network/network_error_mapper.dart';
import '../repository/user_admin_repository.dart';

abstract class UserAdminEvent {}

class LoadManagedUsers extends UserAdminEvent {
  final bool showLoading;
  LoadManagedUsers({this.showLoading = true});
}

class SaveManagedUser extends UserAdminEvent {
  final ManagedUser? existingUser;
  final String email;
  final String fullName;
  final UserRole role;
  final String? password;
  final bool active;

  SaveManagedUser({
    this.existingUser,
    required this.email,
    required this.fullName,
    required this.role,
    required this.active,
    this.password,
  });
}

class ToggleManagedUserActive extends UserAdminEvent {
  final ManagedUser user;
  ToggleManagedUserActive(this.user);
}

class ResetManagedUserPassword extends UserAdminEvent {
  final ManagedUser user;
  final String password;
  ResetManagedUserPassword(this.user, this.password);
}

abstract class UserAdminState {}

class UserAdminInitial extends UserAdminState {}
class UserAdminLoading extends UserAdminState {}
class UserAdminLoaded extends UserAdminState {
  final List<ManagedUser> users;
  final bool saving;
  final String? successMessage;

  UserAdminLoaded(
    this.users, {
    this.saving = false,
    this.successMessage,
  });
}
class UserAdminError extends UserAdminState {
  final String message;
  UserAdminError(this.message);
}

class UserAdminBloc extends Bloc<UserAdminEvent, UserAdminState> {
  final UserAdminRepository _repository;

  UserAdminBloc(this._repository) : super(UserAdminInitial()) {
    on<LoadManagedUsers>(_onLoad);
    on<SaveManagedUser>(_onSave);
    on<ToggleManagedUserActive>(_onToggleActive);
    on<ResetManagedUserPassword>(_onResetPassword);
  }

  Future<void> _onLoad(
      LoadManagedUsers event, Emitter<UserAdminState> emit) async {
    if (event.showLoading && state is! UserAdminLoaded) {
      emit(UserAdminLoading());
    }
    try {
      emit(UserAdminLoaded(await _repository.getUsers()));
    } catch (error) {
      emit(UserAdminError(mapNetworkError(error)));
    }
  }

  Future<void> _onSave(
      SaveManagedUser event, Emitter<UserAdminState> emit) async {
    final previous = state;
    if (previous is UserAdminLoaded) {
      emit(UserAdminLoaded(previous.users, saving: true));
    }
    try {
      if (event.existingUser == null) {
        await _repository.createUser(
          email: event.email,
          fullName: event.fullName,
          role: event.role,
          password: event.password ?? '',
        );
      } else {
        await _repository.updateUser(
          id: event.existingUser!.id,
          email: event.email,
          fullName: event.fullName,
          role: event.role,
          active: event.active,
        );
      }
      emit(UserAdminLoaded(await _repository.getUsers(),
          successMessage:
              event.existingUser == null ? 'User created' : 'User updated'));
    } catch (error) {
      if (previous is UserAdminLoaded) {
        emit(UserAdminLoaded(previous.users));
      }
      emit(UserAdminError(mapNetworkError(error)));
    }
  }

  Future<void> _onToggleActive(
      ToggleManagedUserActive event, Emitter<UserAdminState> emit) async {
    final previous = state;
    if (previous is UserAdminLoaded) {
      emit(UserAdminLoaded(previous.users, saving: true));
    }
    try {
      if (event.user.active) {
        await _repository.deactivateUser(event.user.id);
      } else {
        await _repository.reactivateUser(event.user.id);
      }
      emit(UserAdminLoaded(await _repository.getUsers(),
          successMessage:
              event.user.active ? 'User deactivated' : 'User reactivated'));
    } catch (error) {
      if (previous is UserAdminLoaded) {
        emit(UserAdminLoaded(previous.users));
      }
      emit(UserAdminError(mapNetworkError(error)));
    }
  }

  Future<void> _onResetPassword(
      ResetManagedUserPassword event, Emitter<UserAdminState> emit) async {
    final previous = state;
    if (previous is UserAdminLoaded) {
      emit(UserAdminLoaded(previous.users, saving: true));
    }
    try {
      await _repository.resetPassword(
        id: event.user.id,
        password: event.password,
      );
      emit(UserAdminLoaded(await _repository.getUsers(),
          successMessage: 'Password reset'));
    } catch (error) {
      if (previous is UserAdminLoaded) {
        emit(UserAdminLoaded(previous.users));
      }
      emit(UserAdminError(mapNetworkError(error)));
    }
  }
}
