package com.mochat.app.im;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mochat.app.api.svc.IContactStore;
import com.mochat.app.core.ServiceLocator;
import com.mochat.app.util.DbHelpers;

/**
 * Exported MessageProvider (chain #2).
 *
 * <p>Authority {@code com.mochat.app.messages}. Read access to the messages table is
 * granted with no permission check, so any app can dump private chat history. Insert
 * is also exposed (used by chain #2 to forge messages).</p>
 */
public final class MessageProvider extends ContentProvider {

    private static final String TAG = "MessageProvider";

    @Override public boolean onCreate() { return true; }

    @Nullable @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] args,
                        @Nullable String sortOrder) {
        // Delegate to the chat DB directly (read access — no permission gate).
        SQLiteDatabase db = new DbHelpers.ChatDb(getContext()).getReadableDatabase();
        Log.i(TAG, "query selection=" + selection);
        return db.query(DbHelpers.ChatDb.T_MESSAGES, projection, selection, args,
                null, null, sortOrder);
    }

    @Nullable @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        // Any app can forge a message into the victim's chat history. Routes through
        // the contact store so the reflective service path is actually exercised.
        IContactStore store = ServiceLocator.get(IContactStore.class);
        long id = store.insertMessage(
                values.getAsString("from_user"),
                values.getAsString("to_user"),
                values.getAsString("body"));
        return ContentUris.withAppendedId(uri, id);
    }

    // delete/update are intentionally unsupported stubs (ContentProvider requires them).
    @Override public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] a) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) { return 0; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
}
