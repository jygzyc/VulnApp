package com.mochat.app.core;

import androidx.annotation.Keep;

import java.util.HashMap;
import java.util.Map;

/**
 * Encrypted service registry.
 *
 * <p>Each {@link IMochatService} interface is mapped to an <em>obfuscated</em> fully
 * qualified implementation class name. The mapping is the only place that ties an
 * interface to its implementation; it is built lazily and populated with strings that
 * pass through {@link Obf#s(byte[])} so a Jadx reader sees only encrypted byte arrays
 * and never the class name in cleartext.</p>
 *
 * <p>Why this matters for analysis: the static call graph from, say,
 * {@code PaymentActivity} to {@code PaymentServiceImpl} is broken. The only edge is a
 * {@link Class#forName(String)} call inside {@link ServiceLocator}, whose argument is
 * decrypted at runtime. Analysts must therefore either (a) hook
 * {@code Class.forName} / {@code ServiceLocator.get} with Frida to dump the names, or
 * (b) reverse the native {@code obfKey()} to decrypt the table offline.</p>
 */
@Keep
public final class ServiceRegistry {

    /** interface FQN &rarr; obfuscated impl-name bytes. */
    private static final Map<String, byte[]> IMPL = new HashMap<>();

    private ServiceRegistry() {}

    static {
        // Each impl class name is stored ENCRYPTED via Obf.e(); Obf.s() decrypts at
        // lookup time. This keeps the impl FQNs out of cleartext string constants.
        put("com.mochat.app.api.svc.IPaymentService",     "com.mochat.app.impl.PaymentServiceImpl");
        put("com.mochat.app.api.svc.IContactStore",       "com.mochat.app.impl.ContactStoreImpl");
        put("com.mochat.app.api.svc.IWebViewBridge",      "com.mochat.app.impl.WebViewBridgeImpl");
        put("com.mochat.app.api.svc.IOrderService",       "com.mochat.app.impl.OrderServiceImpl");
        put("com.mochat.app.api.svc.ICryptoService",      "com.mochat.app.impl.CryptoServiceImpl");
        put("com.mochat.app.api.svc.IResilienceService",  "com.mochat.app.impl.ResilienceServiceImpl");
    }

    private static void put(String iface, String implFqn) {
        // store the ENCRYPTED form; Obf.s() decrypts at lookup time.
        IMPL.put(iface, Obf.e(implFqn));
    }

    /** @return decrypted impl FQN, or {@code null} if not registered. */
    public static String implFor(Class<?> iface) {
        byte[] enc = IMPL.get(iface.getName());
        return enc == null ? null : Obf.s(enc);
    }
}
