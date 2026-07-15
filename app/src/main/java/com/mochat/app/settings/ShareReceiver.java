package com.mochat.app.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.util.Log;

/**
 * Exported ShareReceiver (chain #5) — intent redirection.
 *
 * <p>Accepts an embedded {@link Intent} extra under the key {@code nextIntent} and
 * forwards it to {@link Context#startActivity}. Because the receiver runs in MoChat's
 * own UID, the forwarded intent can reach MoChat's <em>non-exported</em> activities —
 * which a third-party app could not start directly.</p>
 *
 * <p>This is the classic "access to app protected components" pattern (reported in
 * >80% of apps in large-scale studies). On
 * Android 14 the implicit-intent + BAL restrictions narrow the primitive, but the
 * classic "explicit extra &rarr; startActivity" pattern remains exploitable.</p>
 */
public final class ShareReceiver extends BroadcastReceiver {

    private static final String TAG = "ShareReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // VULNERABLE: pull a caller-supplied Intent extra and startActivity() it.
        Intent next = null;
        // Android < 33 / all: read as Parcelable extra.
        Parcelable p = intent.getParcelableExtra("nextIntent");
        if (p instanceof Intent) next = (Intent) p;
        if (next == null) {
            // Alternative encoding: a URI string the victim parses.
            String uri = intent.getStringExtra("nextIntentURI");
            if (uri != null) {
                try { next = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME); }
                catch (Throwable t) { Log.w(TAG, "parseUri failed", t); }
            }
        }
        if (next == null) return;

        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.i(TAG, "redirection -> " + next);
        try {
            context.startActivity(next);
        } catch (Throwable t) {
            Log.e(TAG, "redirection failed", t);
        }
    }
}
