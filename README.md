# MoChat — Vulnerable Super-App 靶场

A deliberately-vulnerable Android training app: an IM + wallet + mall + mini-app
"super-app" (`com.mochat.app`) that bundles **10 composite exploit chains** covering
every Android component type (Activity, Service, Receiver, Provider, WebView,
Intent/PendingIntent, Parcel, Storage),
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

## The 10 composite chains

All app-layer (no OS-CVE dependency). Each chain crosses 2-4 components and requires
5-8 independent atomic steps from a zero-permission attacker to final impact.
Chains are mapped to the decx app-vulnhunt composite-chain shapes.

| # | Chain | Steps | Components crossed |
|---|-------|-------|--------------------|
| 1  | Wallet Drain via SQLi→Oracle→Payment       | 6 | Provider → Service → Activity |
| 2  | Chat PII Exfiltration Chain                 | 5 | Provider → Provider → Activity |
| 3  | WebView RCE to Persistent Backdoor          | 6 | WebView → Receiver → Provider |
| 4  | Messenger Oracle + Intent Redirect          | 5 | Service → Receiver |
| 5  | PendingIntent Hijack to Provider Theft      | 6 | Service → Intent → Provider |
| 6  | Broadcast Token Leak → JWT Forge → ATO      | 6 | Receiver → Storage → Network |
| 7  | Parcel Mismatch Payment Forgery             | 5 | Service (Parcel mismatch) |
| 8  | Backup Extraction + Native Crypto Reverse   | 6 | Storage → Native (.so reverse) |
| 9  | ZipSlip + DEX Plant → Code Execution        | 6 | Service → Provider → Dynamic load |
| 10 | Biometric + Root Bypass → Wallet Unlock     | 5 | Resilience → Activity (Frida) |

**Component coverage**: Activity ✅ Service ✅ Receiver ✅ Provider ✅ WebView ✅
Intent/PendingIntent ✅ Parcel ✅ Storage ✅ Native ✅ Resilience ✅


