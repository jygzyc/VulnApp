package com.mochat.app.impl;

import com.mochat.app.api.svc.ICryptoService;
import com.mochat.app.nbridge.NativeBridge;

/**
 * Crypto service implementation.
 *
 * <p>The interface declares {@link #encrypt}/{@link #decrypt}/{@link #key}; the real
 * work is delegated to {@link NativeBridge} (the deliberately-buggy XOR). The exported
 * {@code WalletService} Messenger handler exposes this as a decryption oracle, which is
 * chain #6.</p>
 */
public final class CryptoServiceImpl implements ICryptoService {

    @Override public String name() { return "CryptoService"; }

    @Override public byte[] encrypt(byte[] data) {
        return NativeBridge.walletEncrypt(data, key());
    }

    @Override public byte[] decrypt(byte[] data) {
        // Oracle entry point — any caller of the exported WalletService can decrypt
        // arbitrary blobs once they hold the key (which is stored plaintext).
        return NativeBridge.walletDecrypt(data, key());
    }

    @Override public byte[] key() {
        // Key comes from native (obfKey) — the only place it appears.
        return NativeBridge.obfKey();
    }
}
