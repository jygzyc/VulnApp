package com.mochat.app.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exploit-chain registry — 10 deep composite chains.
 *
 * <p>Each chain crosses 2-4 components and requires 5-8 independent atomic steps
 * from a zero-permission attacker to final impact. Chains are mapped to the decx
 * app-vulnhunt composite-chain shapes and cover every Android component type.</p>
 *
 * <p>The {@code steps} field documents the full source&rarr;sink path so writeups
 * can enumerate each atomic step with its evidence gate.</p>
 */
public final class Chains {

    private Chains() {}

    public enum Diff { EASY, MEDIUM, HARD, INSANE }

    public static final class Chain {
        public final int id;
        public final String name;
        public final Diff  difficulty;
        public final String category;
        public final String component;
        public final String modern;
        /** Full step-by-step path (source → … → sink → impact). */
        public final String steps;

        public Chain(int id, String name, Diff difficulty, String category,
                     String component, String modern, String steps) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.category = category;
            this.component = component;
            this.modern = modern;
            this.steps = steps;
        }
    }

    public static List<Chain> all() {
        List<Chain> c = new ArrayList<>();

        // 1. WALLET DRAIN (6 steps, crosses Provider → Service → Activity)
        //    SQLi leaks WALLET_KEY → native XOR reverse → Messenger oracle decrypts
        //    balance → forge amount → exported PaymentActivity bypasses PIN → transfer.
        c.add(new Chain(1, "Wallet Drain via SQLi→Oracle→Payment",
                Diff.INSANE, "provider+service+activity",
                "ContactsProvider → WalletService → PaymentActivity",
                "Fully exploitable",
                "①SQLi ContactsProvider to leak WALLET_KEY from keys table "
                + "②reverse native single-byte XOR (key=last byte) "
                + "③bind exported WalletService, MSG_DECRYPT the balance_enc BLOB "
                + "④re-encrypt a forged balance (e.g. 9999999 fen) "
                + "⑤write it back via FileBackupProvider path traversal to wallet.db "
                + "⑥launch exported PaymentActivity with no PIN extra → pay() succeeds"));

        // 2. CHAT PII EXFILTRATION (5 steps, crosses Provider → Provider → Storage)
        //    SQLi dumps contacts → path traversal reads chat.db → extract JWT from
        //    messages → use JWT to access AccountActivity → full PII + balance.
        c.add(new Chain(2, "Chat PII Exfiltration Chain",
                Diff.HARD, "provider+storage",
                "ContactsProvider → FileBackupProvider → AccountActivity",
                "Fully exploitable",
                "①SQLi ContactsProvider (' OR 1=1 --) dumps all contacts (id_card, phone) "
                + "②path-traversal FileBackupProvider reads chat.db "
                + "③extract JWT from messages table body field "
                + "④read plaintext MASTER_PIN via FileBackupProvider → wallet.db keys table "
                + "⑤launch exported AccountActivity to confirm balance, use PIN for transfer"));

        // 3. WEBVIEW RCE → PERSISTENT BACKDOOR (6 steps, crosses WebView → Receiver → Provider)
        //    Deeplink loads local HTML → JS bridge exec() → read token + plant file via
        //    FileProvider → NotificationReceiver triggers System.load → persistent ACE.
        c.add(new Chain(3, "WebView RCE to Persistent Backdoor",
                Diff.INSANE, "webview+receiver+provider",
                "MallH5Activity → NotificationReceiver → MallFileProvider",
                "Fully exploitable",
                "①trigger mochat://mall/open?page=mall deeplink "
                + "②JS calls MoChat.exec('id') to confirm code execution "
                + "③JS calls MoChat.getToken() to steal the JWT "
                + "④use FileProvider root-path to write libpayload.so to app_librarian/ "
                + "⑤send broadcast to NotificationReceiver with plantUri+plantPath+load=true "
                + "⑥receiver chmod 444 + System.load → JNI_OnLoad runs attacker code persistently"));

        // 4. MESSENGER ORACLE + INTENT REDIRECTION (5 steps, crosses Service → Receiver → Activity)
        //    Bind WalletService → decrypt balance via oracle → discover internal msg codes
        //    → use ShareReceiver intent redirection to launch private WalletConfigActivity
        //    → reconfigure transfer limit → drain wallet.
        c.add(new Chain(4, "Messenger Oracle + Intent Redirect to Config",
                Diff.HARD, "service+intent",
                "WalletService → ShareReceiver",
                "Fully exploitable",
                "①bind exported WalletService, enumerate msg.what codes (0xD01-0xD03) "
                + "②MSG_DECRYPT reveals balance and the WALLET_KEY via obfKey "
                + "③craft a nested Intent targeting the non-exported WalletConfigActivity "
                + "④send to exported ShareReceiver with nextIntent extra → redirected to config "
                + "⑤config activity raises the per-transfer limit (no auth check)"));

        // 5. PENDINGINTENT HIJACK → PROVIDER DATA THEFT (6 steps, crosses Service → Intent → Provider)
        //    Bind PushService → get mutable PendingIntent → mutate file_uri extra to point
        //    at contacts DB → PendingIntent fires under victim identity → reads protected
        //    provider → exfiltrates data via reply.
        c.add(new Chain(5, "PendingIntent Hijack to Provider Theft",
                Diff.INSANE, "intent+provider",
                "PushService → ContactsProvider",
                "Explicit + FLAG_MUTABLE on Android 14",
                "①bind exported PushService, send MSG_GET_PENDING "
                + "②receive a FLAG_MUTABLE PendingIntent with an explicit target "
                + "③mutate the 'file_uri' extra to content://com.mochat.app.contacts/contacts "
                + "④fire the PendingIntent → victim app reads its own provider under granted identity "
                + "⑤the VIEW_FILE action returns the full contacts cursor in the result "
                + "⑥attacker reads PII (id_card, phone, email) from the result extras"));

        // 6. BROADCAST TOKEN LEAK → JWT FORGE → ACCOUNT TAKEOVER (6 steps, crosses Receiver → Storage → Network)
        //    Register matching receiver → intercept TOKEN_BROADCAST → extract JWT →
        //    crack HS256 sig (weak key = wallet XOR key) → forge admin JWT → access
        //    PayWebActivity → complete account takeover.
        c.add(new Chain(6, "Broadcast Token Leak → JWT Forge → ATO",
                Diff.INSANE, "receiver+storage+network",
                "TokenReceiver → PrefStore → PayWebActivity",
                "Fully exploitable",
                "①register a receiver for com.mochat.app.action.TOKEN_BROADCAST "
                + "②intercept the JWT from the 'token' extra "
                + "③read WALLET_KEY from wallet.db via FileBackupProvider traversal "
                + "④crack the HS256 signature (weak key = XOR key 'MoChat!') "
                + "⑤forge a JWT with role=admin and user_id=u_attacker "
                + "⑥load PayWebActivity with forged Authorization header → account takeover"));

        // 7. PARCEL MISMATCH → PAYMENT FORGERY (5 steps, crosses Service → Parcel → Activity)
        //    Bind OrderService → send PaymentOrder parcel with crafted byte layout →
        //    write/read mismatch scrambles fields → paid=true → checkout returns OK
        //    → order confirmed without payment.
        c.add(new Chain(7, "Parcel Mismatch Payment Forgery",
                Diff.HARD, "parcel+service",
                "OrderService (PaymentOrder)",
                "Fully exploitable",
                "①bind exported OrderService, send MSG_CHECKOUT "
                + "②craft PaymentOrder parcel bytes matching the WRITE order "
                + "③the mismatched readFromParcel deserializes paid=true (scrambled from amount) "
                + "④checkout() sees paid=true → returns OK-<orderId> "
                + "⑤order is confirmed without actual payment"));

        // 8. ALLOWBACKUP → KEY EXTRACTION → NATIVE REVERSE → DECRYPT (6 steps, crosses Storage → Native)
        //    adb backup → extract wallet.db + mochat_prefs.xml → read plaintext MASTER_PIN +
        //    WALLET_KEY → reverse libmochat.so XOR → decrypt all balance blobs offline.
        c.add(new Chain(8, "Backup Extraction + Native Crypto Reverse",
                Diff.HARD, "storage+native",
                "wallet.db + libmochat.so",
                "Fully exploitable",
                "①adb backup com.mochat.app (allowBackup=true) "
                + "②extract wallet.db, read keys table → MASTER_PIN + WALLET_KEY plaintext "
                + "③extract mochat_prefs.xml → JWT + mini-app AppSecrets "
                + "④reverse libmochat.so: walletDecrypt reduces to single-byte XOR with key[last] "
                + "⑤decrypt all balance_enc BLOBs offline "
                + "⑥recover full balance + all transaction history"));

        // 9. ORDER SERVICE ZISSLIP → DEX PLANT → CODE EXECUTION (6 steps, crosses Service → Provider → Native)
        //    Bind OrderService → send MSG_INSTALL with crafted zip → ZipSlip writes .so
        //    outside target dir → loadPlugin DexClassLoader loads attacker DEX → code
        //    runs under app UID with all permissions.
        c.add(new Chain(9, "ZipSlip + DEX Plant → App-UID Code Execution",
                Diff.INSANE, "storage+dynamic-load",
                "OrderService + MallFileProvider",
                "Fully exploitable",
                "①bind exported OrderService, send MSG_INSTALL with base64 zip payload "
                + "②zip entry ../../app_librarian/libpayload.so escapes target dir (ZipSlip) "
                + "③or: use MallFileProvider root-path to write DEX directly "
                + "④send MSG_LOAD with splitPath pointing to the planted file "
                + "⑤Os.chmod(path, 0444) satisfies Android 14 read-only-DEX rule "
                + "⑥DexClassLoader loads attacker code → runs under com.mochat.app UID"));

        // 10. BIOMETRIC BYPASS + ROOT BYPASS → WALLET UNLOCK (5 steps, crosses Resilience → Activity)
        //    Frida bypasses event-only biometric → native root detection returns clean →
        //    anti-debug TracerPid check bypassed → KeyguardBypassActivity unlocks wallet →
        //    balance + transfer accessible.
        c.add(new Chain(10, "Biometric + Root Bypass → Wallet Unlock",
                Diff.MEDIUM, "resilience+activity",
                "KeyguardBypassActivity + libmochat.so",
                "Fully exploitable",
                "①Frida-hook ResilienceServiceImpl.authenticate() → force return true "
                + "②Frida-hook NativeBridge.checkRoot() → force return false (clean) "
                + "③Frida-hook NativeBridge.antiDebug() → force return false (no tracer) "
                + "④launch exported KeyguardBypassActivity → all gates pass "
                + "⑤wallet unlocks: balance displayed + transfer enabled without biometric"));

        return Collections.unmodifiableList(c);
    }
}
