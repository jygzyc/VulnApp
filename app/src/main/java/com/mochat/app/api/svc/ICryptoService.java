package com.mochat.app.api.svc;

import com.mochat.app.api.IMochatService;

/**
 * Crypto service (chain #6 decryption oracle).
 *
 * <p>Wraps the native XOR {@code encrypt}/{@code decrypt} routines so that the
 * exported {@code WalletService} Messenger handler can offer a decryption oracle
 * without exposing the JNI symbols directly.</p>
 */
public interface ICryptoService extends IMochatService {

    /** XOR-encrypt a payload with the (buggy) native routine. */
    byte[] encrypt(byte[] data);

    /** XOR-decrypt a payload — the oracle. */
    byte[] decrypt(byte[] data);

    /** Returns the native key bytes after the obfuscated native call. */
    byte[] key();
}
