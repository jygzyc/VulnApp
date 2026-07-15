package com.mochat.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Plaintext SharedPreferences secret store.
 *
 * <p>Stores the JWT auth token, the mini-app AppSecret, and the server base URL in
 * plaintext SharedPreferences — exactly the {@code mac.xml} pattern the IMA storage
 * article calls out. Readable via:
 * <ul>
 *   <li>{@code adb run-as} (debuggable)</li>
 *   <li>{@code adb backup} (allowBackup)</li>
 *   <li>{@code FileBackupProvider} path traversal</li>
 *   <li>the exported {@code MessageProvider}/{@code ContactsProvider} if proxied</li>
 * </ul>
 * </p>
 */
public final class PrefStore {

    private static final String FILE = "mochat_prefs.xml";

    private PrefStore() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences("mochat_prefs", Context.MODE_PRIVATE);
    }

    // ---- JWT / auth (chain #10 MITM target) ------------------------------------
    public static String jwt(Context ctx) {
        return sp(ctx).getString("jwt", "");
    }
    public static void jwt(Context ctx, String v) {
        sp(ctx).edit().putString("jwt", v).apply();
    }

    // ---- Mini-app AppSecret (chain #7) -----------------------------------------
    public static String miniAppSecret(Context ctx, String appId) {
        return sp(ctx).getString("mini_secret_" + appId, "");
    }
    public static void miniAppSecret(Context ctx, String appId, String v) {
        sp(ctx).edit().putString("mini_secret_" + appId, v).apply();
    }

    public static void seed(Context ctx) {
        SharedPreferences.Editor e = sp(ctx).edit();
        if (sp(ctx).getString("jwt", "").isEmpty()) {
            // a fake-but-structurally-valid HS256 JWT with weak-secret-vulnerable header
            e.putString("jwt",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +            // {"alg":"HS256","typ":"JWT"}
                "eyJ1c2VyX2lkIjoidV8xMDAxIiwicm9sZSI6InVzZXIiLCJleHAiOjk5OTk5OTk5OTl9." + // userId u_1001
                "s3cr3t");                                            // weak signature (native-XOR-recoverable key)
        }
        if (sp(ctx).getString("mini_secret_wxpay", "").isEmpty()) {
            e.putString("mini_secret_wxpay", "wx_app_secret_a1b2c3d4e5f6");
        }
        if (sp(ctx).getString("mini_secret_mall", "").isEmpty()) {
            e.putString("mini_secret_mall", "mall_app_secret_xyz789");
        }
        e.apply();
    }
}
