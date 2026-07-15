package com.mochat.app.core;

import androidx.annotation.Keep;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * InvocationHandler for every MoChat service proxy.
 *
 * <p>Every call to a service interface method lands here. We then reflectively find
 * the matching public method on the <em>implementation</em> and invoke it. This means:
 * <ul>
 *   <li>The interface method (e.g. {@code IPaymentService.pay}) and the impl method
 *       ({@code PaymentServiceImpl.pay}) are <b>only</b> linked at runtime via
 *       {@link Method#invoke}; there is no compile-time edge.</li>
 *   <li>An analyst who hooks only the interface sees the call; one who hooks only the
 *       impl does not — unless they hook {@link #invoke} (or {@link Method#invoke}).</li>
 * </ul>
 * </p>
 *
 * <p>This class also demonstrates a <em>vulnerable</em> interceptor pattern: a proxy is
 * the ideal place to enforce caller / context checks, but we deliberately perform
 * none. The {@code WebViewBridge} proxy, for instance, is registered via
 * {@code addJavascriptInterface} and reaches straight through to {@code exec()}.</p>
 */
@Keep
public final class ServiceHandler implements InvocationHandler {

    private final Object impl;

    ServiceHandler(Object impl) {
        this.impl = impl;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Resolve the same-named method on the concrete implementation class.
        // NOTE: we look up on the *impl* class, not the interface, so private/internal
        // helpers cannot be reached this way — but public service methods can. This is
        // the dispatch the analyst must intercept.
        Method target;
        try {
            target = impl.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            // Fall back to declared (incl. non-public) methods if the impl uses a
            // different visibility. This widens the reflective surface intentionally
            // so that impl classes can mark their real logic private and still be
            // reachable from the proxy — a pattern that confuses Jadx readers.
            target = impl.getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
            target.setAccessible(true);
        }
        try {
            return target.invoke(impl, args);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getCause() != null ? ite.getCause() : ite;
        }
    }
}
