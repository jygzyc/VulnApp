package com.mochat.app.im;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;

/**
 * Exported LiveWallPreviewActivity (chain #13, stage 1) — arbitrary file read.
 *
 * <p>Mirrors a well-known exported-activity arbitrary-file-read pattern: the activity
 * accepts an embedded Parcelable under the {@code live_wall_paper} extra
 * whose {@code videoPath} is stored in a static field; the exported
 * {@code FileBackupProvider} then returns a {@link ParcelFileDescriptor} for that path.
 *
 * <p>Here we simplify: the attacker supplies a {@code path} extra and we read it
 * straight via {@link FileBackupProvider}, returning the bytes to the caller's
 * {@code onActivityResult}. This is the arbitrary-file-read primitive that yields the
 * JWT, the wallet key, and the private messages.</p>
 */
public final class LiveWallPreviewActivity extends Activity {

    private static final String TAG = "LiveWallPreview";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra("path");
        Log.i(TAG, "preview path=" + path);

        // Open the attacker-supplied path through the (separately exported) provider
        // so the primitive is reachable even from outside the process.
        try {
            Uri uri = Uri.parse("content://com.mochat.app.backup/" + (path == null ? "" : path));
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                // Hand the raw bytes back to the caller via the result intent so the
                // attacker app's onActivityResult can collect them.
                byte[] buf = new byte[(int) Math.min(pfd.getStatSize(), 1 << 20)];
                java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
                int n = fis.read(buf);
                fis.close();
                pfd.close();
                android.content.Intent reply = new android.content.Intent();
                reply.putExtra("bytes", java.util.Arrays.copyOf(buf, Math.max(n, 0)));
                setResult(RESULT_OK, reply);
            } else {
                setResult(RESULT_CANCELED);
            }
        } catch (Throwable t) {
            Log.e(TAG, "read failed", t);
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
