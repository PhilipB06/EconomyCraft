package com.reazip.economycraft.util;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * ClickEvent helper for 1.21.x (Fabric/NeoForge).
 * Creates RUN_COMMAND ClickEvents across mapping/API changes.
 */
public final class ChatCompat {
    private ChatCompat() {}

    // ---- Caches (filled lazily on first use) --------------------------------
    private static volatile boolean scanned = false;

    private static volatile Method factoryMethod;                 // (String) -> ClickEvent
    private static volatile Class<?> actionEnumType;              // ClickEvent.Action (or obfuscated)
    private static volatile Object runCommandEnum;                // RUN_COMMAND constant
    private static volatile Constructor<?> enumCtorString;        // ClickEvent(Action, String)
    private static volatile Constructor<?> enumCtorComponent;     // ClickEvent(Action, Component)
    private static volatile Constructor<?> nestedCtorString;      // ClickEvent$RunCommand(String) or similar
    private static volatile Constructor<?> nestedCtorComponent;   // ClickEvent$RunCommand(Component) or similar

    /**
     * Build a RUN_COMMAND ClickEvent for the given command string.
     * Returns null if no compatible shape exists.
     */
    public static ClickEvent runCommandEvent(String cmd) {
        ensureScanned();

        // A) Preferred: static factory (String) -> ClickEvent
        try {
            if (factoryMethod != null) {
                Object ev = factoryMethod.invoke(null, cmd);
                if (ev instanceof ClickEvent ce && isRunCommand(ce)) {
                    return ce;
                }
            }
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] f1");
        }

        // B) Ctor with Action enum
        try {
            if (runCommandEnum != null) {
                if (enumCtorString != null) {
                    Object ev = enumCtorString.newInstance(runCommandEnum, cmd);
                    if (ev instanceof ClickEvent ce && isRunCommand(ce)) {
                        return ce;
                    }
                }
                if (enumCtorComponent != null) {
                    Object ev = enumCtorComponent.newInstance(runCommandEnum, Component.literal(cmd));
                    if (ev instanceof ClickEvent ce && isRunCommand(ce)) {
                        return ce;
                    }
                }
            }
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] f2");
        }

        // C) Nested-class fallback
        try {
            if (nestedCtorString != null) {
                Object ev = nestedCtorString.newInstance(cmd);
                if (ev instanceof ClickEvent ce && isRunCommand(ce)) {
                    return ce;
                }
            }
            if (nestedCtorComponent != null) {
                Object ev = nestedCtorComponent.newInstance(Component.literal(cmd));
                if (ev instanceof ClickEvent ce && isRunCommand(ce)) {
                    return ce;
                }
            }
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] f3");
        }

        return null;
    }

    // ---- Guaranteed fallback ------------------------------------------------

    /**
     * Guaranteed clickable RUN_COMMAND via /tellraw.
     * Use when runCommandEvent(...) returns null.
     */
    public static void sendRunCommandTellraw(ServerPlayer target, String prefixText, String labelText, String cmd) {
        try {
            String escCmd = cmd.replace("\\", "\\\\").replace("\"", "\\\"");
            String escPrefix = prefixText.replace("\\", "\\\\").replace("\"", "\\\"");
            String escLabel = labelText.replace("\\", "\\\\").replace("\"", "\\\"");
            String json = "{\"text\":\"" + escPrefix + "\",\"color\":\"yellow\",\"extra\":[{\"text\":\"" + escLabel +
                    "\",\"underlined\":true,\"color\":\"green\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" +
                    escCmd + "\"}}]}";

            String selector = target.getScoreboardName(); // safe
            String line = "tellraw " + selector + " " + json;

            var srv = target.getServer();
            srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withPermission(4), line);
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] tw");
        }
    }

    // ---- Scan & helpers -----------------------------------------------------

    private static void ensureScanned() {
        if (scanned) return;
        synchronized (ChatCompat.class) {
            if (scanned) return;
            try {
                // A) Find public static factory: (String) -> ClickEvent
                for (Method m : ClickEvent.class.getDeclaredMethods()) {
                    int mod = m.getModifiers();
                    if (Modifier.isPublic(mod) && Modifier.isStatic(mod)
                            && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class
                            && ClickEvent.class.isAssignableFrom(m.getReturnType())) {
                        factoryMethod = m;
                        break;
                    }
                }

                // B) Find Action enum + RUN_COMMAND & match constructors
                for (Class<?> nested : ClickEvent.class.getDeclaredClasses()) {
                    if (nested.isEnum()) {
                        Object rc = enumConstantIgnoreCase(nested, "RUN_COMMAND");
                        if (rc != null) {
                            actionEnumType = nested;
                            runCommandEnum = rc;
                            break;
                        }
                    }
                }

                for (Constructor<?> c : ClickEvent.class.getDeclaredConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    if (p.length == 2 && actionEnumType != null && p[0].isAssignableFrom(actionEnumType)) {
                        if (p[1] == String.class) enumCtorString = c;
                        else if (Component.class.isAssignableFrom(p[1])) enumCtorComponent = c;
                    }
                }

                // C) Find nested ClickEvent subclasses that accept (String) / (Component)
                for (Class<?> nested : ClickEvent.class.getDeclaredClasses()) {
                    if (!ClickEvent.class.isAssignableFrom(nested)) continue;
                    try {
                        Constructor<?> sCtor = nested.getConstructor(String.class);
                        // Keep only if it actually represents RUN_COMMAND (cheap probe)
                        Object probe = sCtor.newInstance("/ec probe");
                        if (probe instanceof ClickEvent ce && isRunCommand(ce)) {
                            nestedCtorString = sCtor;
                        }
                    } catch (NoSuchMethodException ignored) { /* no-op */ } catch (Throwable ignored) { /* no-op */ }

                    try {
                        Constructor<?> cCtor = nested.getConstructor(Component.class);
                        Object probe = cCtor.newInstance(Component.literal("/ec probe"));
                        if (probe instanceof ClickEvent ce && isRunCommand(ce)) {
                            nestedCtorComponent = cCtor;
                        }
                    } catch (NoSuchMethodException ignored) { /* no-op */ } catch (Throwable ignored) { /* no-op */ }
                }
            } catch (Throwable ignored) {
                System.out.println("[EC-CC] sc");
            } finally {
                scanned = true;
            }
        }
    }

    private static Object enumConstantIgnoreCase(Class<?> enumType, String name) {
        try {
            if (!enumType.isEnum()) return null;
            Object[] constants = enumType.getEnumConstants();
            if (constants == null) return null;
            for (Object c : constants) {
                if (name.equalsIgnoreCase(String.valueOf(c))) return c;
            }
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] ec");
        }
        return null;
    }

    /**
     * Try to detect RUN_COMMAND action from a ClickEvent instance.
     * Avoids mapping names; prefers enum-like access, falls back to toString().
     */
    private static boolean isRunCommand(ClickEvent ce) {
        try {
            for (Method m : ce.getClass().getMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt.isEnum() || rt.getName().startsWith(ClickEvent.class.getName())) {
                    Object v = m.invoke(ce);
                    String s = String.valueOf(v).toLowerCase();
                    if (s.contains("run_command")) return true;
                }
            }
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] rc");
        }
        try {
            return String.valueOf(ce).toLowerCase().contains("run_command");
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] rs");
            return false;
        }
    }
}
