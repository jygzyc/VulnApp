package com.mochat.app.core;

import androidx.annotation.Keep;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Service locator: resolves an {@link IMochatService} interface to a concrete,
 * instantiated implementation via reflection against {@link ServiceRegistry}.
 *
 * <p>This is the single chokepoint at which every business call passes. Because the
 * implementation class name is decrypted only here, and because callers receive a
 * {@link java.lang.reflect.Proxy} (see {@link ServiceProxy}), the static call-graph
 * is severed: a Jadx cross-reference of {@code IPaymentService#pay} yields zero
 * direct implementors, and the only reachable concrete code is the reflective
 * {@code invoke} inside {@link ServiceHandler}.</p>
 *
 * <p>Analyst escalation points (documented as chain hints, not hidden):
 * <ul>
 *   <li>Hook {@code ServiceLocator.get} or {@code Class.forName} to dump impl names.</li>
 *   <li>Hook {@link ServiceHandler#invoke} to intercept every business call centrally.</li>
 * </ul>
 * </p>
 */
@Keep
public final class ServiceLocator {

    /** Singleton cache: interface &rarr; proxied instance. */
    private static final Map<Class<?>, Object> CACHE = new HashMap<>();

    private ServiceLocator() {}

    /**
     * @param iface a service interface from {@code com.mochat.app.api.svc}.
     * @return a proxy implementing the interface. Never returns the raw impl.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> iface) {
        synchronized (CACHE) {
            Object cached = CACHE.get(iface);
            if (cached != null) return (T) cached;
        }

        String implFqn = ServiceRegistry.implFor(iface);
        if (implFqn == null) {
            throw new IllegalStateException("no impl registered for " + iface.getName());
        }

        // Reflective instantiation — the only place a concrete impl class is named.
        Object impl;
        try {
            Class<?> clazz = Class.forName(implFqn);
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            impl = ctor.newInstance();
        } catch (Throwable t) {
            throw new IllegalStateException("cannot load " + implFqn, t);
        }

        // Wrap in a Proxy so call-sites never hold an Impl reference directly.
        Object proxy = ServiceProxy.wrap(iface, impl);
        synchronized (CACHE) {
            CACHE.put(iface, proxy);
        }
        return (T) proxy;
    }
}
