package com.mochat.app.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 10 exploit chains in strict ascending difficulty.
 *
 * <p>There are no artificial "flag{}" strings. The proof of exploitation is
 * recovering <b>real sensitive data</b>: bank card numbers, payment PINs, JWT
 * tokens, private chat messages, server credentials. Each chain's {@code objective}
 * field describes the real-world data the attacker obtains.</p>
 *
 * <p>Difficulty ladder: EASY (#1-3) → MEDIUM (#4-7) → HARD (#8-9) → INSANE (#10).</p>
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
        /** The real sensitive data an attacker obtains — proof of exploitation. */
        public final String objective;

        public Chain(int id, String name, Diff difficulty, String category,
                     String component, String objective) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.category = category;
            this.component = component;
            this.objective = objective;
        }
    }

    public static List<Chain> all() {
        List<Chain> c = new ArrayList<>();

        // ===== EASY =====

        c.add(new Chain(1, "Exported Balance Leak",
                Diff.EASY, "activity", "AccountActivity",
                "Read the victim's wallet balance (¥12,800) without authentication"));

        c.add(new Chain(2, "SQLi Contact Dump",
                Diff.EASY, "provider", "ContactsProvider",
                "Dump all contacts: names, phone numbers, national ID cards (身份证), emails"));

        c.add(new Chain(3, "Path Traversal to Wallet DB",
                Diff.EASY, "provider", "FileBackupProvider",
                "Read wallet.db → recover MASTER_PIN (852013), PAY_TOKEN, bank card table"));

        // ===== MEDIUM =====

        c.add(new Chain(4, "PendingIntent Hijack",
                Diff.MEDIUM, "intent", "PushService",
                "Hijack mutable PendingIntent → read mochat_prefs.xml (JWT, AppSecrets) via granted URI"));

        c.add(new Chain(5, "WebView JS-Bridge Exploit",
                Diff.MEDIUM, "webview", "MallH5Activity",
                "Call MoChat.getToken() → steal JWT; MoChat.exec() → arbitrary command execution"));

        c.add(new Chain(6, "Intent Redirection",
                Diff.MEDIUM, "intent", "ShareReceiver",
                "Forward a crafted Intent to non-exported components under MoChat's identity"));

        c.add(new Chain(7, "Broadcast Token Intercept",
                Diff.MEDIUM, "receiver", "TokenReceiver",
                "Register matching receiver → intercept JWT from TOKEN_BROADCAST"));

        // ===== HARD =====

        c.add(new Chain(8, "Messenger Crypto Oracle",
                Diff.HARD, "service", "WalletService",
                "Bind WalletService → reverse XOR → decrypt bank card numbers (622588...) from BLOBs"));

        c.add(new Chain(9, "Parcel Mismatch Payment Forge",
                Diff.HARD, "parcel", "OrderService",
                "Exploit writeToParcel/readFromParcel skew → forge paid=true → free order checkout"));

        // ===== INSANE =====

        c.add(new Chain(10, "AIDL Bypass + Native Reverse",
                Diff.INSANE, "service+native", "WalletService + libmochat.so",
                "Spoof packageName → dump key table; reverse libmochat.so → recover all encrypted data"));

        return Collections.unmodifiableList(c);
    }
}
