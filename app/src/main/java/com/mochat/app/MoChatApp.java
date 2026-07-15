package com.mochat.app;

import android.app.Application;
import android.util.Log;

import com.mochat.app.core.ServiceLocator;
import com.mochat.app.api.svc.IResilienceService;
import com.mochat.app.impl.ContactStoreImpl;
import com.mochat.app.impl.OrderServiceImpl;
import com.mochat.app.impl.PaymentServiceImpl;
import com.mochat.app.impl.WebViewBridgeImpl;
import com.mochat.app.util.DbHelpers;
import com.mochat.app.util.PrefStore;

/**
 * MoChat application entry. Pre-warms the service locator so the first reflective
 * class load happens during startup (not on the critical user-input path).
 */
public final class MoChatApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("MoChat", "MoChat training app starting (all chains are app-layer)");
        // Seed the vulnerable DBs and prefs so every chain has real data to reach.
        DbHelpers.seed(this);
        PrefStore.seed(this);
        // Hand the application context to the impl classes that need it.
        PaymentServiceImpl.init(this);
        ContactStoreImpl.init(this);
        WebViewBridgeImpl.init(this);
        OrderServiceImpl.init(this);
        // Touch the locator to populate the registry cache.
        try {
            IResilienceService r = ServiceLocator.get(IResilienceService.class);
            r.environmentOk();
        } catch (Throwable t) {
            Log.w("MoChat", "resilience service init deferred", t);
        }
    }
}
