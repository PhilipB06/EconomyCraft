package com.reazip.economycraft.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

public final class PermissionCompat {
    private static final Method HAS_PERMISSION_INT;
    private static final Method WITH_PERMISSION_INT;
    private static final Method WITH_PERMISSION_SET;
    private static final Predicate<CommandSourceStack> GAMEMASTER_PREDICATE;
    private static final Object OWNER_PERMISSION_SET;

    static {
        Method hasPermissionInt = null;
        Method withPermissionInt = null;
        Method withPermissionSet = null;
        Object ownerPermissionSet = null;

        try {
            hasPermissionInt = CommandSourceStack.class.getMethod("hasPermission", int.class);
        } catch (NoSuchMethodException ignored) {
            // handled below
        }

        try {
            withPermissionInt = CommandSourceStack.class.getMethod("withPermission", int.class);
        } catch (NoSuchMethodException ignored) {
            // handled below
        }

        try {
            withPermissionSet = CommandSourceStack.class.getMethod("withPermission",
                    Class.forName("net.minecraft.server.permissions.PermissionSet"));
        } catch (NoSuchMethodException | ClassNotFoundException ignored) {
            // handled below
        }

        try {
            Class<?> levelBased = Class.forName("net.minecraft.server.permissions.LevelBasedPermissionSet");
            Field ownerField = levelBased.getField("OWNER");
            ownerPermissionSet = ownerField.get(null);
        } catch (ReflectiveOperationException ignored) {
            // handled below
        }

        HAS_PERMISSION_INT = hasPermissionInt;
        WITH_PERMISSION_INT = withPermissionInt;
        WITH_PERMISSION_SET = withPermissionSet;
        OWNER_PERMISSION_SET = ownerPermissionSet;

        GAMEMASTER_PREDICATE = buildGamemasterPredicate();
    }

    private PermissionCompat() {}

    public static Predicate<CommandSourceStack> gamemaster() {
        return GAMEMASTER_PREDICATE;
    }

    public static CommandSourceStack withOwnerPermission(CommandSourceStack source) {
        if (WITH_PERMISSION_INT != null) {
            return (CommandSourceStack) invoke(WITH_PERMISSION_INT, source, 4);
        }
        if (WITH_PERMISSION_SET != null && OWNER_PERMISSION_SET != null) {
            return (CommandSourceStack) invoke(WITH_PERMISSION_SET, source, OWNER_PERMISSION_SET);
        }
        return source;
    }

    private static Predicate<CommandSourceStack> buildGamemasterPredicate() {
        if (HAS_PERMISSION_INT != null) {
            return source -> (boolean) invoke(HAS_PERMISSION_INT, source, 2);
        }
        try {
            Field levelGamemasters = Commands.class.getField("LEVEL_GAMEMASTERS");
            Object check = levelGamemasters.get(null);
            Class<?> permissionCheckClass = Class.forName("net.minecraft.server.permissions.PermissionCheck");
            Method hasPermission = Commands.class.getMethod("hasPermission", permissionCheckClass);
            @SuppressWarnings("unchecked")
            Predicate<CommandSourceStack> predicate = (Predicate<CommandSourceStack>) hasPermission.invoke(null, check);
            return predicate;
        } catch (ReflectiveOperationException ignored) {
            return source -> true;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
