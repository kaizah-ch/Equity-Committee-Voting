import 'package:dio/dio.dart';
import 'package:equity_committee_voting/features/auth/bloc/auth_bloc.dart';
import 'package:equity_committee_voting/features/auth/models/auth_models.dart';
import 'package:equity_committee_voting/features/auth/repository/auth_repository.dart';
import 'package:equity_committee_voting/features/auth/screens/login_screen.dart';
import 'package:equity_committee_voting/features/notifications/repository/push_notification_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class _FailingAuthRepository extends AuthRepository {
  _FailingAuthRepository()
      : super(
          Dio(),
          const FlutterSecureStorage(),
        );

  @override
  Future<TokenPair> login(LoginCredentials credentials) async {
    final requestOptions = RequestOptions(path: '/auth/login');
    throw DioException(
      requestOptions: requestOptions,
      response: Response<dynamic>(
        requestOptions: requestOptions,
        statusCode: 401,
        data: {'detail': 'Invalid credentials'},
      ),
      type: DioExceptionType.badResponse,
    );
  }
}

class _NoopPushNotificationRepository extends PushNotificationRepository {
  _NoopPushNotificationRepository() : super(Dio());

  @override
  Future<void> registerCurrentDevice() async {}

  @override
  Future<void> unregisterCurrentDevice() async {}

  @override
  void startTokenRefreshRegistration() {}

  @override
  Future<void> dispose() async {}
}

void main() {
  const secureStorageChannel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  setUp(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, (call) async {
      return null;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, null);
  });

  testWidgets('shows friendly inline auth error for invalid credentials', (tester) async {
    final bloc = AuthBloc(
      _FailingAuthRepository(),
      const FlutterSecureStorage(),
      _NoopPushNotificationRepository(),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: BlocProvider<AuthBloc>.value(
          value: bloc,
          child: const LoginScreen(showLogo: false),
        ),
      ),
    );

    await tester.enterText(find.byType(TextFormField).at(0), 'user@example.com');
    await tester.enterText(find.byType(TextFormField).at(1), 'badpass');
    await tester.tap(find.text('Sign In'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Email or password is incorrect.'), findsOneWidget);
  });
}
