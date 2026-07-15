# MoChat — Vulnerable Super-App 靶场

A deliberately-vulnerable Android training app: an IM + wallet + mall + mini-app
"super-app" (`com.mochat.app`) that bundles **14 app-layer exploit chains** (each depth ≥ 6),
all genuinely exploitable on **Android 13/14/15/16**. Designed against real-world
app-layer vulnerability classes documented in public security research and the
移动安全知识库.

> ⚠️ For authorized security training / CTF / research only. Do not ship. Do not run
> against devices you do not own.

---

## Quick build

Requires JDK 17+ and the Android SDK (with NDK + CMake).

```bash
# 1. Point Gradle at your SDK (forward slashes!)
echo 'sdk.dir=D:/ProgramData/Android/Sdk' > local.properties

# 2. Build the signed, obfuscated release APK
./gradlew assembleRelease

# 3. Output:
#    app/build/outputs/apk/release/app-release.apk
```

The build produces a **single release APK** with:
- **R8 minify + obfuscation** ON (impl classes are mangled / inlined)
- **native `libmochat.so`** for `arm64-v8a`, `armeabi-v7a`, `x86_64`
- **v2 + v3 APK signatures**

Install on a device/emulator:

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

---

## Toolchain

| Item | Value |
|------|-------|
| Android Gradle Plugin | 8.9.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.0 |
| JDK | 17 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| NDK | 27.2.12479018 |
| CMake | 3.31.6 |

---

## Signing credentials

The release APK is signed automatically by `assembleRelease`. The keystore and
`keystore.properties` are **gitignored** (regenerate if missing).

| Field | Value |
|-------|-------|
| Keystore file | `app/mochat-release.jks` (PKCS12) |
| Keystore password | `mochat123` |
| Key alias | `mochat` |
| Key password | `mochat123` |
| Key algorithm | RSA 2048, validity 10000 days |
| Certificate DN | `CN=MoChat Training, OU=Security Lab, O=VulnApp, L=Beijing, ST=Beijing, C=CN` |
| Cert SHA-256 | `fc24f005cda796f50826050ebd9ca0eb84186c0e851037fb1f1eda578600f1eda` |
| Cert SHA-1 | `73e3671273e13db0288ede6e51386d490438b0e8` |
| Signature schemes | v2 ✅ + v3 ✅ (Android 7.0+) |

`keystore.properties` (repo root, gitignored):

```properties
storeFile=app/mochat-release.jks
storePassword=mochat123
keyAlias=mochat
keyPassword=mochat123
```

### Regenerate the keystore

```bash
keytool -genkeypair -v \
  -keystore app/mochat-release.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias mochat \
  -storepass mochat123 -keypass mochat123 \
  -dname "CN=MoChat Training, OU=Security Lab, O=VulnApp, L=Beijing, ST=Beijing, C=CN"
```

> This is a **training keystore** with a public password — deliberately weak so that
> smali-patch-and-resign exercises (chain #12) are easy. Never reuse it for anything real.

---

## Project layout

```
VulnApp/
├── app/
│   ├── build.gradle.kts            # single release build, R8 + signing wired in
│   ├── proguard-rules.pro          # keep rules (components / JNI / JS interface / api)
│   └── src/main/
│       ├── AndroidManifest.xml     # the attack surface (exported components)
│       ├── java/com/mochat/app/
│       │   ├── api/                # service INTERFACES (kept; jadx-visible)
│       │   ├── core/               # ServiceLocator / Proxy / Handler / Obf (reflection)
│       │   ├── impl/               # concrete IMPLS (R8-mangled; hold the real vuln logic)
│       │   ├── wallet/ im/ mall/ settings/   # exported Activities/Services/Providers/Receivers
│       │   ├── nbridge/            # JNI bridge -> libmochat.so
│       │   └── util/               # SQLite helpers + plaintext prefs store
│       ├── cpp/                    # libmochat.so: XOR crypto, root/anti-debug, OBFUSCATE
│       └── res/
├── docs/chains/                   # per-chain writeups (chain-01.md … chain-14.md)
├── tools/exploits/                # drozer / frida / adb / apk scripts per chain
├── keystore.properties            # gitignored signing creds
└── local.properties               # gitignored SDK path
```

---

## The 14 chains

All app-layer (no OS-CVE dependency). See `docs/chains/chain-NN.md` for full writeups.

| # | Chain | Category | Surface |
|---|-------|----------|---------|
| 1  | Wallet Heist               | storage   | PaymentActivity + WalletService |
| 2  | Chat Exfiltration          | component | Contacts/Message/FileBackup providers |
| 3  | Mall WebView RCE           | webview   | MallH5Activity |
| 4  | PendingIntent File Theft   | intent    | PushService |
| 5  | Intent Redirection         | intent    | ShareReceiver |
| 6  | Wallet Crypto Oracle       | storage   | WalletService (Messenger) |
| 7  | Mini-App AppSecret Leak    | webview   | MallH5Activity (mini-app) |
| 8  | Plugin Dynamic-Load RCE    | storage   | OrderService |
| 9  | ZipSlip RCE                | storage   | OrderService |
| 10 | MITM → JWT Forge → ATO     | network   | PayWebActivity |
| 11 | App-Level Parcel Mismatch  | parcel    | OrderService (PaymentOrder) |
| 12 | Biometric + Frida Repack   | resilience| KeyguardBypassActivity |
| 13 | 4-Stage ACE via Exported Surface | component | LiveWallPreview + NotificationReceiver |
| 14 | Exported Account Takeover  | component | AccountActivity + TokenReceiver |
