import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import 'package:timeago/timeago.dart' as timeago;

import '../../../core/constants/app_constants.dart';
import '../../../core/di/injection.dart';
import '../../../core/network/websocket_client.dart';
import '../bloc/notification_bloc.dart';
import '../repository/notification_repository.dart';

class NotificationScreen extends StatefulWidget {
  const NotificationScreen({super.key});

  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen> {
  final FlutterSecureStorage _storage = getIt<FlutterSecureStorage>();
  WebSocketClient? _wsClient;
  StompUnsubscribe? _unsubscribe;
  bool _wsInitialized = false;

  @override
  void initState() {
    super.initState();
    context.read<NotificationBloc>().add(LoadNotifications());
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_wsInitialized) return;
    _connectRealtime();
    _wsInitialized = true;
  }

  @override
  void dispose() {
    _unsubscribe?.call();
    _wsClient?.disconnect();
    super.dispose();
  }

  Future<void> _connectRealtime() async {
    final token = await _storage.read(key: AppConstants.accessTokenKey);
    if (!mounted || token == null || token.isEmpty) return;

    final ws = WebSocketClient(token);
    ws.connect(onConnected: () {
      _unsubscribe = ws.subscribe('/user/queue/notifications', (_) {
        if (!mounted) return;
        context.read<NotificationBloc>().add(LoadNotifications(showLoading: false));
      });
    });
    _wsClient = ws;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          BlocBuilder<NotificationBloc, NotificationState>(
            builder: (context, state) {
              final unread = state is NotificationLoaded ? state.unreadCount : 0;
              if (unread == 0) return const SizedBox.shrink();
              return IconButton(
                onPressed: () => context.read<NotificationBloc>().add(MarkAllNotificationsRead()),
                tooltip: 'Mark all read',
                icon: const Icon(Icons.done_all),
              );
            },
          ),
        ],
      ),
      body: BlocBuilder<NotificationBloc, NotificationState>(
        builder: (context, state) {
          if (state is NotificationLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          if (state is NotificationError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(state.msg, textAlign: TextAlign.center),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: () =>
                          context.read<NotificationBloc>().add(LoadNotifications()),
                      icon: const Icon(Icons.refresh),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            );
          }
          if (state is NotificationLoaded) {
            if (state.notifications.isEmpty) {
              return RefreshIndicator(
                onRefresh: () async => context.read<NotificationBloc>().add(LoadNotifications(showLoading: false)),
                child: ListView(
                  children: [
                    SizedBox(height: MediaQuery.sizeOf(context).height * 0.22),
                    const _EmptyNotifications(),
                  ],
                ),
              );
            }
            return RefreshIndicator(
              onRefresh: () async => context.read<NotificationBloc>().add(LoadNotifications(showLoading: false)),
              child: ListView.separated(
                itemCount: state.notifications.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, i) {
                  final n = state.notifications[i];
                  return _NotificationTile(
                    notification: n,
                    onTap: () {
                      if (!n.isRead) {
                        context.read<NotificationBloc>().add(MarkNotificationRead(n.id));
                      }
                      if (n.caseId != null) {
                        context.go('/cases/${n.caseId}');
                      }
                    },
                  );
                },
              ),
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  final NotificationModel notification;
  final VoidCallback onTap;

  const _NotificationTile({
    required this.notification,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isRead = notification.isRead;
    final createdAt = notification.createdAt;
    final exactTime = DateFormat('dd MMM yyyy, HH:mm').format(createdAt.toLocal());

    return Material(
      color: isRead
          ? Colors.transparent
          : colorScheme.primary.withValues(alpha: 0.07),
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: _typeColor(context, notification.type)
                      .withValues(alpha: isRead ? 0.12 : 0.18),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  _typeIcon(notification.type),
                  color: _typeColor(context, notification.type),
                  size: 21,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Text(
                            notification.title,
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: isRead ? FontWeight.w600 : FontWeight.w800,
                            ),
                          ),
                        ),
                        if (!isRead) ...[
                          const SizedBox(width: 8),
                          Container(
                            width: 9,
                            height: 9,
                            margin: const EdgeInsets.only(top: 4),
                            decoration: BoxDecoration(
                              color: colorScheme.primary,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 5),
                    Text(
                      notification.body,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: colorScheme.onSurface.withValues(alpha: 0.78),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '${timeago.format(createdAt)} - $exactTime',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              if (notification.caseId != null) ...[
                const SizedBox(width: 8),
                Icon(
                  Icons.chevron_right,
                  color: colorScheme.onSurfaceVariant,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  IconData _typeIcon(String type) => switch (type) {
        'CASE_CREATED' => Icons.note_add_outlined,
        'NEW_COMMENT' => Icons.chat_bubble_outline,
        'USER_MENTIONED' => Icons.alternate_email,
        'VOTING_OPENED' => Icons.how_to_vote_outlined,
        'VOTE_REMINDER' => Icons.schedule,
        'VOTING_DEADLINE_APPROACHING' => Icons.timer_outlined,
        'FINAL_VERDICT' => Icons.gavel_outlined,
        _ => Icons.notifications_none,
      };

  Color _typeColor(BuildContext context, String type) {
    final colorScheme = Theme.of(context).colorScheme;
    return switch (type) {
      'FINAL_VERDICT' => Colors.green.shade700,
      'VOTING_DEADLINE_APPROACHING' => Colors.deepOrange.shade700,
      'VOTE_REMINDER' => Colors.amber.shade800,
      'VOTING_OPENED' => Colors.purple.shade700,
      'NEW_COMMENT' => Colors.blue.shade700,
      'USER_MENTIONED' => Colors.teal.shade700,
      _ => colorScheme.primary,
    };
  }
}

class _EmptyNotifications extends StatelessWidget {
  const _EmptyNotifications();

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          children: [
            Icon(
              Icons.notifications_none,
              size: 52,
              color: colorScheme.onSurfaceVariant,
            ),
            const SizedBox(height: 12),
            Text(
              'No notifications',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            Text(
              'Case updates, voting reminders, and final verdicts will appear here.',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}
