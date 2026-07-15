package com.mochat.app.wallet;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.mochat.app.api.svc.IPaymentService;
import com.mochat.app.core.ServiceLocator;

/**
 * Exported PaymentActivity (chain #1).
 *
 * <p>Reads {@code to} and {@code amount} extras from the launching intent and forwards
 * them to {@link IPaymentService#pay}. The PIN comes from the {@code pin} extra — which
 * is <b>null</b> when an attacker omits it &rarr; {@code verifyPin(null)} returns true
 * (see PaymentServiceImpl) &rarr; the payment goes through unauthenticated.</p>
 */
public final class PaymentActivity extends Activity {

    private static final String TAG = "PaymentActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String to     = getIntent().getStringExtra("to");
        long   amount = getIntent().getLongExtra("amount", 0);
        String pin    = getIntent().getStringExtra("pin");     // null if omitted

        IPaymentService svc = ServiceLocator.get(IPaymentService.class);
        boolean authed = svc.verifyPin(pin);
        String txn;
        if (authed) {
            txn = svc.pay(to, amount);
        } else {
            txn = null;
        }
        Log.i(TAG, "to=" + to + " amount=" + amount + " pin=" + (pin == null ? "null" : "***")
                + " authed=" + authed + " txn=" + txn);
        // Echo the result so an attacker app with startActivityForResult can read it.
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText("Payment txn=" + txn + " authed=" + authed);
        tv.setPadding(32, 32, 32, 32);
        setContentView(tv);
        // For a clean demo, don't finish() — leave the result visible.
    }
}
