import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../core/network/network_error_mapper.dart';
import '../repository/message_repository.dart';

abstract class MessageEvent {}

class LoadMessages extends MessageEvent {
  final String caseId;
  LoadMessages(this.caseId);
}

class SendMessage extends MessageEvent {
  final String caseId, text;
  final String? parentId;
  SendMessage(this.caseId, this.text, {this.parentId});
}

class MessageReceived extends MessageEvent {
  final MessageModel message;
  MessageReceived(this.message);
}

abstract class MessageState {}

class MessageInitial extends MessageState {}

class MessageLoading extends MessageState {}

class MessageLoaded extends MessageState {
  final List<MessageModel> messages;
  MessageLoaded(this.messages);
}

class MessageError extends MessageState {
  final String msg;
  MessageError(this.msg);
}

class MessageSendFailed extends MessageState {
  final String msg;
  final String caseId;
  final String text;
  final String? parentId;
  final List<MessageModel> messages;
  MessageSendFailed(
    this.msg,
    this.messages, {
    required this.caseId,
    required this.text,
    this.parentId,
  });
}

class MessageBloc extends Bloc<MessageEvent, MessageState> {
  final MessageRepository _repo;
  final List<MessageModel> _messages = [];

  MessageBloc(this._repo) : super(MessageInitial()) {
    on<LoadMessages>(_onLoad);
    on<SendMessage>(_onSend);
    on<MessageReceived>(_onReceived);
  }

  Future<void> _onLoad(LoadMessages e, Emitter<MessageState> emit) async {
    emit(MessageLoading());
    try {
      _messages
        ..clear()
        ..addAll(await _repo.getMessages(e.caseId));
      emit(MessageLoaded(List.from(_messages)));
    } catch (err) {
      emit(MessageError(mapNetworkError(err)));
    }
  }

  Future<void> _onSend(SendMessage e, Emitter<MessageState> emit) async {
    try {
      final msg =
          await _repo.sendMessage(e.caseId, e.text, parentId: e.parentId);
      if (!_messages.any((m) => m.id == msg.id)) {
        _messages.add(msg);
      }
      emit(MessageLoaded(List.from(_messages)));
    } catch (err) {
      emit(MessageSendFailed(
        mapNetworkError(err),
        List.from(_messages),
        caseId: e.caseId,
        text: e.text,
        parentId: e.parentId,
      ));
      emit(MessageLoaded(List.from(_messages)));
    }
  }

  void _onReceived(MessageReceived e, Emitter<MessageState> emit) {
    if (_messages.any((m) => m.id == e.message.id)) {
      return;
    }
    _messages.add(e.message);
    emit(MessageLoaded(List.from(_messages)));
  }
}
