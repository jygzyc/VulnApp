package com.mochat.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mochat.app.R;
import com.mochat.app.mall.MallH5Activity;
import com.mochat.app.mall.PayWebActivity;

/**
 * Mall tab — product cards plus the entry points to the local-asset H5 WebView
 * (chain #3/#7) and the pay-web TLS surface (chain #10). All WebView content is
 * bundled inside the APK under {@code assets/www/}; no network is required.
 */
public final class MallFragment extends Fragment {

    private static final String[][] PRODUCTS = {
            {"MoChat Premium (1 mo)", "¥9.90"},
            {"1000 Coins",             "¥6.00"},
            {"Sticker Pack: Cats",     "¥1.00"},
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_mall, container, false);

        // Wire the static product rows.
        int[] ids = {R.id.p1, R.id.p2, R.id.p3};
        for (int i = 0; i < ids.length; i++) {
            View row = v.findViewById(ids[i]);
            ((TextView) row.findViewById(R.id.productName)).setText(PRODUCTS[i][0]);
            ((TextView) row.findViewById(R.id.productPrice)).setText(PRODUCTS[i][1]);
            // Buy -> opens the pay web (checkout) flow.
            row.findViewById(R.id.productBuy).setOnClickListener(b ->
                    startActivity(new Intent(requireContext(), PayWebActivity.class)));
        }

        // Mall H5 — loads assets/www/mall.html via the exported MallH5Activity.
        v.findViewById(R.id.openH5Btn).setOnClickListener(b ->
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("mochat://mall/open?page=mall"))));

        // Mini app — loads assets/www/miniapp.html.
        v.findViewById(R.id.openMiniAppBtn).setOnClickListener(b ->
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("mochat://mall/open?page=miniapp"))));

        v.findViewById(R.id.openPayWebBtn).setOnClickListener(b ->
                startActivity(new Intent(requireContext(), PayWebActivity.class)));

        return v;
    }
}
