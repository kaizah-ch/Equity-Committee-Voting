import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../core/network/network_error_mapper.dart';
import '../repository/notification_repository.dart';

abstract class NotificationEvent {}
class LoadNotifications extends NotificationEvent {
  final bool showLoading;
  LoadNotifications({this.showLoading = true});
}
class MarkNotificationRead extends NotificationEvent { final String id; MarkNotificationRead(this.id); }
class MarkAllNotificationsRead extends NotificationEvent {}

abstract class NotificationState {}
class NotificationInitial extends NotificationState {}
class NotificationLoading extends NotificationState {}
class NotificationLoaded extends NotificationState {
  final List<NotificationModel> notifications;
  final int unreadCount;
  NotificationLoaded(this.notifications)
      : unreadCount = notifications.where((n) => !n.isRead).length;
}
class NotificationError extends NotificationState { final String msg; NotificationError(this.msg); }

class NotificationBloc extends Bloc<NotificationEvent, NotificationState> {
  final NotificationRepository _repo;
  NotificationBloc(this._repo) : super(NotificationInitial()) {
    on<LoadNotifications>(_onLoad);
    on<MarkNotificationRead>(_onMarkRead);
    on<MarkAllNotificationsRead>(_onMarkAllRead);
  }

  Future<void> _onLoad(LoadNotifications e, Emitter<NotificationState> emit) async {
    if (e.showLoading && state is! NotificationLoaded) {
      emit(NotificationLoading());
    }
    try {
      emit(NotificationLoaded(await _repo.getNotifications()));
    } catch (err) {
      emit(NotificationError(mapNetworkError(err)));
    }
  }

  Future<void> _onMarkRead(MarkNotificationRead e, Emitter<NotificationState> emit) async {
    final previous = state;
    if (previous is NotificationLoaded) {
      final updated = previous.notifications.map((n) {
        if (n.id == e.id && !n.isRead) {
          return NotificationModel(
            id: n.id,
            type: n.type,
            title: n.title,
            body: n.body,
            caseId: n.caseId,
            isRead: true,
            createdAt: n.createdAt,
          );
        }
        return n;
      }).toList(growable: false);
      emit(NotificationLoaded(updated));
    }

    try {
      await _repo.markRead(e.id);
    } catch (_) {
      add(LoadNotifications(showLoading: false));
      return;
    }
    add(LoadNotifications(showLoading: false));
  }

  Future<void> _onMarkAllRead(MarkAllNotificationsRead e, Emitter<NotificationState> emit) async {
    final previous = state;
    if (previous is NotificationLoaded) {
      final updated = previous.notifications.map((n) {
        if (!n.isRead) {
          return NotificationModel(
            id: n.id,
            type: n.type,
            title: n.title,
            body: n.body,
            caseId: n.caseId,
            isRead: true,
            createdAt: n.createdAt,
          );
        }
        return n;
      }).toList(growable: false);
      emit(NotificationLoaded(updated));
    }

    try {
      await _repo.markAllRead();
    } catch (_) {
      add(LoadNotifications(showLoading: false));
      return;
    }
    add(LoadNotifications(showLoading: false));
  }
}
