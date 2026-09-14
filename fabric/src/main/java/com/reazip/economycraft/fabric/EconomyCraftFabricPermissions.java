package com.reazip.economycraft.fabric;

import com.reazip.economycraft.util.EconomyPermissions;
import net.minecraft.commands.SharedSuggestionProvider;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class EconomyCraftFabricPermissions {
    private EconomyCraftFabricPermissions() {}

    public static void install() {
        MethodHandle check = resolveCheckHandle();
        if (check == null) return;
        EconomyPermissions.setBackend((source, node, fallback) -> {
            try {
                return (boolean) check.invoke(source, node, fallback);
            } catch (Throwable t) {
                return fallback;
            }
        });
    }

    private static MethodHandle resolveCheckHandle() {
        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            return MethodHandles.publicLookup().findStatic(permissionsClass, "check",
                    MethodType.methodType(boolean.class, SharedSuggestionProvider.class, String.class, boolean.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
