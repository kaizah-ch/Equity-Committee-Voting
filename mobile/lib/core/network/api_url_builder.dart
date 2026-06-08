import '../constants/app_constants.dart';

class ApiUrlBuilder {
  static const Set<String> _deduplicatedPrefixes = {
    'api',
    'v1',
    'ws',
    'socket',
  };

  static String get apiBaseUrl => normalizeBaseUrl(AppConstants.baseUrl);

  static String get dioBaseUrl => '$apiBaseUrl/';

  static String get webSocketUrl => normalizeWebSocketUrl(AppConstants.wsUrl);

  static String endpoint(String path) {
    return normalizeEndpoint(path, baseUrl: apiBaseUrl);
  }

  static String finalApiUrl(String path) {
    final base = apiBaseUrl.endsWith('/') ? apiBaseUrl : '$apiBaseUrl/';
    return Uri.parse(base).resolve(endpoint(path)).toString();
  }

  static String normalizeBaseUrl(String value) {
    final uri = Uri.parse(value.trim());
    if (!uri.hasScheme || uri.host.isEmpty) {
      throw ArgumentError.value(value, 'value', 'Base URL must be absolute');
    }

    final path = _normalizePath(uri.pathSegments);
    return _uriWithoutQueryOrFragment(uri, path).toString();
  }

  static String normalizeWebSocketUrl(String value) {
    final uri = Uri.parse(value.trim());
    if (!uri.hasScheme || uri.host.isEmpty) {
      throw ArgumentError.value(
          value, 'value', 'WebSocket URL must be absolute');
    }

    final scheme = switch (uri.scheme) {
      'https' => 'wss',
      'http' => 'ws',
      'wss' || 'ws' => uri.scheme,
      _ => uri.scheme,
    };
    final path = _normalizePath(uri.pathSegments);
    return _uriWithoutQueryOrFragment(uri.replace(scheme: scheme), path)
        .toString();
  }

  static String normalizeEndpoint(String value, {required String baseUrl}) {
    final trimmed = value.trim();
    final uri = Uri.tryParse(trimmed);
    if (uri != null && uri.hasScheme) {
      throw ArgumentError.value(value, 'value', 'Endpoint must be relative');
    }

    final endpointSegments = _deduplicateSegments(
      trimmed
          .replaceAll('\\', '/')
          .split('/')
          .where((segment) => segment.trim().isNotEmpty),
    );
    final baseSegments = Uri.parse(baseUrl)
        .pathSegments
        .where((segment) => segment.trim().isNotEmpty)
        .toList();

    while (endpointSegments.isNotEmpty &&
        baseSegments.isNotEmpty &&
        _isSameDeduplicatedPrefix(endpointSegments.first, baseSegments.last)) {
      endpointSegments.removeAt(0);
    }

    return endpointSegments.join('/');
  }

  static String _normalizePath(Iterable<String> rawSegments) {
    final segments = _deduplicateSegments(
      rawSegments.where((segment) => segment.trim().isNotEmpty),
    );
    return segments.isEmpty ? '' : '/${segments.join('/')}';
  }

  static List<String> _deduplicateSegments(Iterable<String> rawSegments) {
    final segments = <String>[];
    for (final rawSegment in rawSegments) {
      final segment = rawSegment.trim();
      if (segment.isEmpty) continue;
      if (segments.isNotEmpty &&
          _isSameDeduplicatedPrefix(segments.last, segment)) {
        continue;
      }
      segments.add(segment);
    }
    return segments;
  }

  static bool _isSameDeduplicatedPrefix(String left, String right) {
    final normalizedLeft = left.toLowerCase();
    final normalizedRight = right.toLowerCase();
    return normalizedLeft == normalizedRight &&
        _deduplicatedPrefixes.contains(normalizedLeft);
  }

  static Uri _uriWithoutQueryOrFragment(Uri uri, String path) {
    return Uri(
      scheme: uri.scheme,
      userInfo: uri.userInfo,
      host: uri.host,
      port: uri.hasPort ? uri.port : null,
      path: path,
    );
  }
}
