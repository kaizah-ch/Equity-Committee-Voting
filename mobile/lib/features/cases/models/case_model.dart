enum CaseStatus {
  draft,
  submitted,
  underReview,
  votingOpen,
  approved,
  rejected,
  deferred,
  closed,
}

extension CaseStatusDisplay on CaseStatus {
  String get apiName => switch (this) {
        CaseStatus.draft => 'DRAFT',
        CaseStatus.submitted => 'SUBMITTED',
        CaseStatus.underReview => 'UNDER_REVIEW',
        CaseStatus.votingOpen => 'VOTING_OPEN',
        CaseStatus.approved => 'APPROVED',
        CaseStatus.rejected => 'REJECTED',
        CaseStatus.deferred => 'DEFERRED',
        CaseStatus.closed => 'CLOSED',
      };

  String get displayName => switch (this) {
        CaseStatus.draft => 'Draft',
        CaseStatus.submitted => 'Submitted',
        CaseStatus.underReview => 'Under review',
        CaseStatus.votingOpen => 'Voting open',
        CaseStatus.approved => 'Approved',
        CaseStatus.rejected => 'Rejected',
        CaseStatus.deferred => 'Deferred',
        CaseStatus.closed => 'Closed',
      };

  String get actionLabel => switch (this) {
        CaseStatus.draft => 'Return to draft',
        CaseStatus.submitted => 'Submit case',
        CaseStatus.underReview => 'Move to review',
        CaseStatus.votingOpen => 'Open voting',
        CaseStatus.approved => 'Approve case',
        CaseStatus.rejected => 'Reject case',
        CaseStatus.deferred => 'Defer case',
        CaseStatus.closed => 'Close case',
      };

  List<CaseStatus> get nextStatuses => switch (this) {
        CaseStatus.draft => const [CaseStatus.submitted],
        CaseStatus.submitted => const [CaseStatus.draft, CaseStatus.underReview],
        CaseStatus.underReview => const [
            CaseStatus.votingOpen,
            CaseStatus.deferred,
          ],
        CaseStatus.votingOpen => const [
            CaseStatus.approved,
            CaseStatus.rejected,
            CaseStatus.deferred,
          ],
        CaseStatus.approved => const [CaseStatus.closed],
        CaseStatus.rejected => const [CaseStatus.closed],
        CaseStatus.deferred => const [CaseStatus.closed],
        CaseStatus.closed => const [],
      };

  bool get canEdit => this == CaseStatus.draft ||
      this == CaseStatus.submitted ||
      this == CaseStatus.underReview;
}

class CaseModel {
  final String id;
  final String referenceNumber;
  final String clientName;
  final double requestedAmount;
  final String productType;
  final String? tenure;
  final String? summary;
  final String? riskNotes;
  final String? collateralSummary;
  final CaseStatus status;
  final DateTime? votingDeadline;
  final String? verdict;
  final String createdById;
  final String createdByName;
  final DateTime createdAt;
  final DateTime updatedAt;

  const CaseModel({
    required this.id,
    required this.referenceNumber,
    required this.clientName,
    required this.requestedAmount,
    required this.productType,
    this.tenure,
    this.summary,
    this.riskNotes,
    this.collateralSummary,
    required this.status,
    this.votingDeadline,
    this.verdict,
    required this.createdById,
    required this.createdByName,
    required this.createdAt,
    required this.updatedAt,
  });

  factory CaseModel.fromJson(Map<String, dynamic> json) {
    return CaseModel(
      id: json['id'] as String,
      referenceNumber: json['referenceNumber'] as String,
      clientName: json['clientName'] as String,
      requestedAmount: (json['requestedAmount'] as num).toDouble(),
      productType: json['productType'] as String,
      tenure: json['tenure'] as String?,
      summary: json['summary'] as String?,
      riskNotes: json['riskNotes'] as String?,
      collateralSummary: json['collateralSummary'] as String?,
      status: CaseStatus.values.byName(_toCamelCase(json['status'] as String)),
      votingDeadline: json['votingDeadline'] != null
          ? DateTime.parse(json['votingDeadline'] as String)
          : null,
      verdict: json['verdict'] as String?,
      createdById: json['createdById'] as String,
      createdByName: json['createdByName'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }

  static String _toCamelCase(String s) {
    final parts = s.toLowerCase().split('_');
    return parts[0] +
        parts.skip(1).map((p) => p[0].toUpperCase() + p.substring(1)).join();
  }
}
