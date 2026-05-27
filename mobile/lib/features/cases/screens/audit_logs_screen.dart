import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:timeago/timeago.dart' as timeago;

import '../../../core/di/injection.dart';
import '../models/audit_log_model.dart';
import '../repository/case_repository.dart';

class AuditLogsScreen extends StatefulWidget {
  final String caseId;
  const AuditLogsScreen({super.key, required this.caseId});

  @override
  State<AuditLogsScreen> createState() => _AuditLogsScreenState();
}

class _AuditLogsScreenState extends State<AuditLogsScreen> {
  final CaseRepository _repository = getIt<CaseRepository>();
  final List<AuditLogModel> _logs = [];
  int _page = 0;
  bool _hasMore = true;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadInitial();
  }

  Future<void> _loadInitial() async {
    setState(() {
      _loading = true;
      _error = null;
      _page = 0;
      _hasMore = true;
      _logs.clear();
    });
    try {
      final result = await _repository.getCaseAuditLogs(widget.caseId, page: 0);
      if (!mounted) return;
      setState(() {
        _logs.addAll(result.logs);
        _hasMore = result.hasMore;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'Failed to load audit logs');
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _loadMore() async {
    if (_loadingMore || !_hasMore) return;
    setState(() => _loadingMore = true);
    final nextPage = _page + 1;
    try {
      final result = await _repository.getCaseAuditLogs(widget.caseId, page: nextPage);
      if (!mounted) return;
      setState(() {
        _page = nextPage;
        _logs.addAll(result.logs);
        _hasMore = result.hasMore;
      });
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not load more audit activity')),
      );
    } finally {
      if (mounted) {
        setState(() => _loadingMore = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(_error!, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _loadInitial,
                icon: const Icon(Icons.refresh),
                label: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }
    if (_logs.isEmpty) {
      return RefreshIndicator(
        onRefresh: _loadInitial,
        child: ListView(
          children: [
            SizedBox(height: MediaQuery.sizeOf(context).height * 0.2),
            const _EmptyAuditLogs(),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadInitial,
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 20),
        itemCount: _logs.length + (_hasMore ? 1 : 0),
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          if (index == _logs.length) {
            _loadMore();
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          return _AuditLogTile(
            log: _logs[index],
            isFirst: index == 0,
            isLast: index == _logs.length - 1 && !_hasMore,
          );
        },
      ),
    );
  }
}

class _AuditLogTile extends StatelessWidget {
  final AuditLogModel log;
  final bool isFirst;
  final bool isLast;

  const _AuditLogTile({
    required this.log,
    required this.isFirst,
    required this.isLast,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final exactTime = DateFormat('dd MMM yyyy, HH:mm').format(log.createdAt.toLocal());
    final metadata = _visibleMetadata(log.metadata);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Column(
          children: [
            Container(
              width: 2,
              height: isFirst ? 8 : 16,
              color: isFirst
                  ? Colors.transparent
                  : colorScheme.outlineVariant,
            ),
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: _actionColor(context, log.action).withValues(alpha: 0.14),
                shape: BoxShape.circle,
              ),
              child: Icon(
                _actionIcon(log.action),
                color: _actionColor(context, log.action),
                size: 19,
              ),
            ),
            Container(
              width: 2,
              height: isLast ? 8 : 48,
              color: isLast
                  ? Colors.transparent
                  : colorScheme.outlineVariant,
            ),
          ],
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Card(
            margin: EdgeInsets.zero,
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
                          log.displayAction,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        log.entityLabel,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: colorScheme.primary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    log.actorEmail,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: colorScheme.onSurface.withValues(alpha: 0.82),
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    '${timeago.format(log.createdAt)} • $exactTime',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: colorScheme.onSurfaceVariant,
                    ),
                  ),
                  if (metadata.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: metadata.entries
                          .map((entry) => _MetadataChip(
                                label: entry.key,
                                value: entry.value,
                              ))
                          .toList(),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Map<String, String> _visibleMetadata(Map<String, dynamic>? metadata) {
    if (metadata == null || metadata.isEmpty) {
      return const {};
    }
    return Map.fromEntries(
      metadata.entries.take(4).map(
            (entry) => MapEntry(entry.key, '${entry.value}'),
          ),
    );
  }

  IconData _actionIcon(String action) => switch (action) {
        'CREATED' => Icons.note_add_outlined,
        'UPDATED' => Icons.edit_note,
        'STATUS_CHANGED' => Icons.sync_alt,
        'SENT' => Icons.chat_bubble_outline,
        'UPLOADED' => Icons.photo_library_outlined,
        'DELETED' => Icons.delete_outline,
        'CAPTION_UPDATED' => Icons.closed_caption_outlined,
        'VOTED' => Icons.how_to_vote_outlined,
        'VERDICT_UPDATED' => Icons.gavel_outlined,
        _ => Icons.history,
      };

  Color _actionColor(BuildContext context, String action) {
    final colorScheme = Theme.of(context).colorScheme;
    return switch (action) {
      'CREATED' => Colors.green.shade700,
      'UPDATED' => Colors.blue.shade700,
      'STATUS_CHANGED' => Colors.purple.shade700,
      'SENT' => Colors.teal.shade700,
      'UPLOADED' => Colors.indigo.shade700,
      'DELETED' => Colors.red.shade700,
      'VOTED' => Colors.orange.shade800,
      'VERDICT_UPDATED' => Colors.green.shade800,
      _ => colorScheme.primary,
    };
  }
}

class _MetadataChip extends StatelessWidget {
  final String label;
  final String value;

  const _MetadataChip({
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.75),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        '$label: $value',
        style: Theme.of(context).textTheme.bodySmall,
      ),
    );
  }
}

class _EmptyAuditLogs extends StatelessWidget {
  const _EmptyAuditLogs();

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          children: [
            Icon(
              Icons.history,
              size: 52,
              color: colorScheme.onSurfaceVariant,
            ),
            const SizedBox(height: 12),
            Text(
              'No audit activity yet',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            Text(
              'Case edits, messages, image changes, votes, and verdict updates will appear here.',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}
