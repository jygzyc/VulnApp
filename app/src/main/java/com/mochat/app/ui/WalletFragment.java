package com.mochat.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mochat.app.R;
import com.mochat.app.api.svc.IPaymentService;
import com.mochat.app.core.ServiceLocator;

/**
 * Wallet tab — balance card + transfer form. The Pay button routes through
 * {@link IPaymentService#pay}, the same path the exported PaymentActivity uses
 * (chain #1). Leaving the PIN field empty exercises the auth bypass.
 */
public final class WalletFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_wallet, container, false);

        TextView balanceValue = v.findViewById(R.id.balanceValue);
        TextView balanceHint  = v.findViewById(R.id.balanceHint);
        EditText to     = v.findViewById(R.id.payTo);
        EditText amount = v.findViewById(R.id.payAmount);
        EditText pin    = v.findViewById(R.id.payPin);
        Button   payBtn = v.findViewById(R.id.payBtn);
        TextView result = v.findViewById(R.id.payResult);

        IPaymentService svc = ServiceLocator.get(IPaymentService.class);
        long balance = svc.balance();
        balanceValue.setText(balance >= 0 ? String.valueOf(balance) : getString(R.string.empty));
        balanceHint.setText(balance >= 0
                ? "≈ ¥" + (balance / 100.0)
                : "open the wallet to initialize");

        payBtn.setOnClickListener(btn -> {
            String toVal = to.getText().toString().trim();
            String amtStr = amount.getText().toString().trim();
            if (TextUtils.isEmpty(toVal) || TextUtils.isEmpty(amtStr)) {
                result.setText("err: to and amount required");
                return;
            }
            long amt;
            try { amt = Long.parseLong(amtStr); }
            catch (NumberFormatException e) { result.setText("err: bad amount"); return; }

            // PIN is forwarded straight to verifyPin — empty => bypass (chain #1).
            String pinVal = pin.getText().toString();
            boolean authed = svc.verifyPin(pinVal.isEmpty() ? null : pinVal);
            String txn = authed ? svc.pay(toVal, amt) : null;
            result.setText("authed=" + authed + " txn=" + (txn == null ? "null" : txn));
        });

        return v;
    }
}
