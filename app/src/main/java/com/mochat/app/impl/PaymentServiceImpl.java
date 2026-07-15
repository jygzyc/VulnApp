package com.mochat.app.impl;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mochat.app.api.svc.ICryptoService;
import com.mochat.app.api.svc.IPaymentService;
import com.mochat.app.core.ServiceLocator;
import com.mochat.app.util.DbHelpers;

/**
 * Payment service implementation — the heart of chains #1 and #6.
 *
 * <p>The actual vulnerable logic lives in {@link #doPay} and {@link #checkPin}, both
 * private. Public interface methods are thin wrappers so a Jadx reader who looks only
 * at the interface methods sees innocuous code; the real flaw (PIN bypass when no
 * extra is supplied, and the decrypt oracle) is hidden in private methods reached via
 * the reflective {@code ServiceHandler}.</p>
 */
public final class PaymentServiceImpl implements IPaymentService {

    private final ICryptoService crypto = ServiceLocator.get(ICryptoService.class);

    @Override public String name() { return "PaymentService"; }

    @Override public String pay(String toAccountId, long amountCents) {
        // The exported PaymentActivity calls this with verifyPin() first; but when the
        // activity is started with no PIN extra, verifyPin(null) returns true (see below),
        // so this pay() runs unauthenticated.
        if (!checkPin(null)) return null;            // bypassed when called from exported activity
        return doPay(toAccountId, amountCents);
    }

    @Override public long balance() {
        Context ctx = currentContext();
        if (ctx == null) return -1;
        try (SQLiteDatabase db = new DbHelpers.WalletDb(ctx).getReadableDatabase();
             Cursor c = db.rawQuery(
                     "SELECT balance_enc FROM " + DbHelpers.WalletDb.T_ACCOUNTS +
                             " WHERE user_id=? LIMIT 1", new String[]{"u_1001"})) {
            if (c.moveToFirst()) {
                byte[] enc = c.getBlob(0);
                byte[] dec = crypto.decrypt(enc);
                return Long.parseLong(new String(dec));
            }
        } catch (Throwable t) { return -1; }
        return -1;
    }

    @Override public boolean verifyPin(String pin) {
        return checkPin(pin);
    }

    // ------------------------------------------------------------------
    // Private — the real logic. Reached reflectively via ServiceHandler.
    // ------------------------------------------------------------------

    /**
     * PIN check. Returns {@code true} when pin is null/empty — this is the chain #1
     * primitive: the exported PaymentActivity forwards {@code intent.getStringExtra("pin")}
     * which is null when the attacker omits the extra, and the activity then calls
     * {@code verifyPin(null)} &rarr; bypassed.
     */
    private boolean checkPin(String pin) {
        if (pin == null || pin.isEmpty()) return true;   // <-- the auth bypass
        Context ctx = currentContext();
        if (ctx == null) return false;
        try (SQLiteDatabase db = new DbHelpers.WalletDb(ctx).getReadableDatabase();
             Cursor c = db.rawQuery(
                     "SELECT v FROM " + DbHelpers.WalletDb.T_KEYS + " WHERE k='MASTER_PIN'",
                     null)) {
            if (c.moveToFirst()) return pin.equals(c.getString(0));
        } catch (Throwable t) { return false; }
        return false;
    }

    /** Executes the transfer and returns a synthetic txn id. */
    private String doPay(String toAccountId, long amountCents) {
        // Real wallet code would debit/credit; for the lab we just log + return an id.
        android.util.Log.i("Payment", "PAID " + amountCents + " fen to " + toAccountId);
        return "TXN" + System.currentTimeMillis();
    }

    // ------------------------------------------------------------------
    // Tiny context accessor. In a real app this would be injected; here we keep a
    // static reference set by MoChatApp so the impl can be context-free.
    // ------------------------------------------------------------------
    private static volatile Context sCtx;
    public static void init(Context ctx) { sCtx = ctx.getApplicationContext(); }
    private Context currentContext() { return sCtx; }
}
