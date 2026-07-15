package com.mochat.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pure-JVM smoke test for the reflection/proxy core (no Android deps).
 *
 * <p>This does NOT exercise Android code; it validates the service-locator plumbing
 * that the chains depend on. Run via {@code ./gradlew test}.</p>
 */
public class ServiceLocatorTest {

    /** A throwaway test interface separate from the app's own api package. */
    public interface ITest {
        String echo(String s);
        int    add(int a, int b);
    }

    public static class TestImpl implements ITest {
        public String echo(String s) { return s; }
        public int add(int a, int b) { return a + b; }
    }

    @Test
    public void proxyDispatchesReflectively() {
        // Register a test binding by poking the registry directly via reflection,
        // so this test does not depend on app-specific impls.
        try {
            java.lang.reflect.Method put = ServiceRegistry.class
                    .getDeclaredMethod("put", String.class, String.class);
            put.setAccessible(true);
            put.invoke(null, ITest.class.getName(), TestImpl.class.getName());
        } catch (Exception e) {
            fail("registry setup failed: " + e);
        }

        ITest svc = ServiceLocator.get(ITest.class);
        assertNotNull("locator must return a non-null proxy", svc);
        assertTrue("returned object must be a Proxy",
                java.lang.reflect.Proxy.isProxyClass(svc.getClass()));

        assertEquals("echo", svc.echo("echo"));
        assertEquals(7, svc.add(3, 4));
    }
}
