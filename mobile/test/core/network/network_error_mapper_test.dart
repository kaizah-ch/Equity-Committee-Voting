import 'package:dio/dio.dart';
import 'package:equity_committee_voting/core/network/network_error_mapper.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  DioException dioError({
    required String path,
    required int statusCode,
    required Map<String, dynamic> data,
  }) {
    final requestOptions = RequestOptions(path: path);
    return DioException(
      requestOptions: requestOptions,
      response: Response<dynamic>(
        requestOptions: requestOptions,
        statusCode: statusCode,
        data: data,
      ),
      type: DioExceptionType.badResponse,
    );
  }

  test('maps known auth detail to clearer copy', () {
    final error = dioError(
      path: '/api/auth/login',
      statusCode: 401,
      data: {'detail': 'Invalid credentials'},
    );

    expect(mapNetworkError(error), 'Email or password is incorrect.');
  });

  test('maps parent-message mismatch detail to discussion-specific copy', () {
    final error = dioError(
      path: '/api/cases/abc/messages',
      statusCode: 400,
      data: {'detail': 'Parent message does not belong to this case'},
    );

    expect(
      mapNetworkError(error),
      'Could not send reply in this case. Refresh messages and try again.',
    );
  });

  test('returns backend detail when no specific mapping exists', () {
    const detail = 'Some backend-specific validation message';
    final error = dioError(
      path: '/api/cases',
      statusCode: 400,
      data: {'detail': detail},
    );

    expect(mapNetworkError(error), detail);
  });
}
