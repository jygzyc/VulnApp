package com.mochat.app.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Wallet DB.
 *
 * <p>Tables:
 * <ul>
 *   <li>{@code accounts}  (_id, user_id, name, balance_enc BLOB) — balance XOR-encrypted.</li>
 *   <li>{@code keys}      (k TEXT PRIMARY KEY, v TEXT) — MASTER_PIN and MASTER_PW
 *       stored in PLAINTEXT (the chain #1 / #6 storage flaw).</li>
 * </ul>
 *
 * <p>The plaintext key table is the deliberate storage flaw: master PIN and the
 * wallet encryption key sit next to the encrypted balance, in cleartext. Any reader of
 * the DB (via allowBackup, FileBackupProvider traversal, or the exported provider) gets
 * everything needed to decrypt balances.</p>
 */
public final class DbHelpers {

    private DbHelpers() {}

    public static final class WalletDb extends SQLiteOpenHelper {
        public static final String DB_NAME = "wallet.db";
        public static final int    DB_VER  = 1;

        public static final String T_ACCOUNTS = "accounts";
        public static final String T_KEYS     = "keys";

        public WalletDb(Context ctx) { super(ctx, DB_NAME, null, DB_VER); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + T_ACCOUNTS + " (" +
                    "_id INTEGER PRIMARY KEY, " +
                    "user_id TEXT, name TEXT, balance_enc BLOB)");
            db.execSQL("CREATE TABLE " + T_KEYS + " (" +
                    "k TEXT PRIMARY KEY, v TEXT)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
            db.execSQL("DROP TABLE IF EXISTS " + T_ACCOUNTS);
            db.execSQL("DROP TABLE IF EXISTS " + T_KEYS);
            onCreate(db);
        }
    }

    public static final class ChatDb extends SQLiteOpenHelper {
        public static final String DB_NAME = "chat.db";
        public static final int    DB_VER  = 1;

        public static final String T_CONTACTS = "contacts";
        public static final String T_MESSAGES = "messages";

        public ChatDb(Context ctx) { super(ctx, DB_NAME, null, DB_VER); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + T_CONTACTS + " (" +
                    "_id INTEGER PRIMARY KEY, name TEXT, phone TEXT, id_card TEXT, email TEXT)");
            db.execSQL("CREATE TABLE " + T_MESSAGES + " (" +
                    "_id INTEGER PRIMARY KEY, from_user TEXT, to_user TEXT, body TEXT, ts INTEGER)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
            db.execSQL("DROP TABLE IF EXISTS " + T_CONTACTS);
            db.execSQL("DROP TABLE IF EXISTS " + T_MESSAGES);
            onCreate(db);
        }
    }

    /**
     * Seed both DBs with realistic PII on first launch so the chains have real data
     * to exfiltrate. Called from {@link com.mochat.app.MoChatApp}.
     */
    public static void seed(Context ctx) {
        seedWallet(ctx);
        seedChat(ctx);
    }

    private static void seedWallet(Context ctx) {
        WalletDb h = new WalletDb(ctx);
        SQLiteDatabase db = h.getWritableDatabase();
        // default balance 1000.00 yuan = 100000 fen, single-byte-XOR encrypted
        byte[] balEnc = xorBug("100000".getBytes());
        ContentValues a = new ContentValues();
        a.put("user_id", "u_1001");
        a.put("name", "Alice");
        a.put("balance_enc", balEnc);
        db.insert(WalletDb.T_ACCOUNTS, null, a);

        // PLAINTEXT master PIN and the wallet key — the storage flaw.
        db.delete(WalletDb.T_KEYS, null, null);
        ContentValues k1 = new ContentValues(); k1.put("k", "MASTER_PIN"); k1.put("v", "135790"); db.insert(WalletDb.T_KEYS, null, k1);
        ContentValues k2 = new ContentValues(); k2.put("k", "WALLET_KEY"); k2.put("v", "MoChat!"); db.insert(WalletDb.T_KEYS, null, k2);
        h.close();
    }

    private static void seedChat(Context ctx) {
        ChatDb h = new ChatDb(ctx);
        SQLiteDatabase db = h.getWritableDatabase();
        // A few contacts with realistic PII (id_card = national ID, email, phone).
        Object[][] contacts = {
                {"Alice",  "13800001000", "110101199001011234", "alice@example.com"},
                {"Bob",    "13800002000", "31010419920722456X", "bob@example.com"},
                {"Carol",  "13800003000", "440305198811307890", "carol@example.com"},
        };
        for (Object[] c : contacts) {
            ContentValues cv = new ContentValues();
            cv.put("name",    (String) c[0]);
            cv.put("phone",   (String) c[1]);
            cv.put("id_card", (String) c[2]);
            cv.put("email",   (String) c[3]);
            db.insert(ChatDb.T_CONTACTS, null, cv);
        }
        Object[][] msgs = {
                {"Alice", "Bob",   "the gate code is 4213", 1700000000L},
                {"Bob",   "Alice", "wire 50000 to acct 6228...", 1700000100L},
                {"Alice", "Carol", "JWT for prod: eyJhbGciOi...", 1700000200L},
        };
        for (Object[] m : msgs) {
            ContentValues cv = new ContentValues();
            cv.put("from_user", (String) m[0]);
            cv.put("to_user",   (String) m[1]);
            cv.put("body",      (String) m[2]);
            cv.put("ts",        (Long) m[3]);
            db.insert(ChatDb.T_MESSAGES, null, cv);
        }
        h.close();
    }

    /** Mirrors the native single-byte-XOR bug so seed data matches what the oracle decrypts. */
    private static byte[] xorBug(byte[] data) {
        byte key = (byte) '!';
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ key);
        return out;
    }
}
