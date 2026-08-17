package com.reazip.economycraft.neoforge;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.PlaceholderValues;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class EconomyCraftNeoForgePlaceholders {
    private static final Logger LOGGER = LoggerFactory.getLogger("EconomyCraft/PlaceholderAPI");

    private static volatile Method serverMethod;
    private static volatile Method hasPlayerMethod;
    private static volatile Method playerMethod;

    private EconomyCraftNeoForgePlaceholders() {}

    static void register() {
        try {
            Class<?> placeholders = Class.forName("eu.pb4.placeholders.api.Placeholders");
            Class<?> handlerType = Class.forName("eu.pb4.placeholders.api.PlaceholderHandler");
            Class<?> resultType = Class.forName("eu.pb4.placeholders.api.PlaceholderResult");
            Method register = placeholders.getMethod("register", ResourceLocation.class, handlerType);
            Method value = resultType.getMethod("value", String.class);
            Method invalid = resultType.getMethod("invalid", String.class);

            register(register, handlerType, value, invalid, "balance");
            register(register, handlerType, value, invalid, "balance_formatted");
            register(register, handlerType, value, invalid, "daily_sell_remaining");
            register(register, handlerType, value, invalid, "top_name");
            register(register, handlerType, value, invalid, "top_balance");
            register(register, handlerType, value, invalid, "top_balance_formatted");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register EconomyCraft placeholders", e);
        }
    }

    private static void register(Method register, Class<?> handlerType, Method value, Method invalid,
                                 String path) throws ReflectiveOperationException {
        Object handler = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "EconomyCraft placeholder " + path;
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if (args == null || args.length == 0) {
                        return null;
                    }
                    Object context = args[0];
                    String argument = args.length > 1 && args[1] instanceof String string ? string : null;
                    try {
                        return resolve(context, argument, path, value, invalid);
                    } catch (ReflectiveOperationException e) {
                        LOGGER.error("Failed to resolve EconomyCraft placeholder {}", path, e);
                        return invalid.invoke(null, "Error");
                    }
                });
        register.invoke(null, ResourceLocation.fromNamespaceAndPath(EconomyCraft.MOD_ID, path), handler);
    }

    private static Object resolve(Object context, String argument, String path, Method value, Method invalid)
            throws ReflectiveOperationException {
        if (serverMethod == null) {
            Class<?> cls = context.getClass();
            serverMethod = cls.getMethod("server");
            hasPlayerMethod = cls.getMethod("hasPlayer");
            playerMethod = cls.getMethod("player");
        }

        MinecraftServer server = (MinecraftServer) serverMethod.invoke(context);
        EconomyManager economy = EconomyCraft.getManager(server);

        if (path.startsWith("top_")) {
            String result = switch (path) {
                case "top_name" -> PlaceholderValues.topName(economy, argument);
                case "top_balance" -> PlaceholderValues.topBalance(economy, argument);
                default -> PlaceholderValues.topBalanceFormatted(economy, argument);
            };
            return result != null ? value.invoke(null, result) : invalid.invoke(null, "No player!");
        }

        boolean hasPlayer = Boolean.TRUE.equals(hasPlayerMethod.invoke(context));
        if (!hasPlayer) return invalid.invoke(null, "No player!");
        ServerPlayer player = (ServerPlayer) playerMethod.invoke(context);
        return value.invoke(null, switch (path) {
            case "balance" -> PlaceholderValues.balance(economy, player.getUUID());
            case "balance_formatted" -> PlaceholderValues.balanceFormatted(economy, player.getUUID());
            default -> PlaceholderValues.dailySellRemaining(economy, player.getUUID());
        });
    }
}
