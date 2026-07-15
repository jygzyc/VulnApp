package com.mochat.app.mall;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.mochat.app.util.PrefStore;

/**
 * Exported PayWebActivity (chain #10) — trust-all TLS &rarr; MITM &rarr; JWT theft.
 *
 * <p>Loads the local checkout page ({@code assets/www/pay.html}) inside a WebView
 * and attaches the user's JWT as an {@code Authorization: Bearer} header. The page
 * also displays the JWT so the MITM attack surface is visible. The trust-all TLS
 * configuration below is what makes a real network request MITM-able — in this
 * offline build we keep the config but point at the local asset.</p>
 *
 * <p>The JWT itself is signed with a weak secret (the wallet XOR key, recoverable via
 * the chain #6 oracle), so the attacker can forge a new token for an arbitrary
 * {@code user_id} and take over the account.</p>
 */
public final class PayWebActivity extends Activity {

    private static final String TAG = "PayWeb";
    private static final String PAY_PAGE = "file:///android_asset/www/pay.html";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView wv = new WebView(this);
        wv.setWebViewClient(new WebViewClient());
        setContentView(wv);
        MallH5Activity.applyInsets(wv); // avoid camera-notch / status-bar overlap

        // Attach the JWT as an Authorization header when loading the page.
        String jwt = PrefStore.jwt(this);
        Log.i(TAG, "attaching jwt=" + jwt);
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + jwt);
        wv.loadUrl(PAY_PAGE, headers);

        // Demonstrate the trust-all TLS config (chain #10). In production this would
        // hit an HTTPS endpoint; here we just log the config to prove it is active.
        logTrustAllConfig();
    }

    /** Logs the trust-all TLS configuration that would make any HTTPS call MITM-able. */
    private void logTrustAllConfig() {
        try {
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String s) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String s) {} // trust ALL
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            Log.i(TAG, "trust-all TLS configured — any HTTPS request is MITM-able");
        } catch (Throwable t) {
            Log.e(TAG, "trust-all config failed", t);
        }
    }
}
