package com.reazip.economycraft.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.function.Predicate;

public final class PermissionCompat {

    private PermissionCompat() {}

    public static Predicate<CommandSourceStack> gamemaster() {
        return source -> {
            // Console, command blocks, rcon
            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (Exception e) {
                return true;
            }

            NameAndId nameAndId = new NameAndId(
                    player.getUUID(),
                    player.getName().getString()
            );

            return source.getServer()
                    .getPlayerList()
                    .isOp(nameAndId);
        };
    }

    public static CommandSourceStack withOwnerPermission(CommandSourceStack source) {
        return source;
    }
}
