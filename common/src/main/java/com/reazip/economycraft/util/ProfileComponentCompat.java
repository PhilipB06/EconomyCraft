package com.reazip.economycraft.util;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.world.item.component.ResolvableProfile;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.UUID;

public final class ProfileComponentCompat {
    private static final boolean IS_ABSTRACT = Modifier.isAbstract(ResolvableProfile.class.getModifiers());
    private static final Method CREATE_RESOLVED = findResolvedFactory();
    private static final Method CREATE_UNRESOLVED_STRING = findMethod("createUnresolved", String.class);
    private static final Method CREATE_UNRESOLVED_UUID = findMethod("createUnresolved", UUID.class);
    private static final Constructor<ResolvableProfile> CTOR_GAME_PROFILE = findConstructor(GameProfile.class);
    private static final Constructor<ResolvableProfile> CTOR_FULL = findConstructor(Optional.class, Optional.class, PropertyMap.class);
    private static final Constructor<ResolvableProfile> CTOR_FULL_WITH_PROFILE = findConstructor(
            Optional.class, Optional.class, PropertyMap.class, GameProfile.class);
    private static final Constructor<PropertyMap> PROPERTYMAP_NOARG = findPropertyMapConstructor();
    private static final Constructor<PropertyMap> PROPERTYMAP_MULTIMAP = findPropertyMapConstructor(Multimap.class);

    private ProfileComponentCompat() {}

    public static ResolvableProfile resolved(GameProfile profile) {
        if (CREATE_RESOLVED != null) {
            return invokeStatic(CREATE_RESOLVED, profile);
        }
        if (!IS_ABSTRACT) {
            if (CTOR_GAME_PROFILE != null) {
                return newInstance(CTOR_GAME_PROFILE, profile);
            }
            if (CTOR_FULL_WITH_PROFILE != null) {
                return newInstance(CTOR_FULL_WITH_PROFILE,
                        Optional.ofNullable(extractName(profile)),
                        Optional.ofNullable(extractId(profile)),
                        profile.getProperties(),
                        profile);
            }
        }
        throw new IllegalStateException("No compatible ResolvableProfile factory found for resolved profile");
    }

    public static ResolvableProfile resolvedOrUnresolved(GameProfile profile) {
        return tryResolvedOrUnresolved(profile)
                .orElseThrow(() -> new IllegalStateException("No compatible ResolvableProfile factory found"));
    }

    public static Optional<ResolvableProfile> tryResolvedOrUnresolved(GameProfile profile) {
        try {
            return Optional.of(resolved(profile));
        } catch (IllegalStateException e) {
            String name = extractName(profile);
            if (name != null && !name.isBlank()) {
                return tryUnresolved(name);
            }
            UUID id = extractId(profile);
            return tryUnresolved(id != null ? id.toString() : "");
        }
    }

    public static Optional<ResolvableProfile> tryUnresolved(String nameOrId) {
        try {
            return Optional.of(unresolved(nameOrId));
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    public static ResolvableProfile unresolved(String nameOrId) {
        String value = nameOrId == null || nameOrId.isBlank() ? "" : nameOrId;
        if (CREATE_UNRESOLVED_STRING != null) {
            return invokeStatic(CREATE_UNRESOLVED_STRING, value);
        }
        if (CREATE_UNRESOLVED_UUID != null) {
            try {
                return invokeStatic(CREATE_UNRESOLVED_UUID, UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Fall back to other strategies if the value is not a UUID
            }
        }
        if (!IS_ABSTRACT && CTOR_FULL != null) {
            return newInstance(CTOR_FULL,
                    Optional.of(value),
                    Optional.empty(),
                    newPropertyMap());
        }
        throw new IllegalStateException("No compatible ResolvableProfile factory found for unresolved profile");
    }

    private static String extractName(GameProfile profile) {
        try {
            Method m = profile.getClass().getMethod("name");
            return (String) m.invoke(profile);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method m = profile.getClass().getMethod("getName");
                return (String) m.invoke(profile);
            } catch (ReflectiveOperationException ignoredToo) {
                try {
                    RecordComponent rc = profile.getClass().getRecordComponents() != null
                            ? java.util.Arrays.stream(profile.getClass().getRecordComponents())
                            .filter(c -> c.getName().equals("name"))
                            .findFirst().orElse(null)
                            : null;
                    if (rc != null) {
                        return (String) rc.getAccessor().invoke(profile);
                    }
                } catch (ReflectiveOperationException ignoredThree) {
                    // fall through
                }
                return null;
            }
        }
    }

    private static UUID extractId(GameProfile profile) {
        try {
            Method m = profile.getClass().getMethod("id");
            return (UUID) m.invoke(profile);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method m = profile.getClass().getMethod("getId");
                return (UUID) m.invoke(profile);
            } catch (ReflectiveOperationException ignoredToo) {
                try {
                    RecordComponent rc = profile.getClass().getRecordComponents() != null
                            ? java.util.Arrays.stream(profile.getClass().getRecordComponents())
                            .filter(c -> c.getName().equals("id"))
                            .findFirst().orElse(null)
                            : null;
                    if (rc != null) {
                        return (UUID) rc.getAccessor().invoke(profile);
                    }
                } catch (ReflectiveOperationException ignoredThree) {
                    // fall through
                }
                return null;
            }
        }
    }

    private static PropertyMap newPropertyMap() {
        if (PROPERTYMAP_NOARG != null) {
            try {
                return PROPERTYMAP_NOARG.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to construct PropertyMap via no-arg constructor", e);
            }
        }
        if (PROPERTYMAP_MULTIMAP != null) {
            try {
                return PROPERTYMAP_MULTIMAP.newInstance(LinkedHashMultimap.create());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to construct PropertyMap via Multimap constructor", e);
            }
        }
        throw new IllegalStateException("No compatible PropertyMap constructor found");
    }

    private static ResolvableProfile invokeStatic(Method method, Object... args) {
        try {
            return (ResolvableProfile) method.invoke(null, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke ResolvableProfile factory: " + method.getName(), e);
        }
    }

    private static ResolvableProfile newInstance(Constructor<ResolvableProfile> ctor, Object... args) {
        try {
            return ctor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to create ResolvableProfile via constructor: " + ctor, e);
        }
    }

    private static Method findMethod(String name, Class<?>... params) {
        try {
            return ResolvableProfile.class.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findResolvedFactory() {
        Method method = findMethod("createResolved", GameProfile.class);
        if (method != null) {
            return method;
        }
        for (Method candidate : ResolvableProfile.class.getMethods()) {
            if (!Modifier.isStatic(candidate.getModifiers())) {
                continue;
            }
            if (!ResolvableProfile.class.isAssignableFrom(candidate.getReturnType())) {
                continue;
            }
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 1 && params[0].isAssignableFrom(GameProfile.class)) {
                return candidate;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Constructor<ResolvableProfile> findConstructor(Class<?>... params) {
        try {
            return (Constructor<ResolvableProfile>) ResolvableProfile.class.getConstructor(params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Constructor<PropertyMap> findPropertyMapConstructor(Class<?>... params) {
        try {
            @SuppressWarnings("unchecked")
            Constructor<PropertyMap> ctor = (Constructor<PropertyMap>) PropertyMap.class.getConstructor(params);
            return ctor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
