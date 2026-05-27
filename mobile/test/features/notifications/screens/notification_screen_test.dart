import 'package:dio/dio.dart';
import 'package:equity_committee_voting/core/di/injection.dart';
import 'package:equity_committee_voting/features/notifications/bloc/notification_bloc.dart';
import 'package:equity_committee_voting/features/notifications/repository/notification_repository.dart';
import 'package:equity_committee_voting/features/notifications/screens/notification_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

class _FakeNotificationRepository extends NotificationRepository {
  _FakeNotificationRepository(this.notifications) : super(Dio());

  final List<NotificationModel> notifications;
  final List<String> markedReadIds = <String>[];

  @override
  Future<List<NotificationModel>> getNotifications({int page = 0}) async {
    return notifications;
  }

  @override
  Future<void> markRead(String id) async {
    markedReadIds.add(id);
  }
}

void main() {
  const secureStorageChannel =
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  setUp(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    await getIt.reset();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, (call) async {
      return null;
    });

    getIt.registerSingleton<FlutterSecureStorage>(const FlutterSecureStorage());
  });

  tearDown(() async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, null);
    await getIt.reset();
  });

  testWidgets('notification tap marks unread notification and opens its case',
      (tester) async {
    final repository = _FakeNotificationRepository([
      NotificationModel(
        id: 'notification-1',
        type: 'CASE_CREATED',
        title: 'New Case: EC-TEST-001',
        body: 'A new case has been submitted: Client A',
        caseId: 'case-123',
        isRead: false,
        createdAt: DateTime(2026, 5, 27, 10),
      ),
    ]);
    final bloc = NotificationBloc(repository);
    final router = GoRouter(
      initialLocation: '/notifications',
      routes: [
        GoRoute(
          path: '/notifications',
          builder: (context, state) => BlocProvider.value(
            value: bloc,
            child: const NotificationScreen(),
          ),
        ),
        GoRoute(
          path: '/cases/:id',
          builder: (context, state) =>
              Text('Opened case ${state.pathParameters['id']}'),
        ),
      ],
    );

    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    await tester.tap(find.text('New Case: EC-TEST-001'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(repository.markedReadIds, contains('notification-1'));
    expect(find.text('Opened case case-123'), findsOneWidget);
  });
}
