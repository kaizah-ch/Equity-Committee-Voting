import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../bloc/auth_bloc.dart';
import '../models/auth_models.dart';

class LoginScreen extends StatefulWidget {
  final bool showLogo;

  const LoginScreen({super.key, this.showLogo = true});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _emailCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  void _submit() {
    if (_formKey.currentState!.validate()) {
      context.read<AuthBloc>().add(LoginRequested(
            LoginCredentials(email: _emailCtrl.text.trim(), password: _passwordCtrl.text),
          ));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            return SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: constraints.maxHeight - 48),
                child: Form(
                  key: _formKey,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (widget.showLogo) ...[
                        Center(
                          child: Container(
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: const Color(0x1A1EA7FD),
                              borderRadius: BorderRadius.circular(24),
                            ),
                            child: Image.asset(
                              'assets/images/company_logo.png',
                              height: 120,
                              fit: BoxFit.contain,
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],
                      Text('Equity Committee',
                          style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700),
                          textAlign: TextAlign.center),
                      Text('Voting System',
                          style: Theme.of(context).textTheme.titleMedium,
                          textAlign: TextAlign.center),
                      const SizedBox(height: 44),
                      TextFormField(
                        controller: _emailCtrl,
                        decoration: const InputDecoration(
                            labelText: 'Email', prefixIcon: Icon(Icons.email_outlined)),
                        keyboardType: TextInputType.emailAddress,
                        validator: (v) => (v == null || !v.contains('@')) ? 'Enter a valid email' : null,
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _passwordCtrl,
                        decoration: InputDecoration(
                          labelText: 'Password',
                          prefixIcon: const Icon(Icons.lock_outline),
                          suffixIcon: IconButton(
                            icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility),
                            onPressed: () => setState(() => _obscure = !_obscure),
                          ),
                        ),
                        obscureText: _obscure,
                        validator: (v) => (v == null || v.length < 6) ? 'Password too short' : null,
                      ),
                      const SizedBox(height: 14),
                      BlocBuilder<AuthBloc, AuthState>(
                        builder: (context, state) {
                          if (state is! AuthFailure) return const SizedBox.shrink();
                          return Container(
                            margin: const EdgeInsets.only(bottom: 10),
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: Theme.of(context).colorScheme.errorContainer,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    state.message,
                                    style: TextStyle(
                                      color: Theme.of(context).colorScheme.onErrorContainer,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                TextButton(
                                  onPressed: _submit,
                                  child: const Text('Retry'),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                      const SizedBox(height: 8),
                      BlocBuilder<AuthBloc, AuthState>(
                        builder: (context, state) {
                          if (state is AuthLoading) {
                            return const Center(child: CircularProgressIndicator());
                          }
                          return ElevatedButton(onPressed: _submit, child: const Text('Sign In'));
                        },
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
