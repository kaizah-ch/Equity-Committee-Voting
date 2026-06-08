import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:intl/intl.dart';

import '../../../core/constants/app_constants.dart';
import '../../../core/di/injection.dart';
import '../../../core/network/network_error_mapper.dart';
import '../models/case_model.dart';
import '../repository/case_repository.dart';
import 'audit_logs_screen.dart';
import 'create_case_screen.dart';
import '../../discussion/screens/discussion_screen.dart';
import '../../voting/screens/voting_screen.dart';
import '../../images/screens/images_screen.dart';

class CaseDetailScreen extends StatefulWidget {
  final String caseId;
  const CaseDetailScreen({super.key, required this.caseId});

  @override
  State<CaseDetailScreen> createState() => _CaseDetailScreenState();
}

class _CaseDetailScreenState extends State<CaseDetailScreen> {
  final CaseRepository _caseRepository = getIt<CaseRepository>();
  final FlutterSecureStorage _storage = getIt<FlutterSecureStorage>();
  CaseModel? _caseModel;
  bool _loading = true;
  bool _updatingStatus = false;
  String? _error;
  String? _currentUserId;
  String? _currentUserRole;

  @override
  void initState() {
    super.initState();
    _loadCurrentUserContext();
    _loadCase();
  }

  Future<void> _loadCurrentUserContext() async {
    final userId = await _storage.read(key: AppConstants.userIdKey);
    final role = await _storage.read(key: AppConstants.userRoleKey);
    if (!mounted) return;
    setState(() {
      _currentUserId = userId;
      _currentUserRole = role;
    });
  }

  bool get _canModifyCase {
    final data = _caseModel;
    if (data == null) return false;
    final role = _currentUserRole;
    final isPrivileged = role == 'ADMIN' || role == 'CHAIRPERSON';
    final isCreator =
        _currentUserId != null && _currentUserId == data.createdById;
    return isPrivileged || isCreator;
  }

  Future<void> _loadCase() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await _caseRepository.getCase(widget.caseId);
      if (!mounted) return;
      setState(() => _caseModel = data);
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = 'Failed to load case details');
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _changeStatus(CaseStatus status) async {
    final current = _caseModel;
    if (current == null || _updatingStatus) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('Move to ${status.displayName}?'),
        content: Text(
          'This will change ${current.referenceNumber} from ${current.status.displayName} to ${status.displayName}.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _updatingStatus = true);
    try {
      final updated =
          await _caseRepository.updateStatus(widget.caseId, status.apiName);
      if (!mounted) return;
      setState(() => _caseModel = updated);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Case moved to ${status.displayName}')),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(mapNetworkError(error))),
      );
    } finally {
      if (mounted) {
        setState(() => _updatingStatus = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final keyboardVisible = MediaQuery.viewInsetsOf(context).bottom > 0;

    return DefaultTabController(
      length: 4,
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            _caseModel == null
                ? 'Case #${widget.caseId.substring(0, 8)}'
                : _caseModel!.referenceNumber,
          ),
          actions: [
            if (_canModifyCase && _caseModel?.status.canEdit == true)
              IconButton(
                tooltip: 'Edit case',
                icon: const Icon(Icons.edit_outlined),
                onPressed: () async {
                  final updated = await Navigator.of(context).push<CaseModel>(
                    MaterialPageRoute(
                      builder: (_) => CreateCaseScreen(initialCase: _caseModel),
                    ),
                  );
                  if (!mounted) return;
                  if (updated != null) {
                    setState(() => _caseModel = updated);
                  } else {
                    _loadCase();
                  }
                },
              ),
          ],
          bottom: const TabBar(
            isScrollable: true,
            tabAlignment: TabAlignment.start,
            indicatorColor: Colors.white,
            labelColor: Colors.white,
            unselectedLabelColor: Colors.white70,
            tabs: [
              Tab(icon: Icon(Icons.chat_bubble_outline), text: 'Discussion'),
              Tab(icon: Icon(Icons.how_to_vote_outlined), text: 'Vote'),
              Tab(icon: Icon(Icons.photo_library_outlined), text: 'Images'),
              Tab(icon: Icon(Icons.history), text: 'Audit'),
            ],
          ),
        ),
        body: Column(
          children: [
            if (!keyboardVisible)
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
                child: _CaseOverviewCard(
                  caseModel: _caseModel,
                  loading: _loading,
                  updatingStatus: _updatingStatus,
                  error: _error,
                  canModify: _canModifyCase,
                  onRetry: _loadCase,
                  onChangeStatus: _changeStatus,
                ),
              ),
            Expanded(
              child: TabBarView(children: [
                DiscussionScreen(caseId: widget.caseId),
                VotingScreen(caseId: widget.caseId),
                ImagesScreen(caseId: widget.caseId, caseModel: _caseModel),
                AuditLogsScreen(caseId: widget.caseId),
              ]),
            ),
          ],
        ),
      ),
    );
  }
}

