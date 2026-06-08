import 'package:dio/dio.dart';

enum UserRole { admin, committeeMember, chairperson, secretary }

extension UserRoleDisplay on UserRole {
  String get apiName => switch (this) {
        UserRole.admin => 'ADMIN',
        UserRole.committeeMember => 'COMMITTEE_MEMBER',
        UserRole.chairperson => 'CHAIRPERSON',
        UserRole.secretary => 'SECRETARY',
      };

  String get label => switch (this) {
        UserRole.admin => 'Admin',
        UserRole.committeeMember => 'Committee Member',
        UserRole.chairperson => 'Chairperson',
        UserRole.secretary => 'Secretary',
      };

  static UserRole fromApi(String value) => UserRole.values.firstWhere(
        (role) => role.apiName == value,
        orElse: () => UserRole.committeeMember,
      );
}

class ManagedUser {
  final String id;
  final String email;
  final String fullName;
  final UserRole role;
  final bool active;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const ManagedUser({
    required this.id,
    required this.email,
    required this.fullName,
    required this.role,
    required this.active,
    this.createdAt,
    this.updatedAt,
  });

  factory ManagedUser.fromJson(Map<String, dynamic> json) => ManagedUser(
        id: json['id'] as String,
        email: json['email'] as String,
        fullName: json['fullName'] as String,
        role: UserRoleDisplay.fromApi(json['role'] as String),
        active: json['active'] as bool,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.parse(json['createdAt'] as String),
        updatedAt: json['updatedAt'] == null
            ? null
            : DateTime.parse(json['updatedAt'] as String),
      );
}

class UserAdminRepository {
  final Dio _dio;

  UserAdminRepository(this._dio);

  Future<List<ManagedUser>> getUsers({int page = 0}) async {
    final response = await _dio.get(
      'admin/users',
      queryParameters: {'page': page, 'size': 100},
    );
    return (response.data['content'] as List)
        .map((e) => ManagedUser.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<ManagedUser> createUser({
    required String email,
    required String fullName,
    required UserRole role,
    required String password,
  }) async {
    final response = await _dio.post('admin/users', data: {
      'email': email,
      'fullName': fullName,
      'role': role.apiName,
      'password': password,
    });
    return ManagedUser.fromJson(response.data as Map<String, dynamic>);
  }

  Future<ManagedUser> updateUser({
    required String id,
    required String email,
    required String fullName,
    required UserRole role,
    required bool active,
  }) async {
    final response = await _dio.put('admin/users/$id', data: {
      'email': email,
      'fullName': fullName,
      'role': role.apiName,
      'active': active,
    });
    return ManagedUser.fromJson(response.data as Map<String, dynamic>);
  }

  Future<ManagedUser> deactivateUser(String id) async {
    final response = await _dio.delete('admin/users/$id');
    return ManagedUser.fromJson(response.data as Map<String, dynamic>);
  }

  Future<ManagedUser> reactivateUser(String id) async {
    final response = await _dio.patch('admin/users/$id/reactivate');
    return ManagedUser.fromJson(response.data as Map<String, dynamic>);
  }

  Future<ManagedUser> resetPassword({
    required String id,
    required String password,
  }) async {
    final response = await _dio.post(
      'admin/users/$id/reset-password',
      data: {'password': password},
    );
    return ManagedUser.fromJson(response.data as Map<String, dynamic>);
  }
}
