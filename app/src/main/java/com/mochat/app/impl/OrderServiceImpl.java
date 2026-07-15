package com.mochat.app.impl;

import android.content.Context;
import android.system.Os;
import android.util.Base64;
import android.util.Log;

import com.mochat.app.api.svc.IOrderService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.DexClassLoader;

/**
 * Order service implementation — chains #8, #9, #11.
 *
 * <p>Three sinks reachable from the exported {@code OrderService} binder:
 * <ul>
 *   <li>{@link #checkout(PaymentOrder)} — consumes the deliberately-mismatched
 *       {@link PaymentOrder} parcel (chain #11).</li>
 *   <li>{@link #installUpdate(byte[])} — unzips attacker bytes with no
 *       {@code ../} filtering &rarr; ZipSlip (chain #9).</li>
 *   <li>{@link #loadPlugin(String)} — {@code DexClassLoader}-loads an
 *       attacker-controlled path, chmod 444 first to satisfy Android 14's
 *       read-only-DEX rule (chain #8).</li>
 * </ul>
 * </p>
 */
public final class OrderServiceImpl implements IOrderService {

    private static final String TAG = "OrderService";
    private static volatile Context sCtx;
    public static void init(Context ctx) { sCtx = ctx.getApplicationContext(); }
    private Context ctx() { return sCtx; }

    @Override public String name() { return "OrderService"; }

    /**
     * Chain #11: the PaymentOrder arrives over the binder. Its writeToParcel and
     * readFromParcel disagree on field order, so the deserialised object has its
     * fields scrambled. An attacker who controls the parcel can therefore inject a
     * {@code paid=true} + spoofed userId by crafting the bytes to match the WRITE order.
     */
    @Override public String checkout(PaymentOrder order) {
        // After the mismatch, 'order.paid' may already be true regardless of payment.
        Log.i(TAG, "checkout userId=" + order.userId + " paid=" + order.paid
                + " amount=" + order.amountCents);
        if (!order.paid) {
            return "ERR_NOT_PAID";
        }
        return "OK-" + order.orderId;
    }

    /**
     * Chain #9: ZipSlip. Each {@link ZipEntry#getName()} is used verbatim, so an entry
     * named {@code ../../lib/libpayload.so} escapes the target dir.
     */
    @Override public boolean installUpdate(byte[] zipBytes) {
        Context c = ctx();
        if (c == null) return false;
        File dest = c.getFilesDir();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                // VULNERABLE: no canonical-path containment check.
                File out = new File(dest, ze.getName());
                // the bug: ../../.. escapes dest
                new File(out.getParent()).mkdirs();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                }
                if (out.getName().endsWith(".so")) {
                    // victim later System.load()s it (simulated here).
                    Log.i(TAG, "planted native lib at " + out.getAbsolutePath());
                }
                zis.closeEntry();
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "installUpdate failed", t);
            return false;
        }
    }

    /**
     * Chain #8: dynamic-load RCE. Android 14 requires dynamically-loaded DEX to be
     * read-only; we chmod 444 first, then {@code DexClassLoader}-load an attacker path.
     */
    @Override public boolean loadPlugin(String splitPath) {
        File f = new File(splitPath);
        if (!f.exists()) return false;
        try {
            // Android 14 read-only requirement: writable DEX triggers SELinux denial.
            Os.chmod(splitPath, 0444);
            File opt = new File(ctx().getCodeCacheDir(), "opt");
            opt.mkdirs();
            DexClassLoader cl = new DexClassLoader(
                    splitPath, opt.getAbsolutePath(), null,
                    ctx().getClassLoader());
            // A real app would call a known plugin entry point. For the lab we just
            // resolve the class to prove code load succeeded.
            Class<?> entry = cl.loadClass("com.mochat.plugin.Entry");
            Log.i(TAG, "plugin loaded: " + entry.getName());
            return entry != null;
        } catch (Throwable t) {
            Log.e(TAG, "loadPlugin failed", t);
            return false;
        }
    }
}
