class AppConstants {
  static const String baseUrl = String.fromEnvironment(
    'BASE_URL',
    defaultValue: 'https://api-voting.frontierfin.net/api',
  );
  static const String wsUrl = String.fromEnvironment(
    'WS_URL',
    defaultValue: 'wss://api-voting.frontierfin.net/ws',
  );

  static const int maxImagesPerCase = 40;
  static const int maxImageSizeMb = 10;
  static const int pageSize = 20;
  static const int messagePagSize = 50;

  static const String accessTokenKey = 'access_token';
  static const String refreshTokenKey = 'refresh_token';
  static const String userIdKey = 'user_id';
  static const String userRoleKey = 'user_role';
}
