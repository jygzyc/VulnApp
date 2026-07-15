package com.mochat.app.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exploit-chain registry — 10 chains in strict ascending difficulty.
 *
 * <p>Difficulty ladder:</p>
 * <ol>
 *   <li><b>Easy</b>   (#1–#3): single exported component, direct access, no chaining.</li>
 *   <li><b>Medium</b> (#4–#7): cross-component, requires understanding IPC + Intent extras.</li>
 *   <li><b>Hard</b>   (#8–#9): native reverse, crypto oracle, multi-step composition.</li>
 *   <li><b>Insane</b> (#10): full reverse-engineering of native obfuscated crypto.</li>
 * </ol>
 *
 * <p>Each chain has a unique flag planted at its sink. Collect all 10 to prove mastery.</p>
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
        public final String flag;
        /** Full step-by-step path. */
        public final String steps;

        public Chain(int id, String name, Diff difficulty, String category,
                     String component, String flag, String steps) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.category = category;
            this.component = component;
            this.flag = flag;
            this.steps = steps;
        }
    }

    public static List<Chain> all() {
        List<Chain> c = new ArrayList<>();

        // ===== EASY (single exported component, direct access) =====

        // #1: launch exported AccountActivity, read balance + flag from screen
        c.add(new Chain(1, "Exported Balance Leak",
                Diff.EASY, "activity", "AccountActivity",
                "flag{01-exported-balance-leak}",
                "①adb am start AccountActivity (exported, no auth) → balance+flag displayed"));

        // #2: SQLi on ContactsProvider to dump the hidden flag contact
        c.add(new Chain(2, "SQLi Contact Dump",
                Diff.EASY, "provider", "ContactsProvider",
                "flag{02-sqli-contact-dump}",
                "①query ContactsProvider with ' OR 1=1 -- → flag row appears"));

        // #3: path traversal on FileBackupProvider to read wallet.db keys table
        c.add(new Chain(3, "Path Traversal to Wallet DB",
                Diff.EASY, "provider", "FileBackupProvider",
                "flag{03-path-traversal-wallet-db}",
                "①read content://com.mochat.app.backup/..%2Fdatabases%2Fwallet.db "
                + "②parse keys table → FLAG_03 entry"));

        // ===== MEDIUM (cross-component, IPC + Intent extras) =====

        // #4: PendingIntent hijack — mutate file_uri extra
        c.add(new Chain(4, "PendingIntent Hijack",
                Diff.MEDIUM, "intent", "PushService",
                "flag{04-pendingintent-hijack}",
                "①bind PushService, send MSG_GET_PENDING ②get mutable PendingIntent "
                + "③mutate file_uri extra ④fire → flag_04 in result"));

        // #5: WebView JS bridge — call exec() or getToken()
        c.add(new Chain(5, "WebView JS-Bridge RCE",
                Diff.MEDIUM, "webview", "MallH5Activity",
                "flag{06-webview-js-bridge-rce}",
                "①trigger mochat://mall/open?page=mall ②call MoChat.getToken() → flag appended"));

        // #6: Intent redirection via ShareReceiver
        c.add(new Chain(6, "Intent Redirection",
                Diff.MEDIUM, "intent", "ShareReceiver",
                "flag{04-pendingintent-hijack}",
                "①send broadcast with nextIntentURI pointing to internal component "
                + "②ShareReceiver forwards it → reach protected component"));

        // #7: Broadcast token leak → intercept JWT
        c.add(new Chain(7, "Broadcast Token Leak",
                Diff.MEDIUM, "receiver", "TokenReceiver",
                "flag{04-pendingintent-hijack}",
                "①register receiver for TOKEN_BROADCAST ②intercept JWT from token extra"));

        // ===== HARD (native reverse, crypto oracle, multi-step) =====

        // #8: Messenger decrypt oracle — decrypt the flag BLOB
        c.add(new Chain(8, "Messenger Crypto Oracle",
                Diff.HARD, "service", "WalletService",
                "flag{05-messenger-decrypt-oracle}",
                "①bind WalletService ②reverse native XOR (key[last]=0x21) "
                + "③MSG_DECRYPT the u_flag account BLOB → plaintext flag"));

        // #9: Parcel mismatch — forge paid=true via write/read skew
        c.add(new Chain(9, "Parcel Mismatch Forge",
                Diff.HARD, "parcel", "OrderService",
                "flag{07-parcel-mismatch-forge}",
                "①bind OrderService ②craft PaymentOrder bytes matching WRITE order "
                + "③read mismatch causes paid=true ④checkout returns flag"));

        // ===== INSANE (full reverse + composition) =====

        // #10: AIDL auth bypass + native reverse + full chain
        c.add(new Chain(10, "AIDL Bypass + Native Reverse",
                Diff.INSANE, "service+native", "WalletService + libmochat.so",
                "flag{10-native-crypto-reverse}",
                "①reverse libmochat.so OBFUSCATE macro ②recover XOR key "
                + "③spoof packageName on MSG_ADMIN_QUERY → key table dump "
                + "④reverse obfKey → final flag embedded in native"));

        return Collections.unmodifiableList(c);
    }
}
