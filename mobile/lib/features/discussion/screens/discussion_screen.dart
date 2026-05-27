import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import 'package:timeago/timeago.dart' as timeago;
import '../../../core/constants/app_constants.dart';
import '../bloc/message_bloc.dart';
import '../repository/message_repository.dart';
import '../../../core/di/injection.dart';
import '../../../core/network/websocket_client.dart';

class DiscussionScreen extends StatefulWidget {
  final String caseId;
  const DiscussionScreen({super.key, required this.caseId});

  @override
  State<DiscussionScreen> createState() => _DiscussionScreenState();
}

class _DiscussionScreenState extends State<DiscussionScreen> {
  final _msgCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();
  final MessageBloc _messageBloc = getIt<MessageBloc>();
  final FlutterSecureStorage _storage = getIt<FlutterSecureStorage>();
  WebSocketClient? _wsClient;
  StompUnsubscribe? _unsubscribeMessages;

  @override
  void initState() {
    super.initState();
    _messageBloc.add(LoadMessages(widget.caseId));
    _connectRealtime();
  }

  @override
  void dispose() {
    _unsubscribeMessages?.call();
    _wsClient?.disconnect();
    _messageBloc.close();
    _msgCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  void _send(BuildContext context) {
    final text = _msgCtrl.text.trim();
    if (text.isEmpty) return;
    _messageBloc.add(SendMessage(widget.caseId, text));
    _msgCtrl.clear();
    _scrollToBottom();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(_scrollCtrl.position.maxScrollExtent,
            duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
      }
    });
  }

  Future<void> _connectRealtime() async {
    final token = await _storage.read(key: AppConstants.accessTokenKey);
    if (token == null || token.isEmpty) return;

    final ws = WebSocketClient(token);
    ws.connect(onConnected: () {
      _unsubscribeMessages =
          ws.subscribe('/topic/cases/${widget.caseId}/messages', (frame) {
        final body = frame.body;
        if (body == null || body.isEmpty) return;
        try {
          final data = jsonDecode(body) as Map<String, dynamic>;
          _messageBloc.add(MessageReceived(MessageModel.fromJson(data)));
          _scrollToBottom();
        } catch (_) {}
      });
    });
    _wsClient = ws;
  }

  @override
  Widget build(BuildContext context) {
    return BlocProvider.value(
      value: _messageBloc,
      child: Column(
        children: [
          Expanded(
            child: BlocConsumer<MessageBloc, MessageState>(
              listener: (context, state) {
                if (state is MessageSendFailed) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(state.msg),
                      action: SnackBarAction(
                        label: 'Retry',
                        onPressed: () => _messageBloc.add(
                          SendMessage(
                            state.caseId,
                            state.text,
                            parentId: state.parentId,
                          ),
                        ),
                      ),
                    ),
                  );
                }
              },
              builder: (context, state) {
                if (state is MessageLoading) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (state is MessageError) {
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
                                _messageBloc.add(LoadMessages(widget.caseId)),
                            icon: const Icon(Icons.refresh),
                            label: const Text('Retry'),
                          ),
                        ],
                      ),
                    ),
                  );
                }
                if (state is MessageLoaded) {
                  return ListView.builder(
                    controller: _scrollCtrl,
                    padding: const EdgeInsets.all(12),
                    itemCount: state.messages.length,
                    itemBuilder: (ctx, i) {
                      final msg = state.messages[i];
                      return _MessageBubble(
                        senderName: msg.senderName,
                        text: msg.messageText,
                        time: msg.createdAt,
                      );
                    },
                  );
                }
                if (state is MessageSendFailed) {
                  return ListView.builder(
                    controller: _scrollCtrl,
                    padding: const EdgeInsets.all(12),
                    itemCount: state.messages.length,
                    itemBuilder: (ctx, i) {
                      final msg = state.messages[i];
                      return _MessageBubble(
                        senderName: msg.senderName,
                        text: msg.messageText,
                        time: msg.createdAt,
                      );
                    },
                  );
                }
                return const SizedBox.shrink();
              },
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surface,
              border: Border(top: BorderSide(color: Colors.grey.shade300)),
            ),
            child: Builder(
                builder: (ctx) => Row(children: [
                      Expanded(
                        child: TextField(
                          controller: _msgCtrl,
                          decoration: const InputDecoration(
                              hintText: 'Type a message...',
                              border: InputBorder.none),
                          maxLines: null,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.send),
                        onPressed: () => _send(ctx),
                      ),
                    ])),
          ),
        ],
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final String senderName, text;
  final DateTime time;
  const _MessageBubble(
      {required this.senderName, required this.text, required this.time});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Text(senderName,
                style:
                    const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
            const SizedBox(width: 8),
            Text(timeago.format(time),
                style: TextStyle(color: Colors.grey.shade600, fontSize: 12)),
          ]),
          const SizedBox(height: 4),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(text),
          ),
        ],
      ),
    );
  }
}
