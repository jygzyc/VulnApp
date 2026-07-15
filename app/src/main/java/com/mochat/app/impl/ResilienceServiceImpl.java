package com.mochat.app.impl;

import com.mochat.app.api.svc.IResilienceService;
import com.mochat.app.nbridge.NativeBridge;

/**
 * Resilience service implementation — chain #12.
 *
 * <p>Each gate here is deliberately weak and Frida-bypassable:
 * <ul>
 *   <li>{@link #environmentOk} &rarr; native {@code checkRoot} (su paths + tags).</li>
 *   <li>{@link #debugOk} &rarr; native {@code antiDebug} (reads /proc/self/status TracerPid).</li>
 *   <li>{@link #authenticate} &rarr; event-only biometric; no CryptoObject bound, so a
 *       Frida call to the success callback passes it.</li>
 * </ul>
 * </p>
 */
public final class ResilienceServiceImpl implements IResilienceService {

    @Override public String name() { return "ResilienceService"; }

    @Override public boolean environmentOk() {
        try { return !NativeBridge.checkRoot(); }
        catch (Throwable t) { return true; }       // native not loaded => permissive
    }

    @Override public boolean debugOk() {
        try { return !NativeBridge.antiDebug(); }
        catch (Throwable t) { return true; }
    }

    @Override public boolean authenticate() {
        // Event-only: no CryptoObject, no KeyStore key binding. A Frida hook that just
        // calls the success path bypasses this entirely.
        return authGate();
    }

    /**
     * Stub biometric gate. In the demo app this always succeeds after a "prompt";
     * the lesson is that without {@code setUserAuthenticationRequired(true)} on a
     * KeyStore key, the gate is non-binding.
     */
    private boolean authGate() {
        // Pretend we ran BiometricPrompt and got a SUCCESS callback.
        return true;
    }
}
