# Equity Committee Voting Mobile

Flutter client for the Equity Committee Voting workflow.

## Backend Connection

The app reads the API and WebSocket base URLs from Dart defines:

```bat
flutter run --dart-define=BASE_URL=http://10.0.2.2:8080 --dart-define=WS_URL=http://10.0.2.2:8080/ws
```

`10.0.2.2` is the Android emulator route to the host machine. For a physical
device on the same Wi-Fi network, use the computer's LAN IP instead:

```bat
flutter run --dart-define=BASE_URL=http://YOUR_LAN_IP:8080 --dart-define=WS_URL=http://YOUR_LAN_IP:8080/ws
```

The defaults in `AppConstants` already target the Android emulator:

- `BASE_URL=http://10.0.2.2:8080`
- `WS_URL=http://10.0.2.2:8080/ws`

## Image Upload Check

With the backend running and S3 configured, sign in as `admin@equity.com` or
`chair@equity.com`, open a draft/submitted case created by that user, go to the
Images tab, and upload a JPG/PNG/WEBP image. The backend stores it in the
private S3 bucket and returns a short-lived presigned URL for display.
