import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import 'core/di/injection.dart';
import 'core/router/app_router.dart';
import 'features/auth/bloc/auth_bloc.dart';
import 'features/notifications/bloc/notification_bloc.dart';
import 'features/notifications/repository/push_notification_repository.dart';
import 'shared/themes/app_theme.dart';

@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  try {
    await Firebase.initializeApp();
  } catch (_) {
    // Background delivery should not crash dev builds without Firebase files.
  }
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  var firebaseReady = false;
  try {
    await Firebase.initializeApp();
    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
    firebaseReady = true;
  } catch (e) {
    // Allow local/dev startup when Firebase resources are not configured yet.
    debugPrint('Firebase initialization skipped: $e');
  }
  configureDependencies();
  runApp(EquityVotingApp(firebaseReady: firebaseReady));
}

class EquityVotingApp extends StatefulWidget {
  final bool firebaseReady;

  const EquityVotingApp({super.key, required this.firebaseReady});

  @override
  State<EquityVotingApp> createState() => _EquityVotingAppState();
}

class _EquityVotingAppState extends State<EquityVotingApp> {
  late final AuthBloc _authBloc;
  late final NotificationBloc _notificationBloc;
  late final PushNotificationRepository _pushRepository;
  late final GoRouter _router;
  StreamSubscription<String>? _pushOpenSubscription;
  String? _pendingCaseId;

  @override
  void initState() {
    super.initState();
    _authBloc = getIt<AuthBloc>()..add(AppStarted());
    _notificationBloc = getIt<NotificationBloc>();
    _pushRepository = getIt<PushNotificationRepository>();
    _router = appRouter(_authBloc);
    _authBloc.stream.listen((state) {
      if (state is AuthAuthenticated && _pendingCaseId != null) {
        final caseId = _pendingCaseId;
        _pendingCaseId = null;
        _router.go('/cases/$caseId');
      }
    });
    if (widget.firebaseReady) {
      _pushRepository.configureMessageHandlers(
        onForegroundNotification: () => _notificationBloc.add(
          LoadNotifications(showLoading: false),
        ),
      );
      _pushOpenSubscription =
          _pushRepository.caseOpenRequests.listen(_openCaseFromPush);
    }
  }

  @override
  void dispose() {
    _pushOpenSubscription?.cancel();
    _authBloc.close();
    _notificationBloc.close();
    super.dispose();
  }

  void _openCaseFromPush(String caseId) {
    if (_authBloc.state is AuthAuthenticated) {
      _router.go('/cases/$caseId');
      return;
    }
    _pendingCaseId = caseId;
  }

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider.value(value: _authBloc),
        BlocProvider.value(value: _notificationBloc),
      ],
      child: MaterialApp.router(
        title: 'Equity Committee Voting',
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        routerConfig: _router,
        debugShowCheckedModeBanner: false,
      ),
    );
  }
}
