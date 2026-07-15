package com.mochat.app.nbridge;

import androidx.annotation.Keep;

/**
 * JNI bridge into {@code libmochat.so}.
 *
 * <p>All native methods are declared here so that R8 keeps them, and so that
 * {@code native <methods>} keep-rules resolve. The native symbols implement:
 * <ul>
 *   <li>{@link #walletEncrypt}/{@link #walletDecrypt} — the deliberately-buggy XOR
 *       routine that powers the wallet (chain #1, #6).</li>
 *   <li>{@link #checkRoot} — root detection (chain #12, Frida bypass).</li>
 *   <li>{@link #antiDebug} — TracerPid debug detection (chain #12).</li>
 *   <li>{@link #obfKey} — the master XOR key for {@link com.mochat.app.core.Obf}.
 *       This is the ONLY place the key appears; it is not in smali.</li>
 * </ul>
 * </p>
 *
 * <p>The library is loaded via {@link com.mochat.app.core.Reflector#loadLibrary} so
 * the literal {@code "mochat"} does not appear in smali.</p>
 */
@Keep
public final class NativeBridge {

    static {
        // Hide the library name from static scanners.
        com.mochat.app.core.Reflector.loadLibrary(new byte[] { 'm', 'o', 'c', 'h', 'a', 't' });
    }

    private NativeBridge() {}

    /** XOR-encrypt (buggy: every byte XORed only with last key char). */
    public static native byte[] walletEncrypt(byte[] data, byte[] key);

    /** XOR-decrypt — the oracle used by the exported WalletService (chain #6). */
    public static native byte[] walletDecrypt(byte[] data, byte[] key);

    /** Root detection: scans for {@code su} binaries and checks Build.TAGS. */
    public static native boolean checkRoot();

    /** Anti-debug: reads /proc/self/status TracerPid; returns true if a tracer is attached. */
    public static native boolean antiDebug();

    /** Master XOR key for Obf (the only place the key appears). */
    public static native byte[] obfKey();
}
