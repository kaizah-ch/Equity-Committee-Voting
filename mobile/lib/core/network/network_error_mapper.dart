import 'dart:io';

import 'package:dio/dio.dart';

import '../constants/app_constants.dart';

bool isTransientNetworkError(Object error) {
  if (error is! DioException) {
    return false;
  }
  final statusCode = error.response?.statusCode;
  return error.type == DioExceptionType.connectionError ||
      error.type == DioExceptionType.connectionTimeout ||
      error.type == DioExceptionType.receiveTimeout ||
      error.type == DioExceptionType.sendTimeout ||
      error.error is SocketException ||
      (statusCode != null && statusCode >= 500);
}

String mapNetworkError(Object error) {
  if (error is DioException) {
    final statusCode = error.response?.statusCode;
    final responseData = error.response?.data;
    final path = error.requestOptions.path;
    if (responseData is Map<String, dynamic>) {
      final detail = responseData['detail'] as String?;
      if (detail != null && detail.trim().isNotEmpty) {
        final mapped = _mapKnownBackendDetail(detail, path, statusCode);
        if (mapped != null) {
          return mapped;
        }
        return detail;
      }

      final message = responseData['message'] as String?;
      if (message != null && message.trim().isNotEmpty) {
        return message;
      }
    }

    if (statusCode == 401) {
      return 'Your session has expired. Please sign in again.';
    }
    if (statusCode == 403) {
      return 'You do not have permission to perform this action.';
    }
    if (statusCode == 404) return 'Requested resource was not found.';
    if (statusCode != null && statusCode >= 500) {
      return 'Server error. Please try again shortly.';
    }

    if (isTransientNetworkError(error)) {
      return 'Cannot reach backend at ${AppConstants.baseUrl}. '
          'Ensure the backend is running and reachable from this device.';
    }

    return 'Request failed. Please try again.';
  }

  return 'Something went wrong. Please try again.';
}

String? _mapKnownBackendDetail(String detail, String path, int? statusCode) {
  final normalized = detail.trim();

  if (normalized == 'Invalid credentials') {
    return 'Email or password is incorrect.';
  }
  if (normalized == 'Parent message does not belong to this case') {
    return 'Could not send reply in this case. Refresh messages and try again.';
  }
  if (normalized == 'Only committee members and chairperson can vote') {
    return 'You are not allowed to vote on this case.';
  }
  if (normalized == 'Voting is not open for this case') {
    return 'Voting is not open for this case yet.';
  }
  if (normalized == 'Voting deadline has passed for this case') {
    return 'Voting deadline has already passed for this case.';
  }
  if (normalized == 'You have already voted on this case') {
    return 'You have already submitted your vote for this case.';
  }
  if (normalized == 'Not allowed to access discussion for draft case') {
    return 'Discussion is locked while this case is in draft status.';
  }
  if (normalized == 'Not allowed to access votes for draft case') {
    return 'Votes are hidden while this case is in draft status.';
  }
  if (normalized == 'Not allowed to access draft case') {
    return 'This draft case is only visible to its creator, chairperson, or admin.';
  }

  if (statusCode == 403 && path.contains('/vote')) {
    return 'You do not have permission to vote on this case.';
  }
  if (statusCode == 403 && path.contains('/messages')) {
    return 'You do not have permission to post or view messages for this case.';
  }

  return null;
}
