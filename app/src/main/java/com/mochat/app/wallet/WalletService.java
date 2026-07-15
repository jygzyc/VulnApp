package com.mochat.app.wallet;

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

import com.mochat.app.api.svc.ICryptoService;
import com.mochat.app.api.svc.IPaymentService;
import com.mochat.app.core.ServiceLocator;

/**
 * Exported WalletService (Messenger IPC) — chain #6 decryption oracle.
 *
 * <p>Any app can {@code bindService} to this and send Messages with these {@code what}
 * codes (mirrors the exported-Messenger-auth-service pattern):
 * <ul>
 *   <li>{@code MSG_DECRYPT} — supply base64 ciphertext in {@code data.getString("b64")},
 *       receive plaintext in the reply. <b>This is the decryption oracle.</b></li>
 *   <li>{@code MSG_BALANCE} — returns the current decrypted balance in fen.</li>
 *   <li>{@code MSG_PAY} — performs an unauthenticated transfer (to + amount).</li>
 * </ul>
 * </p>
 *
 * <p>Because the service is exported and accepts any caller, and the key is stored
 * plaintext in the DB, an attacker has everything needed to decrypt the wallet blobs.</p>
 */
public final class WalletService extends Service {

    private static final String TAG = "WalletService";

    public static final int MSG_DECRYPT = 0xD01;
    public static final int MSG_BALANCE = 0xD02;
    public static final int MSG_PAY     = 0xD03;

    private final Messenger messenger = new Messenger(new Incoming());

    @Nullable @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    private final class Incoming extends android.os.Handler {
        @Override public void handleMessage(Message msg) {
            Message reply = Message.obtain();
            Bundle out = new Bundle();
            try {
                ICryptoService crypto = ServiceLocator.get(ICryptoService.class);
                IPaymentService pay   = ServiceLocator.get(IPaymentService.class);
                switch (msg.what) {
                    case MSG_DECRYPT: {
                        // ORACLE: any caller can decrypt arbitrary ciphertext.
                        String b64 = msg.getData().getString("b64");
                        byte[] enc = Base64.decode(b64, Base64.NO_WRAP);
                        out.putString("plain", new String(crypto.decrypt(enc)));
                        break;
                    }
                    case MSG_BALANCE: {
                        out.putLong("balance", pay.balance());
                        break;
                    }
                    case MSG_PAY: {
                        Bundle d = msg.getData();
                        String txn = pay.pay(d.getString("to"), d.getLong("amount"));
                        out.putString("txn", txn);
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
            reply.what = msg.what;
            try {
                msg.replyTo.send(reply);
            } catch (RemoteException e) {
                Log.w(TAG, "reply failed", e);
            }
        }
    }
}
