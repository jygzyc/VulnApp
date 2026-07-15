package com.mochat.app.api.svc;

import android.webkit.WebView;

import com.mochat.app.api.IMochatService;

/**
 * Mall H5 / mini-app WebView bridge (chains #3, #7).
 *
 * <p>The bridge object registered via {@code addJavascriptInterface} is a Proxy over
 * this interface. Its {@link JsBridge} annotation marks which methods are reachable
 * from JavaScript; the reflective dispatch in {@code ServiceHandler} means jadx does
 * not show the {@code @JavascriptInterface} methods on any concrete class.</p>
 */
public interface IWebViewBridge extends IMochatService {

    /**
     * Returns the current user's auth token. Reachable from JS because the impl
     * exposes the proxy under the name {@code MoChat}.
     */
    @JsBridge
    String getToken();

    /** Executes a shell-style command in the native runtime (chain #3 js2native). */
    @JsBridge
    String exec(String cmd);

    /** Mini-app runtime: returns the AppSecret for the given appId (chain #7). */
    @JsBridge
    String miniAppSecret(String appId);

    /** Configures a WebView with the vulnerable settings. */
    void configure(WebView webview);

    /** Marker: methods carrying this annotation are exported to JavaScript. */
    @interface JsBridge {
    }
}
