package com.mochat.app.core;

import androidx.annotation.Keep;

/**
 * Compile-time-ish string obfuscation helper (Java side).
 *
 * <p>Literals passed to {@link #e(String)} are stored in {@link ServiceRegistry} only
 * in XOR-encrypted form; {@link #s(byte[])} decrypts them at lookup time. The master
 * key comes from the native library so it does not appear in smali. Combined with the
 * reflective {@link ServiceLocator} this severs the static call-graph between the
 * service interfaces and their implementations.</p>
 *
 * <p>The native side additionally hides the master key, so a pure-Jadx dump shows only
 * the encrypted byte arrays. Method names in this class are intentionally terse ({@code s},
 * {@code e}) and the class is annotated {@link Keep @Keep} because it is referenced by
 * name from R8-kept entry points; its <em>internals</em> may still be inlined by R8.</p>
 */
@Keep
public final class Obf {

    private Obf() {}

    /**
     * Decode an obfuscated literal.
     *
     * <p>Keystream is position-keyed: {@code stream[i] = key[i % keyLen] ^ (i & 0xff)}.
     * Encryption and decryption are the same XOR, so {@link #e} and {@link #s} are
     * exact inverses (no rolling/stateful mixing that risks sign-extension bugs).</p>
     *
     * @param enc payload produced by {@link #e(String)}.
     */
    public static String s(byte[] enc) {
        byte[] key = key();
        byte[] out = new byte[enc.length];
        for (int i = 0; i < enc.length; i++) {
            int k = (key[i % key.length] & 0xff) ^ (i & 0xff);
            out[i] = (byte) (enc[i] ^ k);
        }
        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Encrypt a plaintext literal — the inverse of {@link #s(byte[])}. Used by
     * {@link ServiceRegistry} at registration time so the stored bytes are never
     * cleartext.
     */
    public static byte[] e(String plain) {
        byte[] src = plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] key = key();
        byte[] out = new byte[src.length];
        for (int i = 0; i < src.length; i++) {
            int k = (key[i % key.length] & 0xff) ^ (i & 0xff);
            out[i] = (byte) (src[i] ^ k);
        }
        return out;
    }

    /**
     * The master key. Fetched from the native library so it does not appear in smali;
     * a plaintext fallback is used only when the native lib is not loaded yet (unit
     * tests on a plain JVM).
     */
    private static byte[] key() {
        try {
            return com.mochat.app.nbridge.NativeBridge.obfKey();
        } catch (Throwable t) {
            // Native lib not loaded yet (unit tests) — fall back.
            return new byte[] { 'M', 'o', 'C', 'h', 'a', 't' };
        }
    }
}
