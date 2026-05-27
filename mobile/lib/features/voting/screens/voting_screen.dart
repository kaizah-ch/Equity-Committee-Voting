import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../../cases/models/case_model.dart';
import '../../cases/repository/case_repository.dart';
import '../../../core/constants/app_constants.dart';
import '../bloc/voting_bloc.dart';
import '../repository/vote_repository.dart';
import '../../../core/di/injection.dart';
import '../../../core/network/websocket_client.dart';

class VotingScreen extends StatelessWidget {
  final String caseId;
  const VotingScreen({super.key, required this.caseId});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (_) => getIt<VotingBloc>()..add(LoadVotes(caseId)),
      child: _VotingView(caseId: caseId),
    );
  }
}

class _VotingView extends StatefulWidget {
  final String caseId;
  const _VotingView({required this.caseId});

  @override
  State<_VotingView> createState() => _VotingViewState();
}

class _VotingViewState extends State<_VotingView> {
  final CaseRepository _caseRepository = getIt<CaseRepository>();
  final FlutterSecureStorage _storage = getIt<FlutterSecureStorage>();
  CaseModel? _caseModel;
  bool _loadingCase = true;
  bool _wsInitialized = false;
  late VotingBloc _votingBloc;
  WebSocketClient? _wsClient;
  StompUnsubscribe? _unsubscribeVotes;
  StompUnsubscribe? _unsubscribeVerdict;
  String? _currentUserId;
  String? _currentUserRole;
  bool _loadingUserContext = true;

  @override
  void initState() {
    super.initState();
    _loadCurrentUser();
    _loadCase();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_wsInitialized) return;
    _votingBloc = context.read<VotingBloc>();
    _connectRealtime();
    _wsInitialized = true;
  }

  @override
  void dispose() {
    _unsubscribeVotes?.call();
    _unsubscribeVerdict?.call();
    _wsClient?.disconnect();
    super.dispose();
  }

  Future<void> _loadCase() async {
    try {
      final caseModel = await _caseRepository.getCase(widget.caseId);
      if (!mounted) return;
      setState(() {
        _caseModel = caseModel;
        _loadingCase = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loadingCase = false);
    }
  }

  Future<void> _loadCurrentUser() async {
    final userId = await _storage.read(key: AppConstants.userIdKey);
    final role = await _storage.read(key: AppConstants.userRoleKey);
    if (!mounted) return;
    setState(() {
      _currentUserId = userId;
      _currentUserRole = role;
      _loadingUserContext = false;
    });
  }

  bool get _canCastVote =>
      _currentUserRole == 'COMMITTEE_MEMBER' ||
      _currentUserRole == 'CHAIRPERSON';

  Future<void> _connectRealtime() async {
    final token = await _storage.read(key: AppConstants.accessTokenKey);
    if (!mounted || token == null || token.isEmpty) return;

    final ws = WebSocketClient(token);
    ws.connect(onConnected: () {
      _unsubscribeVotes =
          ws.subscribe('/topic/cases/${widget.caseId}/votes', (_) {
        _votingBloc.add(LoadVotes(widget.caseId));
      });
      _unsubscribeVerdict =
          ws.subscribe('/topic/cases/${widget.caseId}/verdict', (_) {
        _votingBloc.add(LoadVotes(widget.caseId));
        _loadCase();
      });
    });
    _wsClient = ws;
  }

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<VotingBloc, VotingState>(
      listener: (context, state) {
        if (state is VoteCast) {
          ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Vote cast successfully')));
          _loadCase();
        }
        if (state is VotingError) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text(state.message)));
        }
        if (state is VoteCastFailed) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(state.message),
              action: SnackBarAction(
                label: 'Retry',
                onPressed: () => context.read<VotingBloc>().add(
                      CastVote(
                        state.caseId,
                        state.choice,
                        reason: state.reason,
                      ),
                    ),
              ),
            ),
          );
        }
      },
      builder: (context, state) {
        if (state is VotingLoading) {
          return const Center(child: CircularProgressIndicator());
        }
        if (state is VotingError) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(state.message, textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: () => context
                        .read<VotingBloc>()
                        .add(LoadVotes(widget.caseId)),
                    icon: const Icon(Icons.refresh),
                    label: const Text('Retry'),
                  ),
                ],
              ),
            ),
          );
        }
        if (state is VotingLoaded) {
          if (_loadingUserContext) {
            return const Center(child: CircularProgressIndicator());
          }
          final votingOpen = _caseModel?.status == CaseStatus.votingOpen;
          final hasCurrentUserVoted = _currentUserId != null &&
              state.votes.any((vote) => vote.voterId == _currentUserId);
          return Column(
            children: [
              Expanded(
                  child: _VoteSummary(
                      votes: state.votes,
                      caseModel: _caseModel,
                      loadingCase: _loadingCase)),
              if (votingOpen && !_canCastVote)
                const _VotingRestrictedPanel()
              else if (votingOpen && !hasCurrentUserVoted)
                _VoteActions(caseId: widget.caseId)
              else if (votingOpen)
                const _VoteRecordedPanel()
              else
                _VotingClosedPanel(caseModel: _caseModel),
            ],
          );
        }
        return const SizedBox.shrink();
      },
    );
  }
}

