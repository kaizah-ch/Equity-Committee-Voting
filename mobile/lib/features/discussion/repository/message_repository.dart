import 'package:dio/dio.dart';

class MessageModel {
  final String id;
  final String caseId;
  final String senderId;
  final String senderName;
  final String messageText;
  final String? parentMessageId;
  final DateTime createdAt;

  const MessageModel({
    required this.id,
    required this.caseId,
    required this.senderId,
    required this.senderName,
    required this.messageText,
    this.parentMessageId,
    required this.createdAt,
  });

  factory MessageModel.fromJson(Map<String, dynamic> json) => MessageModel(
        id: json['id'] as String,
        caseId: json['caseId'] as String,
        senderId: json['senderId'] as String,
        senderName: json['senderName'] as String,
        messageText: json['messageText'] as String,
        parentMessageId: json['parentMessageId'] as String?,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}

class MessageRepository {
  final Dio _dio;
  MessageRepository(this._dio);

  Future<List<MessageModel>> getMessages(String caseId, {int page = 0}) async {
    final response = await _dio.get('cases/$caseId/messages',
        queryParameters: {'page': page, 'size': 50});
    return (response.data['content'] as List)
        .map((e) => MessageModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<MessageModel> sendMessage(String caseId, String text,
      {String? parentId}) async {
    final response = await _dio.post('cases/$caseId/messages', data: {
      'messageText': text,
      if (parentId != null) 'parentMessageId': parentId,
    });
    return MessageModel.fromJson(response.data as Map<String, dynamic>);
  }
}
