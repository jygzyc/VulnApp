package com.mochat.app.im;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mochat.app.api.svc.IContactStore;
import com.mochat.app.core.ServiceLocator;

/**
 * Exported ContactsProvider (chain #2) — SQL injection sink.
 *
 * <p>Authority {@code com.mochat.app.contacts}. The {@code selection} argument is passed
 * verbatim into {@link IContactStore#queryContacts} &rarr; {@code rawQuery}, so a caller
 * can inject {@code ' OR 1=1 --} to dump every contact (name/phone/id_card/email).</p>
 */
public final class ContactsProvider extends ContentProvider {

    private static final String TAG = "ContactsProvider";

    @Override public boolean onCreate() { return true; }

    @Nullable @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        Log.i(TAG, "query selection=" + selection);
        // VULNERABLE: raw caller selection reaches SQL.
        IContactStore store = ServiceLocator.get(IContactStore.class);
        return store.queryContacts(selection, selectionArgs);
    }

    // --- the rest are intentionally trivial / unused ---
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues v) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] a) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) { return 0; }
}