class _CaseOverviewCard extends StatelessWidget {
  final CaseModel? caseModel;
  final bool loading;
  final bool updatingStatus;
  final String? error;
  final bool canModify;
  final Future<void> Function() onRetry;
  final ValueChanged<CaseStatus> onChangeStatus;

  const _CaseOverviewCard({
    required this.caseModel,
    required this.loading,
    required this.updatingStatus,
    required this.error,
    required this.canModify,
    required this.onRetry,
    required this.onChangeStatus,
  });

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Center(child: CircularProgressIndicator()),
        ),
      );
    }

    if (error != null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              const Expanded(child: Text('Failed to load case overview')),
              TextButton(onPressed: onRetry, child: const Text('Retry')),
            ],
          ),
        ),
      );
    }

    final data = caseModel;
    if (data == null) return const SizedBox.shrink();

    final amount = NumberFormat.currency(symbol: 'ZMW ', decimalDigits: 2)
        .format(data.requestedAmount);
    final createdAt =
        DateFormat('dd MMM yyyy, HH:mm').format(data.createdAt.toLocal());
    final deadline = data.votingDeadline == null
        ? 'Not set'
        : DateFormat('dd MMM yyyy, HH:mm')
            .format(data.votingDeadline!.toLocal());

    final transitions =
        canModify ? data.status.nextStatuses : const <CaseStatus>[];

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    data.clientName,
                    style: Theme.of(context)
                        .textTheme
                        .titleMedium
                        ?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
                if (updatingStatus) ...[
                  const SizedBox(width: 8),
                  const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                ] else if (transitions.isNotEmpty) ...[
                  const SizedBox(width: 8),
                  PopupMenuButton<CaseStatus>(
                    tooltip: 'Change status',
                    icon: const Icon(Icons.more_vert),
                    onSelected: onChangeStatus,
                    itemBuilder: (context) => transitions
                        .map(
                          (status) => PopupMenuItem(
                            value: status,
                            child: Row(
                              children: [
                                Icon(_statusActionIcon(status), size: 19),
                                const SizedBox(width: 10),
                                Text(status.actionLabel),
                              ],
                            ),
                          ),
                        )
                        .toList(),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 10,
              runSpacing: 6,
              children: [
                _infoChip('Ref', data.referenceNumber),
                _infoChip('Status', data.status.displayName),
                _infoChip('Verdict', data.verdict ?? 'PENDING'),
                _infoChip('Amount', amount),
                _infoChip('Product', data.productType),
                _infoChip('Tenure', data.tenure ?? 'N/A'),
                _infoChip('Voting deadline', deadline),
                _infoChip('Created by', data.createdByName),
                _infoChip('Created', createdAt),
              ],
            ),
            if ((data.summary ?? '').trim().isNotEmpty) ...[
              const SizedBox(height: 10),
              Text('Summary: ${data.summary}'),
            ],
            if ((data.riskNotes ?? '').trim().isNotEmpty) ...[
              const SizedBox(height: 6),
              Text('Risk notes: ${data.riskNotes}'),
            ],
            if ((data.collateralSummary ?? '').trim().isNotEmpty) ...[
              const SizedBox(height: 6),
              Text('Collateral: ${data.collateralSummary}'),
            ],
          ],
        ),
      ),
    );
  }

  Widget _infoChip(String label, String value) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text('$label: $value'),
    );
  }

  IconData _statusActionIcon(CaseStatus status) => switch (status) {
        CaseStatus.draft => Icons.undo,
        CaseStatus.submitted => Icons.send_outlined,
        CaseStatus.underReview => Icons.rate_review_outlined,
        CaseStatus.votingOpen => Icons.how_to_vote_outlined,
        CaseStatus.approved => Icons.check_circle_outline,
        CaseStatus.rejected => Icons.cancel_outlined,
        CaseStatus.deferred => Icons.schedule,
        CaseStatus.closed => Icons.lock_outline,
      };
}
