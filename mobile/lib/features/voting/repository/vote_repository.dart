import 'package:dio/dio.dart';

enum VoteChoice { approve, reject, defer, abstain }

extension VoteChoiceDisplay on VoteChoice {
  String get apiName => switch (this) {
        VoteChoice.approve => 'APPROVE',
        VoteChoice.reject => 'REJECT',
        VoteChoice.defer => 'DEFER',
        VoteChoice.abstain => 'ABSTAIN',
      };

  String get displayName => switch (this) {
        VoteChoice.approve => 'Approve',
        VoteChoice.reject => 'Reject',
        VoteChoice.defer => 'Defer',
        VoteChoice.abstain => 'Abstain',
      };
}

class VoteModel {
  final String id;
  final String caseId;
  final String voterId;
  final String voterName;
  final VoteChoice voteChoice;
  final String? reason;
  final DateTime votedAt;

  const VoteModel({
    required this.id,
    required this.caseId,
    required this.voterId,
    required this.voterName,
    required this.voteChoice,
    this.reason,
    required this.votedAt,
  });

  factory VoteModel.fromJson(Map<String, dynamic> json) => VoteModel(
        id: json['id'] as String,
        caseId: json['caseId'] as String,
        voterId: json['voterId'] as String,
        voterName: json['voterName'] as String,
        voteChoice: VoteChoice.values
            .byName((json['voteChoice'] as String).toLowerCase()),
        reason: json['reason'] as String?,
        votedAt: DateTime.parse(json['votedAt'] as String),
      );
}

class VoteRepository {
  final Dio _dio;
  VoteRepository(this._dio);

  Future<List<VoteModel>> getVotes(String caseId) async {
    final response = await _dio.get('/api/cases/$caseId/vote');
    return (response.data as List)
        .map((e) => VoteModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<VoteModel> castVote(String caseId, VoteChoice choice,
      {String? reason}) async {
    final response = await _dio.post('/api/cases/$caseId/vote', data: {
      'voteChoice': choice.apiName,
      if (reason != null) 'reason': reason,
    });
    return VoteModel.fromJson(response.data as Map<String, dynamic>);
  }
}
