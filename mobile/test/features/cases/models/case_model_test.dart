import 'package:equity_committee_voting/features/cases/models/case_model.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CaseStatusDisplay', () {
    test('maps statuses to backend API names', () {
      expect(CaseStatus.draft.apiName, 'DRAFT');
      expect(CaseStatus.underReview.apiName, 'UNDER_REVIEW');
      expect(CaseStatus.votingOpen.apiName, 'VOTING_OPEN');
    });

    test('maps statuses to readable labels', () {
      expect(CaseStatus.draft.displayName, 'Draft');
      expect(CaseStatus.underReview.displayName, 'Under review');
      expect(CaseStatus.votingOpen.displayName, 'Voting open');
    });
  });
}
