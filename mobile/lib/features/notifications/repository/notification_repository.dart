import 'package:dio/dio.dart';

class NotificationModel {
  final String id;
  final String type, title, body;
  final String? caseId;
  final bool isRead;
  final DateTime createdAt;

  const NotificationModel({
    required this.id,
    required this.type,
    required this.title,
    required this.body,
    this.caseId,
    required this.isRead,
    required this.createdAt,
  });

  factory NotificationModel.fromJson(Map<String, dynamic> j) =>
      NotificationModel(
        id: j['id'] as String,
        type: j['type'] as String,
        title: j['title'] as String,
        body: j['body'] as String,
        caseId: (j['caseId'] as String?) ??
            (j['caseEntry'] is Map<String, dynamic>
                ? (j['caseEntry']['id'] as String?)
                : null),
        isRead: (j['read'] as bool?) ?? (j['isRead'] as bool? ?? false),
        createdAt: DateTime.parse(j['createdAt'] as String),
      );
}

class NotificationRepository {
  final Dio _dio;
  NotificationRepository(this._dio);

  Future<List<NotificationModel>> getNotifications({int page = 0}) async {
    final resp = await _dio
        .get('notifications', queryParameters: {'page': page, 'size': 20});
    return (resp.data['content'] as List)
        .map((e) => NotificationModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> markRead(String id) async {
    await _dio.patch('notifications/$id/read');
  }

  Future<void> markAllRead() async {
    await _dio.patch('notifications/read-all');
  }
}
