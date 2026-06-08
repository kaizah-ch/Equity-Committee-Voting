import 'package:dio/dio.dart';
import 'package:equity_committee_voting/features/voting/bloc/voting_bloc.dart';
import 'package:equity_committee_voting/features/voting/repository/vote_repository.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeVoteRepository extends VoteRepository {
  _FakeVoteRepository({
    List<VoteModel>? votes,
    this.castError,
  })  : _votes = votes ?? <VoteModel>[],
        super(Dio());

  final List<VoteModel> _votes;
  final Object? castError;

  @override
  Future<List<VoteModel>> getVotes(String caseId) async {
    return List<VoteModel>.from(_votes);
  }

  @override
  Future<VoteModel> castVote(String caseId, VoteChoice choice,
      {String? reason}) async {
    if (castError != null) {
      throw castError!;
    }
    return VoteModel(
      id: 'vote-1',
      caseId: caseId,
      voterId: 'user-1',
      voterName: 'Voter',
      voteChoice: choice,
      reason: reason,
      votedAt: DateTime.now(),
    );
  }
}

void main() {
  DioException votingError(String detail) {
    final requestOptions = RequestOptions(path: '/cases/case-1/vote');
    return DioException(
      requestOptions: requestOptions,
      response: Response<dynamic>(
        requestOptions: requestOptions,
        statusCode: 409,
        data: {'detail': detail},
      ),
      type: DioExceptionType.badResponse,
    );
  }

  Future<List<VotingState>> runCastErrorScenario(String backendDetail) async {
    final bloc =
        VotingBloc(_FakeVoteRepository(castError: votingError(backendDetail)));
    final emitted = <VotingState>[];
    final sub = bloc.stream.listen(emitted.add);

    bloc.add(CastVote('case-1', VoteChoice.approve, reason: 'reason'));
    await Future<void>.delayed(const Duration(milliseconds: 20));

    await sub.cancel();
    await bloc.close();
    return emitted;
  }

  test('maps already-voted backend error to friendly message', () async {
    final emitted =
        await runCastErrorScenario('You have already voted on this case');
    final errors = emitted.whereType<VoteCastFailed>().toList();

    expect(errors.length, 1);
    expect(
      errors.single.message,
      'You have already submitted your vote for this case.',
    );
  });

  test('maps deadline-passed backend error to friendly message', () async {
    final emitted =
        await runCastErrorScenario('Voting deadline has passed for this case');
    final errors = emitted.whereType<VoteCastFailed>().toList();

    expect(errors.length, 1);
    expect(
      errors.single.message,
      'Voting deadline has already passed for this case.',
    );
  });

  test('maps forbidden-vote backend error to friendly message', () async {
    final emitted = await runCastErrorScenario(
        'Only committee members and chairperson can vote');
    final errors = emitted.whereType<VoteCastFailed>().toList();

    expect(errors.length, 1);
    expect(errors.single.message, 'You are not allowed to vote on this case.');
  });
}
