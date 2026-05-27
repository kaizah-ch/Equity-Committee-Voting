import 'package:dio/dio.dart';
import 'package:equity_committee_voting/features/discussion/bloc/message_bloc.dart';
import 'package:equity_committee_voting/features/discussion/repository/message_repository.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeMessageRepository extends MessageRepository {
  _FakeMessageRepository({
    List<MessageModel>? messages,
    this.sendError,
  })  : _messages = messages ?? <MessageModel>[],
        super(Dio());

  final List<MessageModel> _messages;
  final Object? sendError;

  @override
  Future<List<MessageModel>> getMessages(String caseId, {int page = 0}) async {
    return List<MessageModel>.from(_messages);
  }

  @override
  Future<MessageModel> sendMessage(
    String caseId,
    String text, {
    String? parentId,
  }) async {
    if (sendError != null) {
      throw sendError!;
    }
    return MessageModel(
      id: 'new-message',
      caseId: caseId,
      senderId: 'user-1',
      senderName: 'Sender',
      messageText: text,
      parentMessageId: parentId,
      createdAt: DateTime.now(),
    );
  }
}

void main() {
  MessageModel seedMessage() => MessageModel(
        id: 'm-1',
        caseId: 'case-1',
        senderId: 'user-1',
        senderName: 'Sender',
        messageText: 'seed',
        createdAt: DateTime.now(),
      );

  DioException discussionError(String detail) {
    final requestOptions = RequestOptions(path: '/api/cases/case-1/messages');
    return DioException(
      requestOptions: requestOptions,
      response: Response<dynamic>(
        requestOptions: requestOptions,
        statusCode: 400,
        data: {'detail': detail},
      ),
      type: DioExceptionType.badResponse,
    );
  }

  test('emits send-failed state then keeps loaded list when send fails', () async {
    final repo = _FakeMessageRepository(
      messages: [seedMessage()],
      sendError: discussionError('Parent message does not belong to this case'),
    );
    final bloc = MessageBloc(repo);
    final emitted = <MessageState>[];
    final sub = bloc.stream.listen(emitted.add);

    bloc.add(LoadMessages('case-1'));
    await Future<void>.delayed(const Duration(milliseconds: 20));
    bloc.add(SendMessage('case-1', 'reply', parentId: 'bad-parent'));
    await Future<void>.delayed(const Duration(milliseconds: 20));

    final sendFailed = emitted.whereType<MessageSendFailed>().toList();
    expect(sendFailed.length, 1);
    expect(
      sendFailed.single.msg,
      'Could not send reply in this case. Refresh messages and try again.',
    );
    expect(sendFailed.single.messages.length, 1);
    expect(emitted.last, isA<MessageLoaded>());
    expect((emitted.last as MessageLoaded).messages.length, 1);

    await sub.cancel();
    await bloc.close();
  });
}
