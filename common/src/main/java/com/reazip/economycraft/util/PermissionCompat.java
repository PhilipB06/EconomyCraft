package com.reazip.economycraft.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

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
        if (server == null) return false;

        NameAndId nameAndId = new NameAndId(
                player.getUUID(),
                player.getName().getString()
        );

        return server.getPlayerList().isOp(nameAndId);
    }
}
