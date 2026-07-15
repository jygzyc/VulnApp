# MoChat Exploit Chains

10 composite exploit chains, each crossing 2-4 components with 5-6 atomic steps.
All are app-layer (no OS-CVE dependency) and confirmed exploitable on Android 13-16.

## Chain index

| # | Chain | Difficulty | Steps | Writeup | Scripts |
|---|-------|------------|-------|---------|---------|
| 1 | Wallet Drain via SQLi→Oracle→Payment | INSANE | 6 | [chain-01](chain-01.md) | [adb](../../tools/exploits/adb/chain-01-sqli-oracle-pay.sh) |
| 2 | Chat PII Exfiltration | HARD | 5 | [chain-02](chain-02.md) | [adb](../../tools/exploits/adb/chain-02-pii-exfil.sh) |
| 3 | WebView RCE to Persistent Backdoor | INSANE | 6 | [chain-03](chain-03.md) | [adb](../../tools/exploits/adb/chain-03-webview-rce.sh) |
| 4 | Messenger Oracle + Intent Redirect | HARD | 5 | [chain-04](chain-04.md) | [frida](../../tools/exploits/frida/chain-04-dump-messenger.js) |
| 5 | PendingIntent Hijack to Provider Theft | INSANE | 6 | [chain-05](chain-05.md) | attacker-app |
| 6 | Broadcast Token Leak → JWT Forge → ATO | INSANE | 6 | [chain-06](chain-06.md) | attacker-app |
| 7 | Parcel Mismatch Payment Forgery | HARD | 5 | [chain-07](chain-07.md) | attacker-app |
| 8 | Backup Extraction + Native Crypto Reverse | HARD | 6 | [chain-08](chain-08.md) | [frida](../../tools/exploits/frida/chain-08-dump-obf.js) |
| 9 | ZipSlip + DEX Plant → Code Execution | INSANE | 6 | [chain-09](chain-09.md) | attacker-app |
| 10 | Biometric + Root Bypass → Wallet Unlock | MEDIUM | 5 | [chain-10](chain-10.md) | [frida](../../tools/exploits/frida/chain-10-bypass-all.js) |

## Tooling

| Tool | Location | Used for |
|------|----------|----------|
| adb scripts | `tools/exploits/adb/` | quick trigger of exported components |
| drozer module | `tools/exploits/drozer/` | provider SQLi, path traversal, activity launch |
| frida scripts | `tools/exploits/frida/` | hook bypass, oracle dump, class-name recovery |
| attacker app | `tools/attacker-app/` | Messenger/PendingIntent/Parcel IPC exploitation |

## Component coverage

Activity ✅ · Service ✅ · Receiver ✅ · Provider ✅ · WebView ✅
Intent ✅ · PendingIntent ✅ · Parcel ✅ · Storage ✅ · Native ✅ · Resilience ✅
