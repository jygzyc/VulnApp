package com.mochat.app.impl;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.ParcelFileDescriptor;

import com.mochat.app.api.svc.IContactStore;
import com.mochat.app.util.DbHelpers;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Contact/message store implementation — chain #2.
 *
 * <p>The exported providers delegate to this class. The SQLi lives here: {@link #queryContacts}
 * concatenates the caller-supplied {@code selection} into the query, and {@link #openBackup}
 * opens any path the caller hands in, including {@code ../} traversal.</p>
 */
public final class ContactStoreImpl implements IContactStore {

    private static volatile Context sCtx;
    public static void init(Context ctx) { sCtx = ctx.getApplicationContext(); }
    private Context ctx() { return sCtx; }

    @Override public String name() { return "ContactStore"; }

    /**
     * SQLi sink. The caller (exported {@code ContactsProvider.query}) passes the raw
     * {@code selection} straight through here; {@code rawQuery} does not parameterize it.
     */
    @Override public Cursor queryContacts(String selection, String[] args) {
        Context c = ctx();
        if (c == null) return null;
        SQLiteDatabase db = new DbHelpers.ChatDb(c).getReadableDatabase();
        // VULNERABLE: selection is user-controlled and concatenated.
        return db.rawQuery(
                "SELECT _id, name, phone, id_card, email FROM " + DbHelpers.ChatDb.T_CONTACTS
                        + (selection != null && !selection.isEmpty() ? " WHERE " + selection : ""),
                args);
    }

    @Override public long insertMessage(String from, String to, String body) {
        Context c = ctx();
        if (c == null) return -1;
        SQLiteDatabase db = new DbHelpers.ChatDb(c).getWritableDatabase();
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put("from_user", from);
        cv.put("to_user", to);
        cv.put("body", body);
        cv.put("ts", System.currentTimeMillis() / 1000);
        return db.insert(DbHelpers.ChatDb.T_MESSAGES, null, cv);
    }

    /**
     * Path-traversal sink. The exported {@code FileBackupProvider.openFile} passes the
     * caller-controlled path here with no filtering. {@code 'data/data/.../wallet.db'}
     * or any world-path-accessible file is readable.
     */
    @Override public ParcelFileDescriptor openBackup(File path, String mode)
            throws FileNotFoundException {
        // VULNERABLE: no canonical-path containment check.
        int m = "r".equals(mode) ? ParcelFileDescriptor.MODE_READ_ONLY
                : ParcelFileDescriptor.MODE_READ_WRITE;
        return ParcelFileDescriptor.open(path, m);
    }
}
