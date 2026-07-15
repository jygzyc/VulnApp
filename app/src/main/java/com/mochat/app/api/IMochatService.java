package com.mochat.app.api;

/**
 * Root interface for every MoChat service.
 *
 * <p>Anti-analysis rationale: all business logic is reached through this interface.
 * Concrete implementations live in {@code com.mochat.app.impl.*} and are resolved at
 * runtime by {@link com.mochat.app.core.ServiceLocator} via reflection against an
 * encrypted registry. A static call-graph in jadx therefore terminates at
 * {@code ServiceLocator.get(...)} and never shows interface&rarr;implementation edges.
 * Frida hooks placed on the interface methods are useless because callers receive a
 * {@link java.lang.reflect.Proxy} from {@link com.mochat.app.core.ServiceProxy}, whose
 * {@link com.mochat.app.core.ServiceHandler} dispatches every call reflectively.</p>
 */
public interface IMochatService {
    /** Human-readable name, mainly for logging. */
    String name();
}
