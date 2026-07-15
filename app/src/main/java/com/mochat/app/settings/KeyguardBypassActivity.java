package com.mochat.app.settings;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mochat.app.api.svc.IPaymentService;
import com.mochat.app.api.svc.IResilienceService;
import com.mochat.app.core.ServiceLocator;

/**
 * Exported KeyguardBypassActivity (chain #12).
 *
 * <p>Demonstrates the event-only biometric bypass: the activity asks the
 * {@link IResilienceService} to authenticate and, on success, unlocks a wallet action
 * — but the {@code authenticate()} impl has no CryptoObject binding, so a Frida hook
 * that forces the success path bypasses the gate without any biometric. Combined with
 * {@code debuggable=true}, this whole flow can also be driven by jdwp method
 * invocation.</p>
 */
public final class KeyguardBypassActivity extends Activity {

    private static final String TAG = "KeyguardBypass";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setPadding(32, 48, 32, 48);
        tv.setTextSize(16f);
        setContentView(tv);

        IResilienceService res = ServiceLocator.get(IResilienceService.class);
        // Native root + anti-debug gates first.
        boolean env = res.environmentOk();
        boolean dbg = res.debugOk();
        // Event-only biometric gate — Frida-bypassable.
        boolean ok = res.authenticate();

        String msg = "env=" + env + " debug=" + dbg + " auth=" + ok;
        Log.i(TAG, msg);
        tv.setText(msg);

        if (ok) {
            // Auth "passed" — proceed to a privileged action.
            IPaymentService pay = ServiceLocator.get(IPaymentService.class);
            long balance = pay.balance();
            tv.append("\nBalance: " + balance + " fen (unlocked by auth)");
        }
    }
}
