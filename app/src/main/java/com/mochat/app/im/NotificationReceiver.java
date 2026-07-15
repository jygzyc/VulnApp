package com.mochat.app.im;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Exported NotificationReceiver (chain #13, stages 2-4) — intent redirection &rarr;
 * FileProvider write &rarr; System.load persistent ACE.
 *
 * <p>Three flaws chained into a persistent-ACE pipeline:
 * <ol>
 *   <li><b>Intent redirection.</b> Reads a serialized Intent from the {@code contentIntentURI}
 *       extra and calls {@code startActivity(intent)} on it, forwarding to
 *       otherwise-unreachable non-exported activities.</li>
 *   <li><b>Arbitrary file write via FileProvider.</b> Reads a {@code plantUri} content URI
 *       and {@code plantPath} target, copies the bytes to {@code plantPath}. Because
 *       {@code MallFileProvider} is misconfigured with {@code <root-path path=""/>}, the
 *       target may be {@code /data/user/0/com.mochat.app/app_librarian/libpayload.so}.</li>
 *   <li><b>Native lib load.</b> Calls {@code System.load} on the planted path &rarr; the
 *       attacker's {@code JNI_OnLoad} runs in the MoChat process (persistent ACE).</li>
 * </ol>
 * </p>
 */
public final class NotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "NotifReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        handleRedirection(context, intent);
        handlePlant(context, intent);
    }

    /** Stage 2: intent redirection. */
    private void handleRedirection(Context ctx, Intent received) {
        String uri = received.getStringExtra("contentIntentURI");
        if (uri == null) return;
        try {
            Intent forwarded = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            // VULNERABLE: forwarding an attacker-crafted intent to startActivity.
            Log.i(TAG, "redirection -> " + forwarded);
            forwarded.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(forwarded);
        } catch (Throwable t) {
            Log.w(TAG, "redirection failed", t);
        }
    }

    /** Stage 3: arbitrary file write via FileProvider. Stage 4: System.load. */
    private void handlePlant(Context ctx, Intent received) {
        String plantUri = received.getStringExtra("plantUri");
        String plantPath = received.getStringExtra("plantPath");
        boolean load = received.getBooleanExtra("load", false);
        if (plantUri == null || plantPath == null) return;

        try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(plantUri));
             FileOutputStream out = new FileOutputStream(new File(plantPath))) {
            // VULNERABLE: writes attacker-supplied bytes to attacker-chosen path.
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            Log.i(TAG, "planted " + plantPath);
            if (load) {
                // chmod 444 to satisfy Android 14 read-only-DEX/SO rule, then load.
                // (System.load needs an absolute path.)
                try { android.system.Os.chmod(plantPath, 0444); } catch (Throwable ignored) {}
                Runtime.getRuntime().load(plantPath);
                Log.i(TAG, "loaded " + plantPath + " — JNI_OnLoad should have run");
            }
        } catch (Throwable t) {
            Log.e(TAG, "plant/load failed", t);
        }
    }
}
