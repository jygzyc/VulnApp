package com.mochat.app.core;

import androidx.annotation.Keep;

import java.lang.reflect.Proxy;

/**
 * Proxy factory: wraps a service implementation in a {@link java.lang.reflect.Proxy}
 * backed by {@link ServiceHandler}.
 *
 * <p>Why a proxy? Two reasons:
 * <ol>
 *   <li><b>Frida-proofing.</b> Analysts naturally hook the concrete impl methods
 *       (e.g. {@code PaymentServiceImpl.pay}). But the call-site never holds an Impl
 *       reference — it holds a Proxy. Hooking the impl is therefore a no-op unless
 *       the analyst first discovers the impl class name (via {@link ServiceRegistry})
 *       and hooks the reflective dispatch in {@link ServiceHandler}.</li>
 *   <li><b>Central interception.</b> Every business call funnels through
 *       {@link ServiceHandler#invoke}, which is the single point where logging,
 *       anti-tamper checks, or response mutation could be inserted. (We keep it
 *       thin in the training app; the <em>absence</em> of validation here is itself
 *       part of the lesson — the proxy could enforce caller auth but does not.)</li>
 * </ol>
 * </p>
 */
@Keep
public final class ServiceProxy {

    private ServiceProxy() {}

    /**
     * @param iface the service interface
     * @param impl  the concrete implementation instance
     * @return a Proxy implementing {@code iface}; all method calls route through
     *         {@link ServiceHandler#invoke}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> iface, Object impl) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[] { iface },
                new ServiceHandler(impl));
    }
}
