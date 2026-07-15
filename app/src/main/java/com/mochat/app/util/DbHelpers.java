package com.mochat.app.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Wallet + chat databases for MoChat.
 *
 * <p>Seeded with realistic PII that an attacker would actually want to steal:
 * bank card numbers (XOR-encrypted), payment PINs, real chat conversations,
 * server API keys, identity numbers. There are no artificial "flag{}" strings —
 * the proof of exploitation is recovering this real-looking sensitive data.</p>
 */
public final class DbHelpers {

    private DbHelpers() {}

    public static final class WalletDb extends SQLiteOpenHelper {
        public static final String DB_NAME = "wallet.db";
        public static final int    DB_VER  = 2;  // bumped to re-seed

        public static final String T_ACCOUNTS  = "accounts";
        public static final String T_CARDS     = "cards";   // bank card numbers (encrypted)
        public static final String T_KEYS      = "keys";

        public WalletDb(Context ctx) { super(ctx, DB_NAME, null, DB_VER); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + T_ACCOUNTS + " (" +
                    "_id INTEGER PRIMARY KEY, user_id TEXT, name TEXT, balance_enc BLOB)");
            db.execSQL("CREATE TABLE " + T_CARDS + " (" +
                    "_id INTEGER PRIMARY KEY, user_id TEXT, card_no_enc BLOB, bank TEXT, holder TEXT)");
            db.execSQL("CREATE TABLE " + T_KEYS + " (" +
                    "k TEXT PRIMARY KEY, v TEXT)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
            db.execSQL("DROP TABLE IF EXISTS " + T_ACCOUNTS);
            db.execSQL("DROP TABLE IF EXISTS " + T_CARDS);
            db.execSQL("DROP TABLE IF EXISTS " + T_KEYS);
            onCreate(db);
        }
    }

    public static final class ChatDb extends SQLiteOpenHelper {
        public static final String DB_NAME = "chat.db";
        public static final int    DB_VER  = 2;

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

    public static void seed(Context ctx) {
        seedWallet(ctx);
        seedChat(ctx);
    }

    private static void seedWallet(Context ctx) {
        WalletDb h = new WalletDb(ctx);
        SQLiteDatabase db = h.getWritableDatabase();

        // Account balance: ¥12,800.00 = 1280000 fen, XOR-encrypted.
        db.delete(WalletDb.T_ACCOUNTS, null, null);
        ContentValues a = new ContentValues();
        a.put("user_id", "u_1001");
        a.put("name", "Alice");
        a.put("balance_enc", xorBug("1280000".getBytes()));
        db.insert(WalletDb.T_ACCOUNTS, null, a);

        // Bank cards: card numbers XOR-encrypted (recoverable via the oracle).
        db.delete(WalletDb.T_CARDS, null, null);
        insertCard(db, "u_1001", "6225880212345678", "CMB",  "Alice Zhang");
        insertCard(db, "u_1001", "621700102003040506", "CCB", "Alice Zhang");

        // PLAINTEXT keys — the storage flaw.
        db.delete(WalletDb.T_KEYS, null, null);
        insertKey(db, "MASTER_PIN", "852013");
        insertKey(db, "WALLET_KEY", "MoChat!");
        insertKey(db, "PAY_TOKEN",  "pat_live_d8f3a9b2c1e7f0462a8d5b3c9e1f7a02");
        insertKey(db, "DEVICE_ID",  "IMEI:864295040012345");

        h.close();
    }

    private static void insertCard(SQLiteDatabase db, String uid, String cardNo,
                                   String bank, String holder) {
        ContentValues cv = new ContentValues();
        cv.put("user_id", uid);
        cv.put("card_no_enc", xorBug(cardNo.getBytes()));
        cv.put("bank", bank);
        cv.put("holder", holder);
        db.insert(WalletDb.T_CARDS, null, cv);
    }

    private static void insertKey(SQLiteDatabase db, String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        db.insert(WalletDb.T_KEYS, null, cv);
    }

    private static void seedChat(Context ctx) {
        ChatDb h = new ChatDb(ctx);
        SQLiteDatabase db = h.getWritableDatabase();

        // Contacts with realistic PII.
        db.delete(ChatDb.T_CONTACTS, null, null);
        Object[][] contacts = {
                {"Alice",  "13800001000", "110101199001011234", "alice.zhang@mochat.cn"},
                {"Bob",    "13800002000", "31010419920722456X", "bob.li@mochat.cn"},
                {"Carol",  "13800003000", "440305198811307890", "carol.wang@mochat.cn"},
                {"David",  "13900004000", "420106199506189012", "david.chen@mochat.cn"},
                {"Eve",    "13700005000", "51010419931205678X", "eve.zhao@mochat.cn"},
        };
        for (Object[] c : contacts) {
            ContentValues cv = new ContentValues();
            cv.put("name",    (String) c[0]);
            cv.put("phone",   (String) c[1]);
            cv.put("id_card", (String) c[2]);
            cv.put("email",   (String) c[3]);
            db.insert(ChatDb.T_CONTACTS, null, cv);
        }

        // Realistic private messages containing secrets.
        db.delete(ChatDb.T_MESSAGES, null, null);
        Object[][] msgs = {
                {"Bob",   "Alice", "转账给你了,查收。收款人:张三,金额:¥3,500",          1700000000L},
                {"Alice", "Bob",   "验证码是 729461,别告诉别人",                         1700000100L},
                {"Carol", "Alice", "服务器的 root 密码改成 M0ch@t#2024 了",             1700000200L},
                {"Alice", "Carol", "数据库连接串: mysql://prod:Pr0d@s10.0.1.23:3306",   1700000300L},
                {"Bob",   "Alice", "帮我充话费 200,我的号 13900004000",                 1700000400L},
                {"Alice", "David", "银行卡换了,新卡号 6225880212345678",               1700000500L},
                {"Eve",   "Alice", "VPN 账号 eve@corp 密码 Hk88_wLp 不外传",            1700000600L},
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

    /** Mirrors the native single-byte-XOR bug so seed data matches the oracle. */
    private static byte[] xorBug(byte[] data) {
        byte key = (byte) '!';
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ key);
        return out;
    }
}
