package com.reazip.economycraft.util;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ChatCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChatCompat() {}

    private static volatile boolean scanned = false;

    private static volatile Method factoryMethod;
    private static volatile Class<?> actionEnumType;
    private static volatile Object runCommandEnum;
    private static volatile Constructor<?> enumCtorString;
    private static volatile Constructor<?> enumCtorComponent;
    private static volatile Constructor<?> nestedCtorString;
    private static volatile Constructor<?> nestedCtorComponent;

    public static ClickEvent runCommandEvent(String cmd) {
        ensureScanned();

        try {
            if (factoryMethod != null) {
                Object ev = factoryMethod.invoke(null, cmd);
                if (ev instanceof ClickEvent ce && isRunCommand(ce)) {
                    return ce;
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[ChatCompat] Static factory ClickEvent strategy failed", t);
        }

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
        } catch (Throwable t) {
            LOGGER.debug("[ChatCompat] Action-enum constructor ClickEvent strategy failed", t);
        }

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
        } catch (Throwable t) {
            LOGGER.debug("[ChatCompat] Nested-class ClickEvent strategy failed", t);
        }

        return null;
    }

    public static void sendRunCommandTellraw(ServerPlayer target, String prefixText, String labelText, String cmd) {
        try {
            String json = getJson(prefixText, labelText, cmd);

            String selector = target.getScoreboardName();
            String line = "tellraw " + selector + " " + json;

            var srv = target.level().getServer();
            srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), line);
        } catch (Throwable t) {
            LOGGER.debug("[ChatCompat] Tellraw fallback failed", t);
        }
    }

    private static @NonNull String getJson(String prefixText, String labelText, String cmd) {
        String escCmd = cmd.replace("\\", "\\\\").replace("\"", "\\\"");
        String escPrefix = prefixText.replace("\\", "\\\\").replace("\"", "\\\"");
        String escLabel = labelText.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"text\":\"" + escPrefix + "\",\"color\":\"yellow\",\"extra\":[{\"text\":\"" + escLabel +
                "\",\"underlined\":true,\"color\":\"green\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" +
                escCmd + "\"}}]}";
    }

    private static void ensureScanned() {
        if (scanned) return;
        synchronized (ChatCompat.class) {
            if (scanned) return;
            try {
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

                for (Class<?> nested : ClickEvent.class.getDeclaredClasses()) {
                    if (nested.isEnum()) {
                        Object rc = enumConstantIgnoreCase(nested);
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

                for (Class<?> nested : ClickEvent.class.getDeclaredClasses()) {
                    if (!ClickEvent.class.isAssignableFrom(nested)) continue;
                    try {
                        Constructor<?> sCtor = nested.getConstructor(String.class);
                        Object probe = sCtor.newInstance("/ec probe");
                        if (probe instanceof ClickEvent ce && isRunCommand(ce)) {
                            nestedCtorString = sCtor;
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Constructor<?> cCtor = nested.getConstructor(Component.class);
                        Object probe = cCtor.newInstance(Component.literal("/ec probe"));
                        if (probe instanceof ClickEvent ce && isRunCommand(ce)) {
                            nestedCtorComponent = cCtor;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                LOGGER.debug("[ChatCompat] Reflective scan for ClickEvent shape failed", t);
            } finally {
                scanned = true;
            }
        }
    }

    private static Object enumConstantIgnoreCase(Class<?> enumType) {
        try {
            if (!enumType.isEnum()) return null;
            Object[] constants = enumType.getEnumConstants();
            if (constants == null) return null;
            for (Object c : constants) {
                if ("RUN_COMMAND".equalsIgnoreCase(String.valueOf(c))) return c;
            }
        } catch (Throwable t) {
            LOGGER.debug("[ChatCompat] Enum constant lookup failed", t);
        }
        return null;
    }

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
        } catch (Throwable t) {
            LOGGER.debug("[ChatCompat] RUN_COMMAND detection via accessor methods failed", t);
        }
        try {
            return String.valueOf(ce).toLowerCase().contains("run_command");
        } catch (Throwable t) {
            LOGGER.debug("[ChatCompat] RUN_COMMAND detection via toString() failed", t);
            return false;
        }
    }
}
