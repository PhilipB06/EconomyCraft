package com.reazip.economycraft.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyPermissions {
    private EconomyPermissions() {}

    public static final class Nodes {
        private Nodes() {}

        public static final String[] ADMIN_ALL = {
                "economycraft.admin", "economycraft.admin.players", "economycraft.admin.settings",
                "economycraft.admin.shop", "economycraft.admin.reload", "economycraft.admin.reset"
        };
        public static final String ADMIN = ADMIN_ALL[0];
        public static final String ADMIN_PLAYERS = ADMIN_ALL[1];
        public static final String ADMIN_SETTINGS = ADMIN_ALL[2];
        public static final String ADMIN_SHOP = ADMIN_ALL[3];
        public static final String ADMIN_RELOAD = ADMIN_ALL[4];
        public static final String ADMIN_RESET = ADMIN_ALL[5];

        public static final String[] COMMAND_ALL = {
                "economycraft.command.menu", "economycraft.command.balance", "economycraft.command.pay",
                "economycraft.command.shop", "economycraft.command.auction", "economycraft.command.sell",
                "economycraft.command.orders", "economycraft.command.deliveries", "economycraft.command.daily",
                "economycraft.command.transactions", "economycraft.command.worth"
        };
        public static final String COMMAND_MENU = COMMAND_ALL[0];
        public static final String COMMAND_BALANCE = COMMAND_ALL[1];
        public static final String COMMAND_PAY = COMMAND_ALL[2];
        public static final String COMMAND_SHOP = COMMAND_ALL[3];
        public static final String COMMAND_AUCTION = COMMAND_ALL[4];
        public static final String COMMAND_SELL = COMMAND_ALL[5];
        public static final String COMMAND_ORDERS = COMMAND_ALL[6];
        public static final String COMMAND_DELIVERIES = COMMAND_ALL[7];
        public static final String COMMAND_DAILY = COMMAND_ALL[8];
        public static final String COMMAND_TRANSACTIONS = COMMAND_ALL[9];
        public static final String COMMAND_WORTH = COMMAND_ALL[10];
    }

    public interface Backend {
        boolean check(CommandSourceStack source, String node, boolean fallback);
    }

    private static volatile Backend backend = (source, node, fallback) -> fallback;
    private static volatile boolean configured = false;

    public static void setBackend(Backend newBackend) {
        backend = newBackend != null ? newBackend : (source, node, fallback) -> fallback;
        configured = newBackend != null;
    }

    public static boolean check(CommandSourceStack source, String node, boolean fallback) {
        return backend.check(source, node, fallback);
    }

    public static boolean checkAdmin(CommandSourceStack source, String node) {
        if (!configured) return isAdmin(source);
        Boolean specific = explicit(source, node);
        if (specific != null) return specific;
        Boolean blanket = explicit(source, Nodes.ADMIN);
        if (blanket != null) return blanket;
        return isAdmin(source);
    }

    public static boolean checkAdmin(ServerPlayer player, String node) {
        return checkAdmin(player.createCommandSourceStack(), node);
    }

    public static boolean hasAnyAdmin(CommandSourceStack source) {
        for (String node : Nodes.ADMIN_ALL) {
            if (node.equals(Nodes.ADMIN)) continue;
            if (checkAdmin(source, node)) return true;
        }
        return false;
    }

    public static boolean hasAnyAdmin(ServerPlayer player) {
        return hasAnyAdmin(player.createCommandSourceStack());
    }

    public static boolean checkCommand(CommandSourceStack source, String node) {
        return check(source, node, true);
    }

    public static boolean checkCommand(ServerPlayer player, String node) {
        return checkCommand(player.createCommandSourceStack(), node);
    }

    private static Boolean explicit(CommandSourceStack source, String node) {
        boolean whenTrue = check(source, node, true);
        boolean whenFalse = check(source, node, false);
        return whenTrue == whenFalse ? whenTrue : null;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        try {
            return PermissionCompat.isAdmin(source.getPlayerOrException());
        } catch (Exception e) {
            return true;
        }
    }
}
