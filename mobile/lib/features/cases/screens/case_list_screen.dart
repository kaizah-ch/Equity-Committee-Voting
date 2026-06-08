import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../bloc/case_bloc.dart';
import '../models/case_model.dart';
import '../../auth/bloc/auth_bloc.dart';
import '../../notifications/bloc/notification_bloc.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/di/injection.dart';
import '../../../core/network/websocket_client.dart';

class CaseListScreen extends StatelessWidget {
  const CaseListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (_) => getIt<CaseBloc>()..add(LoadCases()),
      child: const _CaseListView(),
    );
  }
}

class _CaseListView extends StatefulWidget {
  const _CaseListView();

  @override
  State<_CaseListView> createState() => _CaseListViewState();
}

class _CaseListViewState extends State<_CaseListView> {
  final FlutterSecureStorage _storage = getIt<FlutterSecureStorage>();
  final ScrollController _scrollController = ScrollController();
  final TextEditingController _searchController = TextEditingController();
  WebSocketClient? _wsClient;
  StompUnsubscribe? _unsubscribeNotifications;
  StompUnsubscribe? _unsubscribeCases;
  CaseStatus? _selectedStatus;
  String _searchQuery = '';
  String? _currentUserRole;
  Timer? _searchDebounce;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_loadMoreIfNeeded);
    _loadCurrentUserRole();
    context.read<NotificationBloc>().add(LoadNotifications());
    _connectRealtime();
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _searchController.dispose();
    _scrollController.dispose();
    _unsubscribeNotifications?.call();
    _unsubscribeCases?.call();
    _wsClient?.disconnect();
    super.dispose();
  }

  void _loadCases({bool showLoading = true}) {
    context
        .read<CaseBloc>()
        .add(LoadCases(status: _selectedStatus, showLoading: showLoading));
  }

  Future<void> _loadCurrentUserRole() async {
    final role = await _storage.read(key: AppConstants.userRoleKey);
    if (!mounted) return;
    setState(() => _currentUserRole = role);
  }

  void _loadMoreIfNeeded() {
    if (!_scrollController.hasClients) return;
    final position = _scrollController.position;
    if (position.pixels < position.maxScrollExtent - 320) return;

    final state = context.read<CaseBloc>().state;
    if (state is CaseLoaded && state.hasMore && !state.isLoadingMore) {
      context.read<CaseBloc>().add(LoadMoreCases());
    }
  }

  void _setStatus(CaseStatus? status) {
    if (_selectedStatus == status) return;
    setState(() => _selectedStatus = status);
    _loadCases();
  }

  void _onSearchChanged(String value) {
    _searchDebounce?.cancel();
    _searchDebounce = Timer(const Duration(milliseconds: 250), () {
      if (!mounted) return;
      setState(() => _searchQuery = value.trim().toLowerCase());
    });
  }

  List<CaseModel> _visibleCases(List<CaseModel> cases) {
    if (_searchQuery.isEmpty) return cases;
    return cases.where(_matchesSearch).toList();
  }

  bool _matchesSearch(CaseModel caseModel) {
    final searchable = [
      caseModel.referenceNumber,
      caseModel.clientName,
      caseModel.productType,
      caseModel.createdByName,
      caseModel.status.displayName,
      caseModel.requestedAmount.toString(),
    ].join(' ').toLowerCase();
    return searchable.contains(_searchQuery);
  }

  void _clearSearch() {
    _searchDebounce?.cancel();
    _searchController.clear();
    setState(() => _searchQuery = '');
  }

  Future<void> _confirmLogout() async {
    final shouldLogout = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Sign out?'),
        content: const Text('You will need to sign in again to continue.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Sign out'),
          ),
        ],
      ),
    );

    if (shouldLogout == true && mounted) {
      context.read<AuthBloc>().add(LogoutRequested());
    }
  }

  Future<void> _connectRealtime() async {
    final token = await _storage.read(key: AppConstants.accessTokenKey);
    if (!mounted || token == null || token.isEmpty) return;

    final ws = WebSocketClient(token);
    ws.connect(onConnected: () {
      _unsubscribeNotifications =
          ws.subscribe('/user/queue/notifications', (_) {
        if (!mounted) return;
        context
            .read<NotificationBloc>()
            .add(LoadNotifications(showLoading: false));
      });
      _unsubscribeCases = ws.subscribe('/topic/cases', (_) {
        if (!mounted) return;
        _loadCases(showLoading: false);
      });
    });
    _wsClient = ws;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cases'),
        actions: [
          if (_currentUserRole == 'ADMIN')
            IconButton(
              tooltip: 'Manage users',
              icon: const Icon(Icons.manage_accounts_outlined),
              onPressed: () => context.go('/admin/users'),
            ),
          BlocBuilder<NotificationBloc, NotificationState>(
            builder: (context, state) {
              final unread =
                  state is NotificationLoaded ? state.unreadCount : 0;
              return Stack(
                alignment: Alignment.center,
                children: [
                  IconButton(
                    icon: const Icon(Icons.notifications_outlined),
                    onPressed: () => context.go('/notifications'),
                  ),
                  if (unread > 0)
                    Positioned(
                      right: 10,
                      top: 10,
                      child: Container(
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          color: Colors.red,
                          shape: BoxShape.circle,
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
          IconButton(
            tooltip: 'Sign out',
            icon: const Icon(Icons.logout),
            onPressed: _confirmLogout,
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.go('/cases/new'),
        icon: const Icon(Icons.add),
        label: const Text('New Case'),
      ),
      body: BlocBuilder<CaseBloc, CaseState>(
        builder: (context, state) {
          if (state is CaseLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          if (state is CaseError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(state.message, textAlign: TextAlign.center),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: _loadCases,
                      icon: const Icon(Icons.refresh),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            );
          }
          if (state is CaseLoaded) {
            final visibleCases = _visibleCases(state.cases);
            return RefreshIndicator(
              onRefresh: () async => _loadCases(),
              child: ListView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 88),
                itemCount: visibleCases.length + 2,
                itemBuilder: (ctx, i) {
                  if (i == 0) {
                    return _CaseListFilters(
                      selectedStatus: _selectedStatus,
                      searchController: _searchController,
                      onSearchChanged: _onSearchChanged,
                      onClearSearch: _clearSearch,
                      onStatusChanged: _setStatus,
                    );
                  }
                  final caseIndex = i - 1;
                  if (caseIndex == visibleCases.length) {
                    return _CaseListFooter(
                      allCasesEmpty: state.cases.isEmpty,
                      visibleCasesEmpty: visibleCases.isEmpty,
                      hasSearch: _searchQuery.isNotEmpty,
                      hasStatusFilter: _selectedStatus != null,
                      hasMore: state.hasMore,
                      isLoadingMore: state.isLoadingMore,
                      onLoadMore: () =>
                          context.read<CaseBloc>().add(LoadMoreCases()),
                      onClearFilters: () {
                        _clearSearch();
                        _setStatus(null);
                      },
                    );
                  }
                  return _CaseCard(caseModel: visibleCases[caseIndex]);
                },
              ),
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }
}

class _CaseListFilters extends StatelessWidget {
  final CaseStatus? selectedStatus;
  final TextEditingController searchController;
  final ValueChanged<String> onSearchChanged;
  final VoidCallback onClearSearch;
  final ValueChanged<CaseStatus?> onStatusChanged;

  const _CaseListFilters({
    required this.selectedStatus,
    required this.searchController,
    required this.onSearchChanged,
    required this.onClearSearch,
    required this.onStatusChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            controller: searchController,
            onChanged: onSearchChanged,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              hintText: 'Search cases',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: searchController.text.isEmpty
                  ? null
                  : IconButton(
                      tooltip: 'Clear search',
                      icon: const Icon(Icons.close),
                      onPressed: onClearSearch,
                    ),
              border: const OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 10),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: const Text('All'),
                    selected: selectedStatus == null,
                    onSelected: (_) => onStatusChanged(null),
                  ),
                ),
                ...CaseStatus.values.map(
                  (status) => Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(status.displayName),
                      selected: selectedStatus == status,
                      onSelected: (_) => onStatusChanged(status),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CaseListFooter extends StatelessWidget {
  final bool allCasesEmpty;
  final bool visibleCasesEmpty;
  final bool hasSearch;
  final bool hasStatusFilter;
  final bool hasMore;
  final bool isLoadingMore;
  final VoidCallback onLoadMore;
  final VoidCallback onClearFilters;

  const _CaseListFooter({
    required this.allCasesEmpty,
    required this.visibleCasesEmpty,
    required this.hasSearch,
    required this.hasStatusFilter,
    required this.hasMore,
    required this.isLoadingMore,
    required this.onLoadMore,
    required this.onClearFilters,
  });

  @override
  Widget build(BuildContext context) {
    if (isLoadingMore) {
      return const Padding(
        padding: EdgeInsets.all(18),
        child: Center(child: CircularProgressIndicator()),
      );
    }

    if (allCasesEmpty || visibleCasesEmpty) {
      final hasFilters = hasSearch || hasStatusFilter;
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 48),
        child: Center(
          child: Column(
            children: [
              Icon(
                Icons.folder_open_outlined,
                size: 44,
                color: Theme.of(context).colorScheme.outline,
              ),
              const SizedBox(height: 10),
              Text(hasFilters ? 'No matching cases' : 'No cases found'),
              if (hasFilters) ...[
                const SizedBox(height: 10),
                OutlinedButton.icon(
                  onPressed: onClearFilters,
                  icon: const Icon(Icons.filter_alt_off_outlined),
                  label: const Text('Clear filters'),
                ),
              ],
              if (hasSearch && hasMore) ...[
                const SizedBox(height: 8),
                TextButton(
                  onPressed: onLoadMore,
                  child: const Text('Load more cases'),
                ),
              ],
            ],
          ),
        ),
      );
    }

    if (!hasMore) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: SizedBox.shrink(),
      );
    }

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Center(
        child: OutlinedButton.icon(
          onPressed: onLoadMore,
          icon: const Icon(Icons.expand_more),
          label: const Text('Load more'),
        ),
      ),
    );
  }
}

class _CaseCard extends StatelessWidget {
  final CaseModel caseModel;
  const _CaseCard({required this.caseModel});

  Color _statusColor(CaseStatus status) => switch (status) {
        CaseStatus.draft => Colors.grey,
        CaseStatus.submitted => Colors.blue,
        CaseStatus.underReview => Colors.orange,
        CaseStatus.votingOpen => Colors.purple,
        CaseStatus.approved => Colors.green,
        CaseStatus.rejected => Colors.red,
        CaseStatus.deferred => Colors.amber,
        CaseStatus.closed => Colors.grey.shade700,
      };

  @override
  Widget build(BuildContext context) {
    final fmt = NumberFormat.currency(symbol: 'ZMW ', decimalDigits: 2);
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: () => context.go('/cases/${caseModel.id}'),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                Expanded(
                  child: Text(caseModel.referenceNumber,
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                ),
                Chip(
                  label: Text(caseModel.status.displayName,
                      style:
                          const TextStyle(fontSize: 11, color: Colors.white)),
                  backgroundColor: _statusColor(caseModel.status),
                  padding: EdgeInsets.zero,
                ),
              ]),
              const SizedBox(height: 4),
              Text(caseModel.clientName,
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(
                  '${caseModel.productType} - ${fmt.format(caseModel.requestedAmount)}',
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 4),
              Text('By ${caseModel.createdByName}',
                  style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
        ),
      ),
    );
  }
}
