import 'package:dio/dio.dart';
import 'package:equity_committee_voting/core/di/injection.dart';
import 'package:equity_committee_voting/features/discussion/bloc/message_bloc.dart';
import 'package:equity_committee_voting/features/discussion/repository/message_repository.dart';
import 'package:equity_committee_voting/features/discussion/screens/discussion_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

class _FailingSendMessageRepository extends MessageRepository {
  _FailingSendMessageRepository() : super(Dio());

  @override
  Future<List<MessageModel>> getMessages(String caseId, {int page = 0}) async {
    return <MessageModel>[
      MessageModel(
        id: 'm-1',
        caseId: caseId,
        senderId: 'user-1',
        senderName: 'Sender',
        messageText: 'Existing message',
        createdAt: DateTime.now(),
      ),
    ];
  }

  @override
  Future<MessageModel> sendMessage(String caseId, String text, {String? parentId}) async {
    final requestOptions = RequestOptions(path: '/cases/$caseId/messages');
    throw DioException(
      requestOptions: requestOptions,
      response: Response<dynamic>(
        requestOptions: requestOptions,
        statusCode: 400,
        data: {'detail': 'Parent message does not belong to this case'},
      ),
      type: DioExceptionType.badResponse,
    );
  }
}

void main() {
  const secureStorageChannel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  setUp(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    await getIt.reset();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, (call) async {
      return null;
    });

    getIt.registerSingleton<FlutterSecureStorage>(const FlutterSecureStorage());
    getIt.registerFactory<MessageBloc>(() => MessageBloc(_FailingSendMessageRepository()));
  });

  tearDown(() async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, null);
    await getIt.reset();
  });

  testWidgets('shows snackbar when message send fails', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: DiscussionScreen(caseId: 'case-1'),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'reply text');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(
      find.text('Could not send reply in this case. Refresh messages and try again.'),
      findsOneWidget,
    );
  });
}
