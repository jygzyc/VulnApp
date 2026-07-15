package com.mochat.app.api.svc;

import com.mochat.app.api.IMochatService;

/**
 * Contacts / messages storage service (chain #2).
 *
 * <p>The exposed ContentProviders ({@code ContactsProvider}, {@code MessageProvider},
 * {@code FileBackupProvider}) delegate their query/insert/openFile handlers to this
 * interface. The interface keeps the SQL surface abstract so jadx cannot reveal the
 * raw {@code selection} string concatenation that yields SQL injection.</p>
 */
public interface IContactStore extends IMochatService {

    /** Returns a cursor-shaped result for the given (injected) selection. */
    android.database.Cursor queryContacts(String selection, String[] args);

    /** Insert a chat message; used by the exported MessageProvider. */
    long insertMessage(String from, String to, String body);

    /** Opens a file by raw path — vulnerable to {@code ../} traversal. */
    android.os.ParcelFileDescriptor openBackup(java.io.File path, String mode)
            throws java.io.FileNotFoundException;
}
