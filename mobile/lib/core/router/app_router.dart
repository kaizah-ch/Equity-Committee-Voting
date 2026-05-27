import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/bloc/auth_bloc.dart';
import '../../features/auth/screens/login_screen.dart';
import '../../features/cases/screens/case_detail_screen.dart';
import '../../features/cases/screens/case_list_screen.dart';
import '../../features/cases/screens/create_case_screen.dart';
import '../../features/notifications/screens/notification_screen.dart';

GoRouter appRouter(AuthBloc authBloc) {
  return GoRouter(
    initialLocation: '/',
    refreshListenable: GoRouterRefreshStream(authBloc.stream),
    redirect: (context, state) {
      final authState = authBloc.state;
      final isLoggedIn = authState is AuthAuthenticated;
      final isLoginRoute = state.matchedLocation == '/login';

      if (!isLoggedIn && !isLoginRoute) return '/login';
      if (isLoggedIn && isLoginRoute) return '/';
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (ctx, _) => const LoginScreen()),
      GoRoute(
        path: '/',
        builder: (ctx, _) => const CaseListScreen(),
        routes: [
          GoRoute(
            path: 'cases/new',
            builder: (ctx, _) => const CreateCaseScreen(),
          ),
          GoRoute(
            path: 'cases/:id',
            builder: (ctx, state) =>
                CaseDetailScreen(caseId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: 'notifications',
            builder: (ctx, _) => const NotificationScreen(),
          ),
        ],
      ),
    ],
  );
}

class GoRouterRefreshStream extends ChangeNotifier {
  late final StreamSubscription<dynamic> _subscription;

  GoRouterRefreshStream(Stream<dynamic> stream) {
    _subscription = stream.asBroadcastStream().listen((_) => notifyListeners());
  }

  @override
  void dispose() {
    _subscription.cancel();
    super.dispose();
  }
}
