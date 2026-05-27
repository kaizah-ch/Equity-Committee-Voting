import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import '../bloc/case_bloc.dart';
import '../../../core/di/injection.dart';
import '../models/case_model.dart';

class CreateCaseScreen extends StatefulWidget {
  final CaseModel? initialCase;

  const CreateCaseScreen({super.key, this.initialCase});

  @override
  State<CreateCaseScreen> createState() => _CreateCaseScreenState();
}

class _CreateCaseScreenState extends State<CreateCaseScreen> {
  final _formKey = GlobalKey<FormState>();
  final _clientNameCtrl = TextEditingController();
  final _amountCtrl = TextEditingController();
  final _productTypeCtrl = TextEditingController();
  final _tenureCtrl = TextEditingController();
  final _summaryCtrl = TextEditingController();
  final _riskNotesCtrl = TextEditingController();
  final _collateralCtrl = TextEditingController();
  final _votingDeadlineCtrl = TextEditingController();

  bool get _isEditing => widget.initialCase != null;

  @override
  void initState() {
    super.initState();
    final initialCase = widget.initialCase;
    if (initialCase == null) return;

    _clientNameCtrl.text = initialCase.clientName;
    _amountCtrl.text = initialCase.requestedAmount.toStringAsFixed(2);
    _productTypeCtrl.text = initialCase.productType;
    _tenureCtrl.text = initialCase.tenure ?? '';
    _summaryCtrl.text = initialCase.summary ?? '';
    _riskNotesCtrl.text = initialCase.riskNotes ?? '';
    _collateralCtrl.text = initialCase.collateralSummary ?? '';
    _votingDeadlineCtrl.text = initialCase.votingDeadline == null
        ? ''
        : _formatDateTime(initialCase.votingDeadline!);
  }

  @override
  void dispose() {
    _clientNameCtrl.dispose();
    _amountCtrl.dispose();
    _productTypeCtrl.dispose();
    _tenureCtrl.dispose();
    _summaryCtrl.dispose();
    _riskNotesCtrl.dispose();
    _collateralCtrl.dispose();
    _votingDeadlineCtrl.dispose();
    super.dispose();
  }

  void _submit(BuildContext context) {
    if (_formKey.currentState!.validate()) {
      final requestedAmount = double.parse(_normalizedAmount);
      final payload = {
        'clientName': _clientNameCtrl.text.trim(),
        'requestedAmount': requestedAmount,
        'productType': _productTypeCtrl.text.trim(),
        'tenure': _nullableText(_tenureCtrl),
        'summary': _nullableText(_summaryCtrl),
        'riskNotes': _nullableText(_riskNotesCtrl),
        'collateralSummary': _nullableText(_collateralCtrl),
        'votingDeadline': _nullableText(_votingDeadlineCtrl),
      };
      final initialCase = widget.initialCase;
      if (initialCase == null) {
        context.read<CaseBloc>().add(CreateCase(payload));
      } else {
        context.read<CaseBloc>().add(UpdateCase(initialCase.id, payload));
      }
    }
  }

  String get _normalizedAmount => _amountCtrl.text.trim().replaceAll(',', '');

  String? _nullableText(TextEditingController controller) {
    final value = controller.text.trim();
    return value.isEmpty ? null : value;
  }

  String _formatDateTime(DateTime value) {
    final local = value.toLocal();
    String twoDigits(int number) => number.toString().padLeft(2, '0');
    return '${local.year}-${twoDigits(local.month)}-${twoDigits(local.day)}T'
        '${twoDigits(local.hour)}:${twoDigits(local.minute)}:00';
  }

  Future<void> _pickVotingDeadline() async {
    final current = widget.initialCase?.votingDeadline?.toLocal();
    final initialDate = current ?? DateTime.now().add(const Duration(days: 1));
    final date = await showDatePicker(
      context: context,
      initialDate: initialDate,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 3650)),
    );
    if (date == null || !mounted) return;

    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(current ?? initialDate),
    );
    if (time == null) return;

    final selected = DateTime(
      date.year,
      date.month,
      date.day,
      time.hour,
      time.minute,
    );
    _votingDeadlineCtrl.text = _formatDateTime(selected);
  }

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (_) => getIt<CaseBloc>(),
      child: BlocListener<CaseBloc, CaseState>(
        listener: (context, state) {
          if (state is CaseCreated) {
            context.go('/cases/${state.caseModel.id}');
          }
          if (state is CaseUpdated) {
            context.pop(state.caseModel);
          }
          if (state is CaseError) {
            ScaffoldMessenger.of(context)
                .showSnackBar(SnackBar(content: Text(state.message)));
          }
        },
        child: Scaffold(
          appBar: AppBar(title: Text(_isEditing ? 'Edit Case' : 'New Case')),
          body: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Form(
              key: _formKey,
              child: Column(
                children: [
                  _field(_clientNameCtrl, 'Client Name', required: true),
                  _field(
                    _amountCtrl,
                    'Requested Amount',
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                    validator: (_) {
                      if (_normalizedAmount.isEmpty) {
                        return 'Requested Amount is required';
                      }
                      final amount = double.tryParse(_normalizedAmount);
                      if (amount == null || amount <= 0) {
                        return 'Enter a valid amount';
                      }
                      return null;
                    },
                  ),
                  _field(_productTypeCtrl, 'Product Type', required: true),
                  _field(_tenureCtrl, 'Tenure'),
                  _field(_summaryCtrl, 'Summary', maxLines: 4),
                  _field(_riskNotesCtrl, 'Risk Notes', maxLines: 3),
                  _field(_collateralCtrl, 'Collateral Summary', maxLines: 3),
                  _field(
                    _votingDeadlineCtrl,
                    'Voting Deadline',
                    readOnly: true,
                    suffixIcon: IconButton(
                      tooltip: 'Pick voting deadline',
                      icon: const Icon(Icons.event),
                      onPressed: _pickVotingDeadline,
                    ),
                  ),
                  const SizedBox(height: 24),
                  BlocBuilder<CaseBloc, CaseState>(
                    builder: (context, state) {
                      if (state is CaseLoading) {
                        return const CircularProgressIndicator();
                      }
                      return ElevatedButton(
                        onPressed: () => _submit(context),
                        child: Text(_isEditing ? 'Save Changes' : 'Submit Case'),
                      );
                    },
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _field(TextEditingController ctrl, String label,
      {int maxLines = 1,
      TextInputType? keyboardType,
      bool required = false,
      bool readOnly = false,
      Widget? suffixIcon,
      String? Function(String?)? validator}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: TextFormField(
        controller: ctrl,
        readOnly: readOnly,
        onTap: readOnly ? () => FocusScope.of(context).unfocus() : null,
        decoration: InputDecoration(labelText: label, suffixIcon: suffixIcon),
        maxLines: maxLines,
        keyboardType: keyboardType,
        validator: validator ??
            (required
                ? (v) => (v == null || v.trim().isEmpty)
                    ? '$label is required'
                    : null
                : null),
      ),
    );
  }
}
