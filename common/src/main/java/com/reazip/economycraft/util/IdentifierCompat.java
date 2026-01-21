package com.reazip.economycraft.util;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

public final class IdentifierCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Class<?> ID_CLASS;
    private static final Constructor<?> ID_CONSTRUCTOR_TWO;
    private static final Constructor<?> ID_CONSTRUCTOR_ONE;
    private static final Method ID_FACTORY_ONE;
    private static final Method ID_FACTORY_TWO;
    private static final Method REGISTRY_CONTAINS_KEY;
    private static final Method REGISTRY_GET_OPTIONAL;
    private static final Method RESOURCE_KEY_CREATE;
    private static final Method RESOURCE_KEY_IDENTIFIER;
    private static final Method HOLDER_VALUE;
    private static final Method EITHER_LEFT;
    private static final Method EITHER_RIGHT;

    static {
        Class<?> idClass = null;
        Constructor<?> idConstructorTwo = null;
        Constructor<?> idConstructorOne = null;
        Method idFactoryOne = null;
        Method idFactoryTwo = null;
        Method registryContainsKey = null;
        Method registryGetOptional = null;
        Method resourceKeyCreate = null;
        Method resourceKeyIdentifier = null;
        Method holderValue = null;
        Method eitherLeft = null;
        Method eitherRight = null;
        Object sample = BuiltInRegistries.ITEM.getKey(Items.AIR);
        if (sample == null) {
            throw new ExceptionInInitializerError("Identifier sample not found");
        }

        Object idSample = extractIdentifierSample(sample);
        if (idSample == null) {
            throw new ExceptionInInitializerError("Identifier sample not found");
        }

        idClass = idSample.getClass();
        for (Constructor<?> constructor : idClass.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                idConstructorTwo = constructor;
            } else if (params.length == 1 && params[0] == String.class) {
                idConstructorOne = constructor;
            }
        }
        for (Method method : idClass.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!idClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == String.class) {
                idFactoryOne = method;
            } else if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                idFactoryTwo = method;
            }
        }
        if (idConstructorTwo == null && idConstructorOne == null && idFactoryOne == null && idFactoryTwo == null) {
            throw new ExceptionInInitializerError("Identifier constructor not found");
        }

        registryContainsKey = findRegistryMethod(Registry.class, boolean.class, idClass);
        registryGetOptional = findRegistryMethod(Registry.class, Optional.class, idClass);
        resourceKeyCreate = findResourceKeyCreate(idClass);
        resourceKeyIdentifier = findResourceKeyIdentifier(idClass);
        holderValue = findHolderValue();
        Method[] eitherMethods = findEitherMethods();
        if (eitherMethods != null) {
            eitherLeft = eitherMethods[0];
            eitherRight = eitherMethods[1];
        }

        ID_CLASS = idClass;
        ID_CONSTRUCTOR_TWO = idConstructorTwo;
        ID_CONSTRUCTOR_ONE = idConstructorOne;
        ID_FACTORY_ONE = idFactoryOne;
        ID_FACTORY_TWO = idFactoryTwo;
        REGISTRY_CONTAINS_KEY = registryContainsKey;
        REGISTRY_GET_OPTIONAL = registryGetOptional;
        RESOURCE_KEY_CREATE = resourceKeyCreate;
        RESOURCE_KEY_IDENTIFIER = resourceKeyIdentifier;
        HOLDER_VALUE = holderValue;
        EITHER_LEFT = eitherLeft;
        EITHER_RIGHT = eitherRight;
    }

    private IdentifierCompat() {}

    public static Id tryParse(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String namespace;
        String path;
        int idx = trimmed.indexOf(':');
        if (idx >= 0) {
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        } else {
            namespace = "minecraft";
            path = trimmed;
        }
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            return null;
        }
        return new Id(construct(namespace, path), namespace, path);
    }

    public static Id withDefaultNamespace(String path) {
        if (!isValidPath(path)) {
            return null;
        }
        return new Id(construct("minecraft", path), "minecraft", path);
    }

    public static Id fromNamespaceAndPath(String namespace, String path) {
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            return null;
        }
        return new Id(construct(namespace, path), namespace, path);
    }

    public static Id wrap(Object value) {
        if (value == null) {
            return null;
        }
        return parseFromString(value.toString(), value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T unwrap(Id id) {
        return id == null ? null : (T) id.handle();
    }

    public static boolean registryContainsKey(Registry<?> registry, Id id) {
        if (id == null) {
            return false;
        }
        return (boolean) invoke(REGISTRY_CONTAINS_KEY, registry, id.handle());
    }

    public static <T> Optional<T> registryGetOptional(Registry<T> registry, Id id) {
        if (id == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Optional<Object> result = (Optional<Object>) invoke(REGISTRY_GET_OPTIONAL, registry, id.handle());
        if (result.isEmpty()) {
            return Optional.empty();
        }
        Object value = unwrapRegistryValue(result.get());
        if (value == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        T direct;
        try {
            direct = (T) value;
        } catch (ClassCastException e) {
            LOGGER.error("[EconomyCraft] Registry lookup for {} returned unexpected value {} (class {}) from {}",
                    id.asString(), value, value.getClass().getName(), registry, e);
            return Optional.empty();
        }
        return Optional.of(direct);
    }

    public static <T> ResourceKey<T> createResourceKey(ResourceKey<? extends Registry<T>> registryKey, Id id) {
        if (id == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        ResourceKey<T> result = (ResourceKey<T>) invokeStatic(RESOURCE_KEY_CREATE, registryKey, id.handle());
        return result;
    }

    public static Id fromResourceKey(ResourceKey<?> key) {
        if (key == null) {
            return null;
        }
        Object value = invoke(RESOURCE_KEY_IDENTIFIER, key);
        return wrap(value);
    }

    private static Id parseFromString(String raw, Object handle) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String namespace;
        String path;
        int idx = raw.indexOf(':');
        if (idx >= 0) {
            namespace = raw.substring(0, idx);
            path = raw.substring(idx + 1);
        } else {
            namespace = "minecraft";
            path = raw;
        }
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            return null;
        }
        return new Id(handle, namespace, path);
    }

    private static Object construct(String namespace, String path) {
        String combined = namespace + ":" + path;
        try {
            if (ID_CONSTRUCTOR_TWO != null) {
                return ID_CONSTRUCTOR_TWO.newInstance(namespace, path);
            }
            if (ID_FACTORY_TWO != null) {
                return ID_FACTORY_TWO.invoke(null, namespace, path);
            }
            if (ID_CONSTRUCTOR_ONE != null) {
                return ID_CONSTRUCTOR_ONE.newInstance(combined);
            }
            if (ID_FACTORY_ONE != null) {
                return ID_FACTORY_ONE.invoke(null, combined);
            }
            throw new IllegalStateException("Identifier constructor not found");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object unwrapRegistryValue(Object value) {
        if (value == null) return null;
        Object v = value;

        while (v instanceof Holder<?> h) {
            v = h.value();
            if (v == null) return null;
        }

        if (EITHER_LEFT != null && EITHER_RIGHT != null && isEither(v)) {
            Optional<?> left = invokeEitherOptional(EITHER_LEFT, v);
            if (left != null && left.isPresent()) return unwrapRegistryValue(left.get());

            Optional<?> right = invokeEitherOptional(EITHER_RIGHT, v);
            if (right != null && right.isPresent()) return unwrapRegistryValue(right.get());

            return null;
        }

        return v;
    }

    private static boolean isEither(Object value) {
        return value != null && value.getClass().getName().equals("com.mojang.datafixers.util.Either");
    }

    @SuppressWarnings("unchecked")
    private static Optional<?> invokeEitherOptional(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        Object result = invoke(method, target);
        if (result instanceof Optional<?> optional) {
            return optional;
        }
        return null;
    }

    private static boolean isValidNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return false;
        }
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || c == '/' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static Method findRegistryMethod(Class<?> registryClass, Class<?> returnType, Class<?> idClass) {
        Method assignableMatch = null;
        for (Method method : registryClass.getMethods()) {
            if (method.getParameterCount() != 1 || !returnType.equals(method.getReturnType())) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (param.equals(idClass)) {
                return method;
            }
            if (param.isAssignableFrom(idClass)) {
                assignableMatch = method;
            }
        }
        if (assignableMatch != null) {
            return assignableMatch;
        }
        throw new ExceptionInInitializerError("Registry method not found");
    }

    private static Method findNoArgMethod(Class<?> type, String... names) {
        for (String name : names) {
            Method method = null;
            try {
                method = type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // try declared method instead
            }
            if (method == null) {
                try {
                    method = type.getDeclaredMethod(name);
                    method.setAccessible(true);
                } catch (NoSuchMethodException ignored) {
                    // try next name
                } catch (RuntimeException ignored) {
                    // access failure; try next name
                }
            }
            if (method == null) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(void.class)
                    || returnType.equals(boolean.class)
                    || Optional.class.isAssignableFrom(returnType)
                    || ResourceKey.class.isAssignableFrom(returnType)) {
                continue;
            }
            return method;
        }
        return null;
    }

    private static Method findResourceKeyCreate(Class<?> idClass) {
        for (Method method : ResourceKey.class.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterCount() == 2 && ResourceKey.class.equals(method.getReturnType())) {
                Class<?>[] params = method.getParameterTypes();
                if (ResourceKey.class.equals(params[0]) && params[1].isAssignableFrom(idClass)) {
                    return method;
                }
            }
        }
        throw new ExceptionInInitializerError("ResourceKey.create method not found");
    }

    private static Method findResourceKeyIdentifier(Class<?> idClass) {
        for (Method method : ResourceKey.class.getMethods()) {
            if (method.getParameterCount() == 0 && idClass.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        throw new ExceptionInInitializerError("ResourceKey identifier method not found");
    }

    private static Method findHolderValue() {
        return findNoArgMethod(Holder.class, "value", "get");
    }

    private static Method[] findEitherMethods() {
        try {
            Class<?> eitherClass = Class.forName("com.mojang.datafixers.util.Either");
            Method left = eitherClass.getMethod("left");
            Method right = eitherClass.getMethod("right");
            return new Method[] { left, right };
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    private static Object extractIdentifierSample(Object sample) {
        if (sample == null) {
            return null;
        }
        Class<?> sampleClass = sample.getClass();
        for (String methodName : new String[] {"location", "identifier"}) {
            try {
                Method method = sampleClass.getMethod(methodName);
                Object id = method.invoke(sample);
                if (id != null) {
                    return id;
                }
            } catch (ReflectiveOperationException ignored) {
                // try next name
            }
        }
        return sample;
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
