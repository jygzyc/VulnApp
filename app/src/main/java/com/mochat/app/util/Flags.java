package com.mochat.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Flag store for the 10 exploit chains.
 *
 * <p>Each chain has a unique flag planted at its sink point. The flag is only
 * reachable by successfully exploiting the corresponding vulnerability — it is
 * hidden in logcat output, encrypted in the database, stored behind the PIN gate,
 * obfuscated in native code, or locked behind a biometric check.</p>
 *
 * <p>Flags follow the format {@code flag{chain-NN-short-description}} and are
 * designed as proof-of-exploitation for CTF-style scoring.</p>
 */
public final class Flags {

    private static final String TAG = "FLAG";
    private static final String SP_NAME = "mochat_flags";
    private static final String SP_COLLECTED = "collected";

    private Flags() {}

    // The 10 flags, planted at increasing difficulty levels.
    public static final String[] ALL = {
        "flag{01-exported-balance-leak}",        // EASY:   read balance from exported Activity
        "flag{02-sqli-contact-dump}",             // MEDIUM: SQL injection on ContactsProvider
        "flag{03-path-traversal-wallet-db}",      // MEDIUM: path traversal to read wallet.db
        "flag{04-pendingintent-hijack}",          // HARD:   mutable PendingIntent mutation
        "flag{05-messenger-decrypt-oracle}",      // HARD:   bind WalletService MSG_DECRYPT
        "flag{06-webview-js-bridge-rce}",         // HARD:   MoChat.exec() from JS
        "flag{07-parcel-mismatch-forge}",         // HARD:   PaymentOrder write/read mismatch
        "flag{08-aidl-auth-bypass}",              // INSANE: spoof packageName on MSG_ADMIN_QUERY
        "flag{09-zipslip-dex-plant}",             // INSANE: ZipSlip + DexClassLoader
        "flag{10-native-crypto-reverse}",         // INSANE: reverse libmochat.so XOR + decrypt
    };

    /** Log a flag to logcat (used by sink points that naturally log output). */
    public static void emit(String flag) {
        Log.i(TAG, flag);
    }

    /** Record that the attacker collected a flag (for UI scoreboard). */
    public static void collect(Context ctx, String flag) {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String collected = sp.getString(SP_COLLECTED, "");
        if (!collected.contains(flag)) {
            collected = collected.isEmpty() ? flag : collected + "," + flag;
            sp.edit().putString(SP_COLLECTED, collected).apply();
        }
    }

    /** How many flags the attacker has collected so far. */
    public static int collectedCount(Context ctx) {
        String c = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getString(SP_COLLECTED, "");
        if (c.isEmpty()) return 0;
        return c.split(",").length;
    }

    /** Get the collected flags as a list. */
    public static String[] collectedList(Context ctx) {
        String c = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getString(SP_COLLECTED, "");
        return c.isEmpty() ? new String[0] : c.split(",");
    }

    /** Check if a specific flag has been collected. */
    public static boolean hasFlag(Context ctx, String flag) {
        String c = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getString(SP_COLLECTED, "");
        return c.contains(flag);
    }
}
