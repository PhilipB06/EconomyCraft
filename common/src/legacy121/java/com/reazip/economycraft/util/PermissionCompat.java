package com.reazip.economycraft.util;

import net.minecraft.server.level.ServerPlayer;

public final class PermissionCompat {
    private PermissionCompat() {}

    public static boolean isAdmin(ServerPlayer player) {
        if (player == null) return false;
        var server = player.level().getServer();
        return server != null && server.getPlayerList().isOp(player.getGameProfile());
    }
}
