package com.mochat.app.wallet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Exported TokenReceiver (chain #14).
 *
 * <p>Registered for {@code com.mochat.app.action.TOKEN_BROADCAST}. When the app (or any
 * other component) broadcasts the user's JWT under the {@code token} extra, this
 * receiver logs it. Because it is exported, a malicious app can register the same
 * action and read the token from the Intent — or, if the app uses
 * {@code sendOrderedBroadcast}, intercept and {@code abortBroadcast} it.</p>
 */
public final class TokenReceiver extends BroadcastReceiver {

    private static final String TAG = "TokenReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String token = intent.getStringExtra("token");
        // VULNERABLE: logging the secret token (visible to any app via logcat on
        // debuggable apps, and to the attacker app if it registers a matching receiver).
        Log.i(TAG, "received token=" + token);
    }
}
