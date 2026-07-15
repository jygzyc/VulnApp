package com.mochat.app.im;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mochat.app.api.svc.IContactStore;
import com.mochat.app.core.ServiceLocator;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Exported FileBackupProvider (chain #2) — path-traversal sink.
 *
 * <p>Authority {@code com.mochat.app.backup}. The {@code openFile} implementation
 * passes {@code uri.getLastPathSegment()} straight into a {@code new File(root, ...)}
 * with no canonicalisation, so an attacker can read
 * {@code content://com.mochat.app.backup/..%2F..%2Fdatabases%2Fwallet.db} (URL-encoded
 * {@code ../}) to pull arbitrary app-private files.</p>
 */
public final class FileBackupProvider extends ContentProvider {

    private static final String TAG = "FileBackupProvider";

    @Override public boolean onCreate() { return true; }

    @Nullable @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        // VULNERABLE: no path filtering. The "root" is the app data dir but the
        // segment may contain ../ which escapes it.
        String seg = uri.getLastPathSegment();
        // Some clients URL-decode already; handle both %2F and ../ for the demo.
        seg = seg == null ? "" : seg.replace("%2F", "/").replace("%5C", "/");
        File root = getContext().getFilesDir().getParentFile(); // /data/data/com.mochat.app
        File target = new File(root, seg);
        Log.i(TAG, "openFile mode=" + mode + " target=" + target.getAbsolutePath());

        IContactStore store = ServiceLocator.get(IContactStore.class);
        return store.openBackup(target, mode.contains("w") ? "rw" : "r");
    }

    // --- query/insert/etc unused ---
    @Nullable @Override public Cursor query(@NonNull Uri u, @Nullable String[] p, @Nullable String s, @Nullable String[] a, @Nullable String so) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext == null ? "" : ext);
    }
    @Nullable @Override public Uri insert(@NonNull Uri u, @Nullable ContentValues v) { return null; }
    @Override public int delete(@NonNull Uri u, @Nullable String s, @Nullable String[] a) { return 0; }
    @Override public int update(@NonNull Uri u, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) { return 0; }
}
