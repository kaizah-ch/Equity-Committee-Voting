import 'package:dio/dio.dart';
import '../models/audit_log_model.dart';
import '../models/case_model.dart';

class CaseRepository {
  final Dio _dio;

  CaseRepository(this._dio);

  Future<List<CaseModel>> getCases({CaseStatus? status, int page = 0}) async {
    final result = await getCasesPage(status: status, page: page);
    return result.cases;
  }

  Future<({List<CaseModel> cases, bool hasMore})> getCasesPage({
    CaseStatus? status,
    int page = 0,
    int size = 20,
  }) async {
    final params = <String, dynamic>{'page': page, 'size': size};
    if (status != null) {
      params['status'] = status.apiName;
    }
    final response = await _dio.get('cases', queryParameters: params);
    final data = response.data as Map<String, dynamic>;
    final content = (data['content'] as List);
    final cases = content
        .map((e) => CaseModel.fromJson(e as Map<String, dynamic>))
        .toList();
    final isLast = data['last'] as bool? ?? cases.length < size;
    return (cases: cases, hasMore: !isLast);
  }

  Future<CaseModel> getCase(String id) async {
    final response = await _dio.get('cases/$id');
    return CaseModel.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CaseModel> createCase(Map<String, dynamic> data) async {
    final response = await _dio.post('cases', data: data);
    return CaseModel.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CaseModel> updateCase(String id, Map<String, dynamic> data) async {
    final response = await _dio.put('cases/$id', data: data);
    return CaseModel.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CaseModel> updateStatus(String id, String status) async {
    final response =
        await _dio.patch('cases/$id/status', data: {'status': status});
    return CaseModel.fromJson(response.data as Map<String, dynamic>);
  }

  Future<({List<AuditLogModel> logs, bool hasMore})> getCaseAuditLogs(
    String caseId, {
    int page = 0,
    int size = 20,
  }) async {
    final response = await _dio.get(
      'cases/$caseId/audit-logs',
      queryParameters: {'page': page, 'size': size},
    );
    final data = response.data as Map<String, dynamic>;
    final content = (data['content'] as List)
        .map((e) => AuditLogModel.fromJson(e as Map<String, dynamic>))
        .toList();
    final isLast = data['last'] as bool? ?? true;
    return (logs: content, hasMore: !isLast);
  }
}
