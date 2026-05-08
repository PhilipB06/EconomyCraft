package com.reazip.economycraft.util;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.reazip.economycraft.util.PermissionCompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Вспомогательный класс для ClickEvent на версиях 1.21.x (Fabric/NeoForge).
 * Создаёт ClickEvent с действием RUN_COMMAND, работая через разные маппинги и API.
 */
public final class ChatCompat {
    private ChatCompat() {}

    // ---- Кэш (заполняется лениво при первом использовании) --------------------------------
    private static volatile boolean scanned = false; // Флаг сканирования

    private static volatile Method factoryMethod;                 // (String) -> ClickEvent (статический фабричный метод)
    private static volatile Class<?> actionEnumType;              // ClickEvent.Action (или обфусцированное имя)
    private static volatile Object runCommandEnum;                // константа RUN_COMMAND
    private static volatile Constructor<?> enumCtorString;        // ClickEvent(Action, String)
    private static volatile Constructor<?> enumCtorComponent;     // ClickEvent(Action, Component)
    private static volatile Constructor<?> nestedCtorString;      // ClickEvent$RunCommand(String) или подобный
    private static volatile Constructor<?> nestedCtorComponent;   // ClickEvent$RunCommand(Component) или подобный

    /**
     * Создаёт ClickEvent RUN_COMMAND для указанной команды.
     * Возвращает null, если не найден совместимый конструктор.
     */
    public static ClickEvent runCommandEvent(String cmd) {
        ensureScanned(); // Сканируем доступные конструкторы

        // A) Предпочтительный способ: статический фабричный метод (String) -> ClickEvent
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

        // B) Конструктор с перечислением Action
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

        // C) Запасной вариант с вложенным классом
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

    // ---- Гарантированный запасной способ ------------------------------------------------

    /**
     * Гарантированно работающая кликабельная команда RUN_COMMAND через /tellraw.
     * Используйте, если runCommandEvent(...) возвращает null.
     */
    public static void sendRunCommandTellraw(ServerPlayer target, String prefixText, String labelText, String cmd) {
        try {
            // Экранируем специальные символы для JSON
            String escCmd = cmd.replace("\\", "\\\\").replace("\"", "\\\"");
            String escPrefix = prefixText.replace("\\", "\\\\").replace("\"", "\\\"");
            String escLabel = labelText.replace("\\", "\\\\").replace("\"", "\\\"");
            String json = "{\"text\":\"" + escPrefix + "\",\"color\":\"yellow\",\"extra\":[{\"text\":\"" + escLabel +
                    "\",\"underlined\":true,\"color\":\"green\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" +
                    escCmd + "\"}}]}";

            String selector = target.getScoreboardName(); // безопасно
            String line = "tellraw " + selector + " " + json;

            var srv = target.level().getServer();
            srv.getCommands().performPrefixedCommand(
                    PermissionCompat.withOwnerPermission(srv.createCommandSourceStack()),
                    line);
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] tw");
        }
    }

    // ---- Сканирование и вспомогательные методы -----------------------------------------------------

    /** Выполняет сканирование доступных конструкторов ClickEvent (один раз). */
    private static void ensureScanned() {
        if (scanned) return;
        synchronized (ChatCompat.class) {
            if (scanned) return;
            try {
                // A) Поиск публичного статического фабричного метода: (String) -> ClickEvent
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

                // B) Поиск перечисления Action и константы RUN_COMMAND
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

                // Ищем конструкторы ClickEvent с параметрами (Action, ...)
                for (Constructor<?> c : ClickEvent.class.getDeclaredConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    if (p.length == 2 && actionEnumType != null && p[0].isAssignableFrom(actionEnumType)) {
                        if (p[1] == String.class) enumCtorString = c;
                        else if (Component.class.isAssignableFrom(p[1])) enumCtorComponent = c;
                    }
                }

                // C) Поиск вложенных классов ClickEvent (например, ClickEvent$RunCommand)
                for (Class<?> nested : ClickEvent.class.getDeclaredClasses()) {
                    if (!ClickEvent.class.isAssignableFrom(nested)) continue;
                    try {
                        Constructor<?> sCtor = nested.getConstructor(String.class);
                        // Проверяем, что конструктор действительно создаёт RUN_COMMAND
                        Object probe = sCtor.newInstance("/ec probe");
                        if (probe instanceof ClickEvent ce && isRunCommand(ce)) {
                            nestedCtorString = sCtor;
                        }
                    } catch (NoSuchMethodException ignored) { /* нет конструктора */ } catch (Throwable ignored) { /* ошибка */ }

                    try {
                        Constructor<?> cCtor = nested.getConstructor(Component.class);
                        Object probe = cCtor.newInstance(Component.literal("/ec probe"));
                        if (probe instanceof ClickEvent ce && isRunCommand(ce)) {
                            nestedCtorComponent = cCtor;
                        }
                    } catch (NoSuchMethodException ignored) { /* нет конструктора */ } catch (Throwable ignored) { /* ошибка */ }
                }
            } catch (Throwable ignored) {
                System.out.println("[EC-CC] sc");
            } finally {
                scanned = true;
            }
        }
    }

    /** Ищет константу перечисления по имени (без учёта регистра). */
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
     * Пытается определить, является ли ClickEvent действием RUN_COMMAND.
     * Избегает прямых маппингов, используя рефлексию или toString().
     */
    private static boolean isRunCommand(ClickEvent ce) {
        try {
            // Пытаемся найти метод, возвращающий перечисление Action
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
        // Запасной вариант: проверяем строковое представление
        try {
            return String.valueOf(ce).toLowerCase().contains("run_command");
        } catch (Throwable ignored) {
            System.out.println("[EC-CC] rs");
            return false;
        }
    }
}