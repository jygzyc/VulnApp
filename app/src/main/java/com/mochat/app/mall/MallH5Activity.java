package com.mochat.app.mall;

import android.app.Activity;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.os.Build;

import androidx.annotation.Nullable;

import com.mochat.app.api.svc.IWebViewBridge;
import com.mochat.app.core.ServiceLocator;

/**
 * Exported MallH5Activity (chains #3 and #7) — deeplink &rarr; vulnerable WebView.
 *
 * <p>Reachable via the {@code mochat://mall/open?page=mall} deeplink. The {@code page}
 * parameter names a local asset under {@code assets/www/} (e.g. {@code mall},
 * {@code miniapp}); the activity loads {@code file:///android_asset/www/<page>.html}.
 * No network is required — the vulnerable content ships inside the APK.</p>
 *
 * <p>The WebView is configured with:
 * <ul>
 *   <li>{@code setAllowFileAccessFromFileURLs(true)} / {@code setAllowUniversalAccessFromFileURLs(true)}</li>
 *   <li>{@code addJavascriptInterface(bridge, "MoChat")}</li>
 * </ul>
 * …both routed through {@link com.mochat.app.core.Reflector} so the method names do not
 * appear in smali. The bridge exposes {@code getToken()}, {@code exec(cmd)} and
 * {@code miniAppSecret(appId)} to JS — yielding RCE (chain #3) and AppSecret leak
 * (chain #7).</p>
 */
public final class MallH5Activity extends Activity {

    private static final String TAG = "MallH5";
    private static final String ASSET_BASE = "file:///android_asset/www/";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The deeplink carries a `page` query param naming the local asset (without
        // extension). Falls back to the `url` extra for backward compatibility.
        Uri data = getIntent().getData();
        String page = data != null ? data.getQueryParameter("page") : null;
        if (page == null) page = getIntent().getStringExtra("page");
        if (page == null || page.isEmpty()) page = "mall";

        // Guard against path traversal in the page name so only assets/www/*.html load.
        if (page.contains("..") || page.contains("/") || page.contains("\\")) {
            page = "mall";
        }
        String target = ASSET_BASE + page + ".html";
        Log.i(TAG, "loading local asset: " + target);

        WebView wv = new WebView(this);
        setContentView(wv);

        // Apply system-bar insets as padding so the WebView doesn't overlap the
        // status bar / display cutout (camera notch).
        applyInsets(wv);

        // Configure the vulnerable WebView through the reflective bridge so the
        // sensitive WebSettings method names are hidden from static scanners.
        IWebViewBridge bridge = ServiceLocator.get(IWebViewBridge.class);
        bridge.configure(wv);

        wv.loadUrl(target);
    }

    /** Pad the view by the system-bar insets (status bar + nav bar + cutout). */
    static void applyInsets(View v) {
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars()
                            | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
}
