import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../core/network/network_error_mapper.dart';
import '../models/case_model.dart';
import '../repository/case_repository.dart';

// Events
abstract class CaseEvent {}

class LoadCases extends CaseEvent {
  final CaseStatus? status;
  final bool showLoading;
  LoadCases({this.status, this.showLoading = true});
}

class LoadMoreCases extends CaseEvent {}

class CreateCase extends CaseEvent {
  final Map<String, dynamic> data;
  CreateCase(this.data);
}

class UpdateCase extends CaseEvent {
  final String id;
  final Map<String, dynamic> data;
  UpdateCase(this.id, this.data);
}

// States
abstract class CaseState {}

class CaseInitial extends CaseState {}

class CaseLoading extends CaseState {}

class CaseLoaded extends CaseState {
  final List<CaseModel> cases;
  final bool hasMore;
  final bool isLoadingMore;
  final CaseStatus? status;
  CaseLoaded(
    this.cases, {
    this.hasMore = false,
    this.isLoadingMore = false,
    this.status,
  });
}

class CaseError extends CaseState {
  final String message;
  CaseError(this.message);
}

class CaseCreated extends CaseState {
  final CaseModel caseModel;
  CaseCreated(this.caseModel);
}

class CaseUpdated extends CaseState {
  final CaseModel caseModel;
  CaseUpdated(this.caseModel);
}

// BLoC
class CaseBloc extends Bloc<CaseEvent, CaseState> {
  final CaseRepository _repository;
  int _currentPage = 0;
  CaseStatus? _currentStatus;
  List<CaseModel> _cases = [];
  bool _hasMore = false;
  bool _loadingMore = false;

  CaseBloc(this._repository) : super(CaseInitial()) {
    on<LoadCases>(_onLoadCases);
    on<LoadMoreCases>(_onLoadMore);
    on<CreateCase>(_onCreateCase);
    on<UpdateCase>(_onUpdateCase);
  }

  Future<void> _onLoadCases(LoadCases event, Emitter<CaseState> emit) async {
    if (event.showLoading) {
      emit(CaseLoading());
    }
    _currentPage = 0;
    _currentStatus = event.status;
    _cases = [];
    _hasMore = false;
    try {
      final result = await _repository.getCasesPage(status: event.status);
      _cases = result.cases;
      _hasMore = result.hasMore;
      emit(CaseLoaded(_cases, hasMore: _hasMore, status: _currentStatus));
    } catch (e) {
      emit(CaseError(mapNetworkError(e)));
    }
  }

  Future<void> _onLoadMore(LoadMoreCases event, Emitter<CaseState> emit) async {
    if (_loadingMore || !_hasMore) return;
    _loadingMore = true;
    emit(CaseLoaded(
      _cases,
      hasMore: _hasMore,
      isLoadingMore: true,
      status: _currentStatus,
    ));
    _currentPage++;
    try {
      final result = await _repository.getCasesPage(
        status: _currentStatus,
        page: _currentPage,
      );
      _cases = [..._cases, ...result.cases];
      _hasMore = result.hasMore;
    } catch (_) {
      _currentPage--;
    } finally {
      _loadingMore = false;
      emit(CaseLoaded(_cases, hasMore: _hasMore, status: _currentStatus));
    }
  }

  Future<void> _onCreateCase(CreateCase event, Emitter<CaseState> emit) async {
    emit(CaseLoading());
    try {
      final created = await _repository.createCase(event.data);
      emit(CaseCreated(created));
    } catch (e) {
      emit(CaseError(mapNetworkError(e)));
    }
  }

  Future<void> _onUpdateCase(UpdateCase event, Emitter<CaseState> emit) async {
    emit(CaseLoading());
    try {
      final updated = await _repository.updateCase(event.id, event.data);
      emit(CaseUpdated(updated));
    } catch (e) {
      emit(CaseError(mapNetworkError(e)));
    }
  }
}
