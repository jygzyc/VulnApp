package com.mochat.app.api.svc;

import com.mochat.app.api.IMochatService;

/**
 * Resilience / hardening service (chain #12).
 *
 * <p>Wraps native root detection, anti-debug, and the (event-only) biometric gate.
 * The interface keeps these names benign so grep'ing for {@code su}/{@code TracerPid}
 * in smali yields nothing — the real strings live in {@code OBFUSCATE} literals and
 * in the native library.</p>
 */
public interface IResilienceService extends IMochatService {

    /** Returns {@code true} if the device looks "clean". Frida-flippable. */
    boolean environmentOk();

    /** Anti-debug check (reads /proc/self/status TracerPid). Frida-flippable. */
    boolean debugOk();

    /**
     * Biometric gate. The vulnerable impl is <em>event-only</em>: it returns
     * {@code true} on any success callback without a bound CryptoObject, so a Frida
     * invocation of the callback bypasses it.
     */
    boolean authenticate();
}
