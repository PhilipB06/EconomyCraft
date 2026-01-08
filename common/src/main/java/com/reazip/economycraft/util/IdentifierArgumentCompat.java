package com.reazip.economycraft.util;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;

public final class IdentifierArgumentCompat {
    private static final Class<?> ARG_CLASS;
    private static final Method ID_METHOD;
    private static final Method GET_ID_METHOD;

    static {
        Class<?> argClass = null;
        Method idMethod = null;
        Method getIdMethod = null;

        for (String className : new String[] {
                "net.minecraft.commands.arguments.IdentifierArgument",
                "net.minecraft.commands.arguments.ResourceLocationArgument"
        }) {
            try {
                argClass = Class.forName(className);
                idMethod = argClass.getMethod("id");
                getIdMethod = argClass.getMethod("getId", CommandContext.class, String.class);
                break;
            } catch (ClassNotFoundException ignored) {
                // try next name
            } catch (NoSuchMethodException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        if (argClass == null) {
            throw new ExceptionInInitializerError("Identifier/ResourceLocation argument class not found");
        }

        ARG_CLASS = argClass;
        ID_METHOD = idMethod;
        GET_ID_METHOD = getIdMethod;
    }

    private IdentifierArgumentCompat() {}

    public static ArgumentType<?> id() {
        return (ArgumentType<?>) invokeStatic(ID_METHOD);
    }

    public static IdentifierCompat.Id getId(CommandContext<CommandSourceStack> context, String name) {
        Object value = invokeStatic(GET_ID_METHOD, context, name);
        return IdentifierCompat.wrap(value);
    }

    private static Object invokeStatic(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
