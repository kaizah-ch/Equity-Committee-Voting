class AuditLogModel {
  final String id;
  final String entityType;
  final String entityId;
  final String action;
  final String actorId;
  final String actorEmail;
  final Map<String, dynamic>? metadata;
  final DateTime createdAt;

  const AuditLogModel({
    required this.id,
    required this.entityType,
    required this.entityId,
    required this.action,
    required this.actorId,
    required this.actorEmail,
    required this.metadata,
    required this.createdAt,
  });

  factory AuditLogModel.fromJson(Map<String, dynamic> json) {
    return AuditLogModel(
      id: json['id'] as String,
      entityType: json['entityType'] as String,
      entityId: json['entityId'] as String,
      action: json['action'] as String,
      actorId: json['actorId'] as String,
      actorEmail: json['actorEmail'] as String,
      metadata: (json['metadata'] as Map?)?.cast<String, dynamic>(),
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  String get displayAction => switch (action) {
        'CREATED' => 'Case created',
        'UPDATED' => 'Case updated',
        'STATUS_CHANGED' => 'Status changed',
        'SENT' => 'Message sent',
        'UPLOADED' => 'Image uploaded',
        'DELETED' => 'Image deleted',
        'CAPTION_UPDATED' => 'Image caption updated',
        'VOTED' => 'Vote submitted',
        'VERDICT_UPDATED' => 'Verdict updated',
        _ => action
            .split('_')
            .map((part) => part.isEmpty
                ? part
                : '${part[0]}${part.substring(1).toLowerCase()}')
            .join(' '),
      };

  String get entityLabel => switch (entityType) {
        'CASE' => 'Case',
        'MESSAGE' => 'Message',
        'IMAGE' => 'Image',
        'VOTE' => 'Vote',
        _ => entityType,
      };
}
