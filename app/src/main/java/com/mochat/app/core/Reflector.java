package com.mochat.app.core;

import androidx.annotation.Keep;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Reflective caller for sensitive Android APIs whose names we do not want to appear
 * as cleartext <em>string constants</em> in the decompiled source.
 *
 * <p>Callers pass method/library names as {@code byte[]} (produced by
 * {@code "name".getBytes(UTF_8)}). This class converts them back to Strings at runtime
 * so the literal does not appear as a single cleartext {@code const-string} in smali —
 * it is split across a {@code new-byte-array} + {@code String} constructor. R8 further
 * mangles this class. The training lesson: analysts hook {@link Method#invoke} or the
 * {@code String} constructor to recover the names.</p>
 */
@Keep
public final class Reflector {

    private Reflector() {}

    private static String dec(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Call a boolean setter by name on {@code target}, e.g. WebSettings methods. */
    public static void callBool(Object target, byte[] name, boolean value) {
        try {
            Method m = target.getClass().getMethod(dec(name), boolean.class);
            m.invoke(target, value);
        } catch (Throwable ignored) { }
    }

    /** Call a single-arg (Object) method by name. */
    public static void callObj(Object target, byte[] name, Object arg, Class<?> argType) {
        try {
            Method m = target.getClass().getMethod(dec(name), argType);
            m.invoke(target, arg);
        } catch (Throwable ignored) { }
    }

    /** Call a two-arg (Object, String) method by name, e.g. addJavascriptInterface(obj, name). */
    public static void callObjStr(Object target, byte[] name, Object arg, String strArg) {
        try {
            Method m = target.getClass().getMethod(dec(name), Object.class, String.class);
            m.invoke(target, arg, strArg);
        } catch (Throwable ignored) { }
    }

    /** Load a native library. The name is passed as bytes so it is not a single
     *  cleartext const-string in smali. We call System.loadLibrary directly (not
     *  via reflection) because Runtime.loadLibrary0 uses the caller's class loader
     *  to locate the .so — reflection would break that lookup. */
    public static void loadLibrary(byte[] libName) {
        System.loadLibrary(dec(libName));
    }
}
