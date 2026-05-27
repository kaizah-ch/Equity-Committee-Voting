import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../core/network/network_error_mapper.dart';
import '../repository/vote_repository.dart';

abstract class VotingEvent {}

class LoadVotes extends VotingEvent {
  final String caseId;
  LoadVotes(this.caseId);
}

class CastVote extends VotingEvent {
  final String caseId;
  final VoteChoice choice;
  final String? reason;
  CastVote(this.caseId, this.choice, {this.reason});
}

abstract class VotingState {}

class VotingInitial extends VotingState {}

class VotingLoading extends VotingState {}

class VotingLoaded extends VotingState {
  final List<VoteModel> votes;
  final bool hasVoted;
  VotingLoaded(this.votes, {this.hasVoted = false});
}

class VotingError extends VotingState {
  final String message;
  VotingError(this.message);
}

class VoteCast extends VotingState {
  final VoteModel vote;
  VoteCast(this.vote);
}

class VoteCastFailed extends VotingState {
  final String message;
  final String caseId;
  final VoteChoice choice;
  final String? reason;
  VoteCastFailed(
    this.message, {
    required this.caseId,
    required this.choice,
    this.reason,
  });
}

class VotingBloc extends Bloc<VotingEvent, VotingState> {
  final VoteRepository _repo;
  List<VoteModel> _votes = [];

  VotingBloc(this._repo) : super(VotingInitial()) {
    on<LoadVotes>(_onLoad);
    on<CastVote>(_onCast);
  }

  Future<void> _onLoad(LoadVotes e, Emitter<VotingState> emit) async {
    emit(VotingLoading());
    try {
      final votes = await _repo.getVotes(e.caseId);
      _votes = votes;
      emit(VotingLoaded(_votes));
    } catch (err) {
      emit(VotingError(mapNetworkError(err)));
    }
  }

  Future<void> _onCast(CastVote e, Emitter<VotingState> emit) async {
    try {
      final vote = await _repo.castVote(e.caseId, e.choice, reason: e.reason);
      emit(VoteCast(vote));
      add(LoadVotes(e.caseId));
    } catch (err) {
      emit(VoteCastFailed(
        mapNetworkError(err),
        caseId: e.caseId,
        choice: e.choice,
        reason: e.reason,
      ));
      emit(VotingLoaded(_votes));
    }
  }
}
