package com.mochat.app.mall;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Exported ProviderProxyActivity — the "gaining access to arbitrary content providers"
 * primitive (a high-frequency pattern in real OEM-app audits).
 *
 * <p>The activity takes a {@code uri} extra and a {@code projection} / {@code selection}
 * extra, then calls {@link android.content.ContentResolver#query} in MoChat's own
 * identity. Because MoChat has access to its own private providers (ContactsProvider,
 * MessageProvider, etc.), a third-party app that cannot query those providers directly
 * can use this activity as a <b>trampoline</b> — exactly the pattern documented in
 * the "Access to arbitrary content providers" research.</p>
 *
 * <p>The result rows are echoed to logcat and to a TextView so the attacker (using
 * {@code startActivityForResult}) can read them.</p>
 */
public final class ProviderProxyActivity extends Activity {

    private static final String TAG = "ProviderProxy";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String uriStr = getIntent().getStringExtra("uri");
        String[] projection = getIntent().getStringArrayExtra("projection");
        String selection = getIntent().getStringExtra("selection");
        String[] selectionArgs = getIntent().getStringArrayExtra("selectionArgs");

        TextView tv = new TextView(this);
        tv.setPadding(32, 48, 32, 48);
        tv.setTextSize(13f);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        setContentView(tv);

        if (uriStr == null) {
            tv.setText("Pass 'uri' extra to query through MoChat's identity.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        try {
            Uri uri = Uri.parse(uriStr);
            // VULNERABLE: queries an attacker-supplied URI under MoChat's own UID.
            // The attacker reaches private providers that trust only MoChat.
            try (Cursor c = getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
                if (c != null) {
                    int n = c.getColumnCount();
                    // header
                    for (int i = 0; i < n; i++) {
                        sb.append(c.getColumnName(i)).append(i < n - 1 ? " | " : "\n");
                    }
                    // rows
                    while (c.moveToNext()) {
                        for (int i = 0; i < n; i++) {
                            sb.append(c.getString(i)).append(i < n - 1 ? " | " : "\n");
                        }
                    }
                }
            }
            Log.i(TAG, "proxy query: " + uriStr + " → " + sb.length() + " chars");
        } catch (Throwable t) {
            sb.append("err: ").append(t.getMessage());
            Log.e(TAG, "proxy query failed", t);
        }

        tv.setText(sb.length() > 0 ? sb.toString() : "(empty)");
    }
}
