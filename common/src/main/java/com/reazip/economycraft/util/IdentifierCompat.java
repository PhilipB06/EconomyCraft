package com.reazip.economycraft.util;

import java.lang.reflect.Method;

public final class IdentifierCompat {
    private static final Class<?> ID_CLASS;
    private static final Method TRY_PARSE;
    private static final Method WITH_DEFAULT_NAMESPACE;
    private static final Method FROM_NAMESPACE_AND_PATH;
    private static final Method GET_NAMESPACE;
    private static final Method GET_PATH;

    static {
        Class<?> idClass = null;
        Method tryParse = null;
        Method withDefaultNamespace = null;
        Method fromNamespaceAndPath = null;
        Method getNamespace = null;
        Method getPath = null;

        for (String className : new String[] {
                "net.minecraft.resources.Identifier",
                "net.minecraft.resources.ResourceLocation"
        }) {
            try {
                idClass = Class.forName(className);
                tryParse = idClass.getMethod("tryParse", String.class);
                withDefaultNamespace = idClass.getMethod("withDefaultNamespace", String.class);
                fromNamespaceAndPath = idClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                getNamespace = idClass.getMethod("getNamespace");
                getPath = idClass.getMethod("getPath");
                break;
            } catch (ClassNotFoundException ignored) {
                // try next name
            } catch (NoSuchMethodException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        if (idClass == null) {
            throw new ExceptionInInitializerError("Identifier/ResourceLocation class not found");
        }

        ID_CLASS = idClass;
        TRY_PARSE = tryParse;
        WITH_DEFAULT_NAMESPACE = withDefaultNamespace;
        FROM_NAMESPACE_AND_PATH = fromNamespaceAndPath;
        GET_NAMESPACE = getNamespace;
        GET_PATH = getPath;
    }

    private IdentifierCompat() {}

    public static Id tryParse(String input) {
        Object value = invokeStatic(TRY_PARSE, input);
        return value == null ? null : wrap(value);
    }

    public static Id withDefaultNamespace(String path) {
        return wrap(invokeStatic(WITH_DEFAULT_NAMESPACE, path));
    }

    public static Id fromNamespaceAndPath(String namespace, String path) {
        return wrap(invokeStatic(FROM_NAMESPACE_AND_PATH, namespace, path));
    }

    public static Id wrap(Object value) {
        if (value == null) {
            return null;
        }
        String namespace = (String) invoke(GET_NAMESPACE, value);
        String path = (String) invoke(GET_PATH, value);
        return new Id(value, namespace, path);
    }

    @SuppressWarnings("unchecked")
    public static <T> T unwrap(Id id) {
        return id == null ? null : (T) id.handle();
    }

    private static Object invokeStatic(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Id(Object handle, String namespace, String path) {
        public String asString() {
            return namespace + ":" + path;
        }
    }
}
