import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/di/injection.dart';
import '../bloc/user_admin_bloc.dart';
import '../repository/user_admin_repository.dart';

class UserAdminScreen extends StatelessWidget {
  const UserAdminScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (_) => getIt<UserAdminBloc>()..add(LoadManagedUsers()),
      child: const _UserAdminView(),
    );
  }
}

class _UserAdminView extends StatelessWidget {
  const _UserAdminView();

  Future<void> _openUserDialog(BuildContext context,
      {ManagedUser? user}) async {
    final result = await showDialog<_UserFormResult>(
      context: context,
      builder: (_) => _UserFormDialog(user: user),
    );
    if (result == null || !context.mounted) return;
    context.read<UserAdminBloc>().add(SaveManagedUser(
          existingUser: user,
          email: result.email,
          fullName: result.fullName,
          role: result.role,
          active: result.active,
          password: result.password,
        ));
  }

  Future<void> _resetPassword(BuildContext context, ManagedUser user) async {
    final password = await showDialog<String>(
      context: context,
      builder: (_) => _PasswordResetDialog(user: user),
    );
    if (password == null || !context.mounted) return;
    context
        .read<UserAdminBloc>()
        .add(ResetManagedUserPassword(user, password));
  }

  Future<void> _toggleActive(BuildContext context, ManagedUser user) async {
    final action = user.active ? 'deactivate' : 'reactivate';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('${action[0].toUpperCase()}${action.substring(1)} user?'),
        content: Text('${user.fullName} will be $action'
            '${user.active ? 'd' : 'd'} for sign-in.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(user.active ? 'Deactivate' : 'Reactivate'),
          ),
        ],
      ),
    );
    if (confirmed == true && context.mounted) {
      context.read<UserAdminBloc>().add(ToggleManagedUserActive(user));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('User Management')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openUserDialog(context),
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('New User'),
      ),
      body: BlocConsumer<UserAdminBloc, UserAdminState>(
        listener: (context, state) {
          if (state is UserAdminLoaded && state.successMessage != null) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(state.successMessage!)),
            );
          }
          if (state is UserAdminError) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(state.message)),
            );
          }
        },
        builder: (context, state) {
          if (state is UserAdminLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          if (state is UserAdminError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(state.message, textAlign: TextAlign.center),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: () =>
                          context.read<UserAdminBloc>().add(LoadManagedUsers()),
                      icon: const Icon(Icons.refresh),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            );
          }
          if (state is UserAdminLoaded) {
            if (state.users.isEmpty) {
              return const Center(child: Text('No users found'));
            }
            return RefreshIndicator(
              onRefresh: () async => context
                  .read<UserAdminBloc>()
                  .add(LoadManagedUsers(showLoading: false)),
              child: Stack(
                children: [
                  ListView.separated(
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 88),
                    itemCount: state.users.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final user = state.users[index];
                      return _UserTile(
                        user: user,
                        onEdit: () => _openUserDialog(context, user: user),
                        onResetPassword: () => _resetPassword(context, user),
                        onToggleActive: () => _toggleActive(context, user),
                      );
                    },
                  ),
                  if (state.saving)
                    const Positioned(
                      left: 0,
                      right: 0,
                      top: 0,
                      child: LinearProgressIndicator(),
                    ),
                ],
              ),
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }
}

class _UserTile extends StatelessWidget {
  final ManagedUser user;
  final VoidCallback onEdit;
  final VoidCallback onResetPassword;
  final VoidCallback onToggleActive;

