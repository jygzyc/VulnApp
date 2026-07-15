package com.mochat.app.settings;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Exported PushService (chain #4) — FLAG_MUTABLE PendingIntent file theft.
 *
 * <p>Returns a {@link PendingIntent} to the caller. The intent is created with
 * {@link PendingIntent#FLAG_MUTABLE} and an explicit target; because the caller can
 * mutate the extras of a mutable PendingIntent, and because the wrapped intent carries
 * a {@code content://} URI with granted read permission, the caller can rewrite the
 * extra to point at an arbitrary file URI and steal it.</p>
 *
 * <p>This is the modern (Android 14-adapted) variant of CVE-2023-44123/44125: implicit
 * intents are blocked, but explicit-intent + FLAG_MUTABLE is still weaponizable.</p>
 */
public final class PushService extends Service {

    private static final String TAG = "PushService";
    public static final int MSG_GET_PENDING = 0x401;

    private final Messenger messenger = new Messenger(new Incoming());

    @Nullable @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    private final class Incoming extends android.os.Handler {
        @Override public void handleMessage(Message msg) {
            if (msg.what != MSG_GET_PENDING) { super.handleMessage(msg); return; }
            // Build an explicit intent (Android 14 requires explicit) carrying a content
            // URI, and a mutable PendingIntent so the caller can mutate its extras.
            Intent target = new Intent("com.mochat.app.action.VIEW_FILE");
            target.setPackage("com.mochat.app");
            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // FLAG 04: this value is reachable only if the attacker mutates the
            // PendingIntent's file_uri extra to point at this hidden prefs entry.
            target.putExtra("file_uri", "content://com.mochat.app.backup/shared_prefs/mochat_prefs.xml");
            target.putExtra("flag_04", "flag{04-pendingintent-hijack}");

            // VULNERABLE: FLAG_MUTABLE allows the receiver of this PendingIntent to
            // mutate the wrapped intent's extras (including the file_uri).
            PendingIntent pi = PendingIntent.getService(
                    PushService.this,
                    1, target,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Message reply = Message.obtain();
            reply.what = msg.what;
            Bundle out = new Bundle();
            // Hand the PendingIntent back to the (untrusted) caller.
            out.putParcelable("pi", pi);
            reply.setData(out);
            try { msg.replyTo.send(reply); }
            catch (RemoteException e) { Log.w(TAG, "reply failed", e); }
        }
    }
}
