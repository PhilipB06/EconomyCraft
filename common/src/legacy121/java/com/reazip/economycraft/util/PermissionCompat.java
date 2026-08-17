package com.reazip.economycraft.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

public final class PermissionCompat {
    private PermissionCompat() {}

    public static Predicate<CommandSourceStack> gamemaster() {
        return source -> {
            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (Exception e) {
                return true;
            }
            return isAdmin(player);
        };
    }

    public static boolean isAdmin(ServerPlayer player) {
        if (player == null) return false;
        var server = player.level().getServer();
        return server != null && server.getPlayerList().isOp(player.getGameProfile());
    }
}