class _VoteSummary extends StatelessWidget {
  final List<VoteModel> votes;
  final CaseModel? caseModel;
  final bool loadingCase;
  const _VoteSummary(
      {required this.votes,
      required this.caseModel,
      required this.loadingCase});

  @override
  Widget build(BuildContext context) {
    if (loadingCase) {
      return const Center(child: CircularProgressIndicator());
    }

    final verdict = caseModel?.verdict;
    final isFinalized =
        caseModel != null && caseModel!.status != CaseStatus.votingOpen;
    final tally = <VoteChoice, int>{};
    for (final v in votes) {
      tally[v.voteChoice] = (tally[v.voteChoice] ?? 0) + 1;
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (isFinalized)
          Container(
            margin: const EdgeInsets.only(bottom: 12),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primaryContainer,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(
              'Final verdict: ${_displayVerdict(verdict) ?? caseModel!.status.displayName}',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
        Text('Tally', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        if (votes.isEmpty)
          const Text('No votes yet')
        else
          ...tally.entries.map((e) {
            return Row(children: [
              Chip(label: Text(e.key.displayName)),
              const SizedBox(width: 8),
              Text('${e.value} vote(s)'),
            ]);
          }),
        const Divider(),
        Text('All Votes', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        if (votes.isEmpty)
          const Text('No vote records yet')
        else
          ...votes.map((v) => ListTile(
                title: Text(v.voterName),
                trailing: Chip(label: Text(v.voteChoice.displayName)),
                subtitle: v.reason != null ? Text(v.reason!) : null,
              )),
      ],
    );
  }

  String? _displayVerdict(String? verdict) {
    if (verdict == null || verdict.isEmpty) return null;
    return verdict
        .toLowerCase()
        .split('_')
        .map((part) =>
            part.isEmpty ? part : part[0].toUpperCase() + part.substring(1))
        .join(' ');
  }
}

class _VotingRestrictedPanel extends StatelessWidget {
  const _VotingRestrictedPanel();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        border: Border(top: BorderSide(color: Colors.grey.shade300)),
      ),
      child: Text(
        'Only committee members and chairperson can vote.',
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.bodyMedium,
      ),
    );
  }
}

class _VoteRecordedPanel extends StatelessWidget {
  const _VoteRecordedPanel();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        border: Border(top: BorderSide(color: Colors.grey.shade300)),
      ),
      child: Text(
        'Your vote has been recorded.',
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.bodyMedium,
      ),
    );
  }
}

class _VotingClosedPanel extends StatelessWidget {
  final CaseModel? caseModel;
  const _VotingClosedPanel({required this.caseModel});

  @override
  Widget build(BuildContext context) {
    final statusLabel =
        caseModel == null ? 'Voting closed' : caseModel!.status.displayName;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        border: Border(top: BorderSide(color: Colors.grey.shade300)),
      ),
      child: Text(
        'Voting is closed ($statusLabel).',
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.bodyMedium,
      ),
    );
  }
}

class _VoteActions extends StatefulWidget {
  final String caseId;
  const _VoteActions({required this.caseId});

  @override
  State<_VoteActions> createState() => _VoteActionsState();
}

class _VoteActionsState extends State<_VoteActions> {
  final _reasonCtrl = TextEditingController();

  void _cast(VoteChoice choice) {
    context.read<VotingBloc>().add(
          CastVote(widget.caseId, choice,
              reason: _reasonCtrl.text.trim().isEmpty
                  ? null
                  : _reasonCtrl.text.trim()),
        );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        border: Border(top: BorderSide(color: Colors.grey.shade300)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: _reasonCtrl,
            decoration: const InputDecoration(
                labelText: 'Reason (optional)', border: OutlineInputBorder()),
            maxLines: 2,
          ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
                child: ElevatedButton.icon(
              style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
              onPressed: () => _cast(VoteChoice.approve),
              icon: const Icon(Icons.check),
              label: const Text('Approve'),
            )),
            const SizedBox(width: 8),
            Expanded(
                child: ElevatedButton.icon(
              style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
              onPressed: () => _cast(VoteChoice.reject),
              icon: const Icon(Icons.close),
              label: const Text('Reject'),
            )),
            const SizedBox(width: 8),
            Expanded(
                child: ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.amber.shade700),
              onPressed: () => _cast(VoteChoice.defer),
              icon: const Icon(Icons.pause),
              label: const Text('Defer'),
            )),
          ]),
        ],
      ),
    );
  }
}
