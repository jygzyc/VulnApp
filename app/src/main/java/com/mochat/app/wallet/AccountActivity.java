package com.mochat.app.wallet;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.mochat.app.api.svc.IPaymentService;
import com.mochat.app.core.ServiceLocator;

/**
 * Exported AccountActivity (chain #14).
 *
 * <p>Shows the wallet balance — but because it is {@code exported=true} and does not
 * re-check authentication, any app can start it directly and read the balance, bypassing
 * the login flow entirely. This is the classic "exported activity skips auth" flaw
 * documented in the IMA Activity-security article (vuls/AccountActivity case).</p>
 */
public final class AccountActivity extends Activity {

    private static final String TAG = "AccountActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // NOTE: no auth check. Anyone can start this activity.
        IPaymentService svc = ServiceLocator.get(IPaymentService.class);
        long balance = svc.balance();
        Log.i(TAG, "balance displayed = " + balance + " fen");
        // FLAG 01: emitted only when the exported activity is launched without auth.
        com.mochat.app.util.Flags.emit(com.mochat.app.util.Flags.ALL[0]);
        TextView tv = new TextView(this);
        tv.setText("Balance: " + (balance >= 0 ? balance : "???") + " fen\n\n"
                + com.mochat.app.util.Flags.ALL[0]);
        tv.setPadding(32, 48, 32, 48);
        tv.setTextSize(20f);
        setContentView(tv);
    }
}
