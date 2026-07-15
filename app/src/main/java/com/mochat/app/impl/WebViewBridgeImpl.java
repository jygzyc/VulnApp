package com.mochat.app.impl;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.mochat.app.api.svc.IWebViewBridge;
import com.mochat.app.core.Reflector;
import com.mochat.app.util.PrefStore;

/**
 * WebView bridge implementation — chains #3 and #7.
 *
 * <p>Two intentional flaws:
 * <ol>
 *   <li><b>{@link #configure}</b> enables {@code setAllowFileAccessFromFileURLs} and
 *       {@code setJavaScriptEnabled} via {@link Reflector} so the method names do not
 *       appear in smali. The bridge is added via {@code addJavascriptInterface} (also
 *       through Reflector), exposing {@link #getToken}/{@link #exec}/{@link #miniAppSecret}
 *       to any loaded page.</li>
 *   <li><b>{@link #exec}</b> runs a shell command and returns stdout — classic js2native
 *       arbitrary code execution when the WebView loads attacker-controlled content.</li>
 * </ol>
 * </p>
 *
 * <p>Note: the {@link JavascriptInterface} annotations are required for the methods to
 * be reachable from JS on API 17+. They are kept by the ProGuard rules.</p>
 */
public final class WebViewBridgeImpl implements IWebViewBridge {

    private static volatile Context sCtx;
    public static void init(Context ctx) { sCtx = ctx.getApplicationContext(); }
    private Context ctx() { return sCtx; }

    @Override public String name() { return "WebViewBridge"; }

    @Override public void configure(WebView webview) {
        WebSettings s = webview.getSettings();
        // The names below are deliberately routed through Reflector + Obf so a static
        // scan for "setAllowFileAccessFromFileURLs" finds nothing.
        Reflector.callBool(s, "setJavaScriptEnabled".getBytes(), true);
        Reflector.callBool(s, "setAllowFileAccessFromFileURLs".getBytes(), true);
        Reflector.callBool(s, "setAllowUniversalAccessFromFileURLs".getBytes(), true);
        // addJavascriptInterface(this, "MoChat") — register the bridge under window.MoChat.
        Reflector.callObjStr(webview, "addJavascriptInterface".getBytes(), this, "MoChat");
    }

    @JavascriptInterface
    @Override public String getToken() {
        // Returns the real JWT — proof of exploitation is stealing this real token.
        return PrefStore.jwt(ctx());
    }

    @JavascriptInterface
    @Override public String exec(String cmd) {
        // js2native RCE — runs an arbitrary shell command in the app's UID.
        try {
            java.util.Scanner sc = new java.util.Scanner(
                    Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd}).getInputStream())
                    .useDelimiter("\\A");
            return sc.hasNext() ? sc.next() : "";
        } catch (Throwable t) {
            return "err: " + t.getMessage();
        }
    }

    @JavascriptInterface
    @Override public String miniAppSecret(String appId) {
        // chain #7 — leaks the AppSecret for the requested mini-app to JS.
        return PrefStore.miniAppSecret(ctx(), appId);
    }
}
