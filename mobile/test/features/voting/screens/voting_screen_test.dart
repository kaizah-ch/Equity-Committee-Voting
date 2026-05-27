import 'package:dio/dio.dart';
import 'package:equity_committee_voting/core/di/injection.dart';
import 'package:equity_committee_voting/features/cases/models/case_model.dart';
import 'package:equity_committee_voting/features/cases/repository/case_repository.dart';
import 'package:equity_committee_voting/features/voting/bloc/voting_bloc.dart';
import 'package:equity_committee_voting/features/voting/repository/vote_repository.dart';
import 'package:equity_committee_voting/features/voting/screens/voting_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeCaseRepository extends CaseRepository {
  _FakeCaseRepository() : super(Dio());

  @override
  Future<CaseModel> getCase(String id) async {
    final now = DateTime.now();
    return CaseModel(
      id: id,
      referenceNumber: 'EC-TEST-001',
      clientName: 'Client',
      requestedAmount: 1000,
      productType: 'TERM_LOAN',
      status: CaseStatus.votingOpen,
      createdById: 'creator-1',
      createdByName: 'Creator',
      createdAt: now,
      updatedAt: now,
    );
  }
}

class _FailingLoadVoteRepository extends VoteRepository {
  _FailingLoadVoteRepository() : super(Dio());

  @override
  Future<List<VoteModel>> getVotes(String caseId) async {
    final requestOptions = RequestOptions(path: '/api/cases/$caseId/vote');
    throw DioException(
      requestOptions: requestOptions,
      response: Response<dynamic>(
        requestOptions: requestOptions,
        statusCode: 403,
        data: {'detail': 'Only committee members and chairperson can vote'},
      ),
      type: DioExceptionType.badResponse,
    );
  }
}

class _SuccessfulLoadVoteRepository extends VoteRepository {
  _SuccessfulLoadVoteRepository() : super(Dio());

  @override
  Future<List<VoteModel>> getVotes(String caseId) async {
    return [
      VoteModel(
        id: 'vote-1',
        caseId: caseId,
        voterId: 'user-1',
        voterName: 'Committee Member',
        voteChoice: VoteChoice.approve,
        votedAt: DateTime.now(),
      ),
    ];
  }
}

void main() {
  const secureStorageChannel =
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  setUp(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    await getIt.reset();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, (call) async {
      return null;
    });

    getIt.registerSingleton<FlutterSecureStorage>(const FlutterSecureStorage());
    getIt.registerSingleton<CaseRepository>(_FakeCaseRepository());
    getIt.registerFactory<VotingBloc>(
        () => VotingBloc(_FailingLoadVoteRepository()));
  });

  tearDown(() async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, null);
    await getIt.reset();
  });

  testWidgets('shows snackbar when load votes fails', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: VotingScreen(caseId: 'case-1'),
        ),
      ),
    );

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.byType(SnackBar), findsOneWidget);
    expect(
      find.descendant(
        of: find.byType(SnackBar),
        matching: find.text('You are not allowed to vote on this case.'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('hides vote actions after current user has voted',
      (tester) async {
    await getIt.reset();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, (call) async {
      if (call.method == 'read' &&
          call.arguments is Map &&
          (call.arguments as Map)['key'] == 'user_id') {
        return 'user-1';
      }
      if (call.method == 'read' &&
          call.arguments is Map &&
          (call.arguments as Map)['key'] == 'user_role') {
        return 'COMMITTEE_MEMBER';
      }
      return null;
    });

    getIt.registerSingleton<FlutterSecureStorage>(const FlutterSecureStorage());
    getIt.registerSingleton<CaseRepository>(_FakeCaseRepository());
    getIt.registerFactory<VotingBloc>(
        () => VotingBloc(_SuccessfulLoadVoteRepository()));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: VotingScreen(caseId: 'case-1'),
        ),
      ),
    );

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Approve'), findsWidgets);
    expect(find.text('Your vote has been recorded.'), findsOneWidget);
    expect(find.widgetWithText(ElevatedButton, 'Approve'), findsNothing);
  });
}
