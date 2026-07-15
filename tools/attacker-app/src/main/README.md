# MoChat Attacker App

A companion app that exercises the exported IPC surfaces (Messenger, PendingIntent,
Parcel, broadcast) that cannot be triggered via adb alone.

## Build

This is a standalone Android project. Copy to Android Studio or build with Gradle:

```bash
cd tools/attacker-app
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Chains triggered

| Chain | Method | IPC type |
|-------|--------|----------|
| #1/#4 | bind WalletService, MSG_DECRYPT oracle | Messenger |
| #5 | bind PushService, hijack PendingIntent | Messenger |
| #7 | bind OrderService, send mismatched PaymentOrder | Messenger + Parcel |
| #6 | register receiver, intercept TOKEN_BROADCAST | Broadcast |
