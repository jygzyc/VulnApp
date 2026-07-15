package com.mochat.app.mall;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.mochat.app.api.svc.IOrderService;
import com.mochat.app.api.svc.IOrderService.PaymentOrder;
import com.mochat.app.core.ServiceLocator;

/**
 * Exported OrderService (Messenger IPC) — chains #8, #9, #11.
 *
 * <p>Accepts Messages from any caller and routes them to {@link IOrderService}:
 * <ul>
 *   <li>{@code MSG_CHECKOUT} — supplies a raw parcel {@link PaymentOrder}; the
 *       writeToParcel/readFromParcel mismatch scrambles the fields (chain #11).</li>
 *   <li>{@code MSG_INSTALL} — supplies base64 zip bytes &rarr; {@code installUpdate}
 *       with ZipSlip (chain #9).</li>
 *   <li>{@code MSG_LOAD} — supplies a {@code splitPath} &rarr; {@code loadPlugin}
 *       DexClassLoader RCE (chain #8).</li>
 * </ul>
 * </p>
 */
public final class OrderService extends Service {

    private static final String TAG = "OrderService";
    public static final int MSG_CHECKOUT = 0x901;
    public static final int MSG_INSTALL  = 0x902;
    public static final int MSG_LOAD     = 0x903;

    private final Messenger messenger = new Messenger(new Incoming());

    @Nullable @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    private final class Incoming extends android.os.Handler {
        @Override public void handleMessage(Message msg) {
            Message reply = Message.obtain();
            reply.what = msg.what;
            Bundle out = new Bundle();
            try {
                IOrderService svc = ServiceLocator.get(IOrderService.class);
                switch (msg.what) {
                    case MSG_CHECKOUT: {
                        // chain #11 — parcel mismatch: the PaymentOrder is deserialized
                        // by its CREATOR, whose read order disagrees with writeToParcel.
                        PaymentOrder order = msg.getData().getParcelable("order");
                        out.putString("result", svc.checkout(order));
                        break;
                    }
                    case MSG_INSTALL: {
                        // chain #9 — ZipSlip
                        byte[] zip = Base64.decode(msg.getData().getString("zip"), Base64.NO_WRAP);
                        out.putBoolean("ok", svc.installUpdate(zip));
                        break;
                    }
                    case MSG_LOAD: {
                        // chain #8 — DexClassLoader RCE (app chmod 444 first)
                        out.putBoolean("ok", svc.loadPlugin(msg.getData().getString("splitPath")));
                        break;
                    }
                    default:
                        super.handleMessage(msg);
                        return;
                }
            } catch (Throwable t) {
                Log.e(TAG, "msg " + msg.what + " failed", t);
                out.putString("error", t.getMessage());
            }
            reply.setData(out);
            try { msg.replyTo.send(reply); }
            catch (RemoteException e) { Log.w(TAG, "reply failed", e); }
        }
    }
}
