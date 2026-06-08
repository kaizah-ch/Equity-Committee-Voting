import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:get_it/get_it.dart';

import '../../features/auth/bloc/auth_bloc.dart';
import '../../features/auth/repository/auth_repository.dart';
import '../../features/cases/bloc/case_bloc.dart';
import '../../features/cases/repository/case_repository.dart';
import '../../features/discussion/bloc/message_bloc.dart';
import '../../features/discussion/repository/message_repository.dart';
import '../../features/voting/bloc/voting_bloc.dart';
import '../../features/voting/repository/vote_repository.dart';
import '../../features/notifications/bloc/notification_bloc.dart';
import '../../features/notifications/repository/notification_repository.dart';
import '../../features/notifications/repository/push_notification_repository.dart';
import '../../features/users/bloc/user_admin_bloc.dart';
import '../../features/users/repository/user_admin_repository.dart';
import '../network/api_client.dart';

final getIt = GetIt.instance;

void configureDependencies() {
  const storage = FlutterSecureStorage();
  getIt.registerSingleton<FlutterSecureStorage>(storage);

  final dio = createDioClient(storage);

  getIt.registerSingleton(AuthRepository(dio, storage));
  getIt.registerSingleton(CaseRepository(dio));
  getIt.registerSingleton(MessageRepository(dio));
  getIt.registerSingleton(VoteRepository(dio));
  getIt.registerSingleton(NotificationRepository(dio));
  getIt.registerSingleton(PushNotificationRepository(dio));
  getIt.registerSingleton(UserAdminRepository(dio));

  getIt.registerFactory(() => AuthBloc(
        getIt<AuthRepository>(),
        storage,
        getIt<PushNotificationRepository>(),
      ));
  getIt.registerFactory(() => CaseBloc(getIt<CaseRepository>()));
  getIt.registerFactory(() => MessageBloc(getIt<MessageRepository>()));
  getIt.registerFactory(() => VotingBloc(getIt<VoteRepository>()));
  getIt.registerFactory(() => NotificationBloc(getIt<NotificationRepository>()));
  getIt.registerFactory(() => UserAdminBloc(getIt<UserAdminRepository>()));
}
