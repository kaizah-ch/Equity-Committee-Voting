import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../constants/app_constants.dart';

typedef MessageCallback = void Function(StompFrame frame);

class WebSocketClient {
  StompClient? _client;
  final String token;

  WebSocketClient(this.token);

  void connect({required VoidCallback onConnected}) {
    _client = StompClient(
      config: StompConfig.sockJS(
        url: AppConstants.wsUrl,
        onConnect: (frame) => onConnected(),
        stompConnectHeaders: {'Authorization': 'Bearer $token'},
        webSocketConnectHeaders: {'Authorization': 'Bearer $token'},
        onDisconnect: (_) {},
        onStompError: (frame) {},
        onWebSocketError: (error) {},
      ),
    );
    _client!.activate();
  }

  StompUnsubscribe subscribe(String destination, MessageCallback callback) {
    return _client!.subscribe(destination: destination, callback: callback);
  }

  void send(String destination, String body) {
    _client?.send(destination: destination, body: body);
  }

  void disconnect() {
    _client?.deactivate();
  }
}

typedef VoidCallback = void Function();