  const _UserTile({
    required this.user,
    required this.onEdit,
    required this.onResetPassword,
    required this.onToggleActive,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: user.active
              ? colorScheme.primaryContainer
              : colorScheme.surfaceContainerHighest,
          foregroundColor: user.active
              ? colorScheme.onPrimaryContainer
              : colorScheme.onSurfaceVariant,
          child: Text(user.fullName.trim().isEmpty
              ? '?'
              : user.fullName.trim()[0].toUpperCase()),
        ),
        title: Text(user.fullName),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(user.email),
            const SizedBox(height: 4),
            Wrap(
              spacing: 8,
              runSpacing: 4,
              children: [
                _StatusChip(label: user.role.label),
                _StatusChip(
                  label: user.active ? 'Active' : 'Inactive',
                  color: user.active ? Colors.green : Colors.grey,
                ),
              ],
            ),
          ],
        ),
        trailing: PopupMenuButton<_UserAction>(
          onSelected: (action) {
            switch (action) {
              case _UserAction.edit:
                onEdit();
              case _UserAction.resetPassword:
                onResetPassword();
              case _UserAction.toggleActive:
                onToggleActive();
            }
          },
          itemBuilder: (context) => [
            const PopupMenuItem(
              value: _UserAction.edit,
              child: ListTile(
                leading: Icon(Icons.edit_outlined),
                title: Text('Edit'),
              ),
            ),
            const PopupMenuItem(
              value: _UserAction.resetPassword,
              child: ListTile(
                leading: Icon(Icons.lock_reset),
                title: Text('Reset password'),
              ),
            ),
            PopupMenuItem(
              value: _UserAction.toggleActive,
              child: ListTile(
                leading: Icon(
                  user.active
                      ? Icons.person_off_outlined
                      : Icons.person_outline,
                ),
                title: Text(user.active ? 'Deactivate' : 'Reactivate'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

enum _UserAction { edit, resetPassword, toggleActive }

class _StatusChip extends StatelessWidget {
  final String label;
  final Color? color;

  const _StatusChip({required this.label, this.color});

  @override
  Widget build(BuildContext context) {
    final baseColor = color ?? Theme.of(context).colorScheme.primary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: baseColor.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: baseColor,
              fontWeight: FontWeight.w700,
            ),
      ),
    );
  }
}

class _UserFormResult {
  final String email;
  final String fullName;
  final UserRole role;
  final String? password;
  final bool active;

  const _UserFormResult({
    required this.email,
    required this.fullName,
    required this.role,
    required this.active,
    this.password,
  });
}

class _UserFormDialog extends StatefulWidget {
  final ManagedUser? user;

  const _UserFormDialog({this.user});

  @override
  State<_UserFormDialog> createState() => _UserFormDialogState();
}

class _UserFormDialogState extends State<_UserFormDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _emailController;
  late final TextEditingController _fullNameController;
  late final TextEditingController _passwordController;
  late UserRole _role;
  late bool _active;

  bool get _isEditing => widget.user != null;

  @override
  void initState() {
    super.initState();
    final user = widget.user;
    _emailController = TextEditingController(text: user?.email ?? '');
    _fullNameController = TextEditingController(text: user?.fullName ?? '');
    _passwordController = TextEditingController();
    _role = user?.role ?? UserRole.committeeMember;
    _active = user?.active ?? true;
  }

  @override
  void dispose() {
    _emailController.dispose();
    _fullNameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    Navigator.of(context).pop(_UserFormResult(
      email: _emailController.text.trim(),
      fullName: _fullNameController.text.trim(),
      role: _role,
      active: _active,
      password: _isEditing ? null : _passwordController.text,
    ));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(_isEditing ? 'Edit user' : 'New user'),
      content: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: _fullNameController,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(labelText: 'Full name'),
                validator: (value) {
                  if ((value ?? '').trim().length < 2) {
                    return 'Enter a full name';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 10),
              TextFormField(
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(labelText: 'Email'),
                validator: (value) {
                  final text = (value ?? '').trim();
                  if (!text.contains('@') || !text.contains('.')) {
                    return 'Enter a valid email';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 10),
              DropdownButtonFormField<UserRole>(
                initialValue: _role,
                decoration: const InputDecoration(labelText: 'Role'),
                items: UserRole.values
                    .map(
                      (role) => DropdownMenuItem(
                        value: role,
                        child: Text(role.label),
                      ),
                    )
                    .toList(),
                onChanged: (value) {
                  if (value != null) setState(() => _role = value);
                },
              ),
              if (!_isEditing) ...[
                const SizedBox(height: 10),
                TextFormField(
                  controller: _passwordController,
                  obscureText: true,
                  decoration:
                      const InputDecoration(labelText: 'Temporary password'),
                  validator: (value) {
                    if ((value ?? '').length < 8) {
                      return 'Use at least 8 characters';
                    }
                    return null;
                  },
                ),
              ],
              if (_isEditing)
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  value: _active,
                  title: const Text('Active'),
                  onChanged: (value) => setState(() => _active = value),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _submit,
          child: const Text('Save'),
        ),
      ],
    );
  }
}

class _PasswordResetDialog extends StatefulWidget {
  final ManagedUser user;

  const _PasswordResetDialog({required this.user});

  @override
  State<_PasswordResetDialog> createState() => _PasswordResetDialogState();
}

class _PasswordResetDialogState extends State<_PasswordResetDialog> {
  final _formKey = GlobalKey<FormState>();
  final _passwordController = TextEditingController();

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    Navigator.of(context).pop(_passwordController.text);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Reset password'),
      content: Form(
        key: _formKey,
        child: TextFormField(
          controller: _passwordController,
          obscureText: true,
          decoration: InputDecoration(
            labelText: 'New password',
            helperText: widget.user.email,
          ),
          validator: (value) {
            if ((value ?? '').length < 8) {
              return 'Use at least 8 characters';
            }
            return null;
          },
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _submit,
          child: const Text('Reset'),
        ),
      ],
    );
  }
}
