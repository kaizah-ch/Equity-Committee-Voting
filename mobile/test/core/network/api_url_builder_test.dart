import 'package:equity_committee_voting/core/network/api_url_builder.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ApiUrlBuilder', () {
    const baseUrl = 'https://api-voting.frontierfin.net/api';

    test('keeps production API base URL normalized', () {
      expect(
        ApiUrlBuilder.normalizeBaseUrl(
            'https://api-voting.frontierfin.net/api/'),
        baseUrl,
      );
    });

    test('normalizes relative endpoints', () {
      expect(
        ApiUrlBuilder.normalizeEndpoint('/auth/login', baseUrl: baseUrl),
        'auth/login',
      );
      expect(
        ApiUrlBuilder.normalizeEndpoint('//users//me', baseUrl: baseUrl),
        'users/me',
      );
    });

    test('removes prefixes already present in the base URL', () {
      expect(
        ApiUrlBuilder.normalizeEndpoint('/api/auth/login', baseUrl: baseUrl),
        'auth/login',
      );
      expect(
        ApiUrlBuilder.finalApiUrl('/api/auth/login'),
        'https://api-voting.frontierfin.net/api/auth/login',
      );
    });

    test('deduplicates repeated api and v1 path segments', () {
      expect(
        ApiUrlBuilder.normalizeBaseUrl('https://example.com/api/api'),
        'https://example.com/api',
      );
      expect(
        ApiUrlBuilder.normalizeEndpoint('/v1/v1/users', baseUrl: '$baseUrl/v1'),
        'users',
      );
    });

    test('normalizes websocket URL without duplicating ws path', () {
      expect(
        ApiUrlBuilder.normalizeWebSocketUrl(
          'https://api-voting.frontierfin.net//ws/ws/',
        ),
        'wss://api-voting.frontierfin.net/ws',
      );
    });

    test('rejects absolute endpoints', () {
      expect(
        () => ApiUrlBuilder.normalizeEndpoint(
          'https://example.com/auth/login',
          baseUrl: baseUrl,
        ),
        throwsArgumentError,
      );
    });
  });
}
