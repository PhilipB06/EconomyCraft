package com.reazip.economycraft.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public final class PermissionCompat {
    private PermissionCompat() {}

    public static boolean isAdmin(ServerPlayer player) {
        if (player == null) return false;
        var server = player.level().getServer();
        if (server == null) return false;
        return server.getPlayerList().isOp(new NameAndId(player.getUUID(), player.getName().getString()));
    }
}
