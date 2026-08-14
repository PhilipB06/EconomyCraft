package com.reazip.economycraft.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class EconomySounds {
    private EconomySounds() {}

    public static void click(ServerPlayer player) {
        play(player, SoundEvents.UI_BUTTON_CLICK, 0.25F, 1.0F);
    }

    public static void page(ServerPlayer player) {
        play(player, SoundEvents.BOOK_PAGE_TURN, 0.4F, 1.0F);
    }

    public static void success(ServerPlayer player) {
        play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45F, 1.0F);
    }

    public static void failure(ServerPlayer player) {
        play(player, SoundEvents.BUNDLE_INSERT_FAIL, 0.4F, 1.0F);
    }

    public static void moneyReceived(ServerPlayer player) {
        play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45F, 1.15F);
    }

    public static void itemPickedUp(ServerPlayer player) {
        play(player, SoundEvents.ITEM_PICKUP, 0.35F, 1.1F);
    }

    public static void dailyReward(ServerPlayer player) {
        play(player, SoundEvents.PLAYER_LEVELUP, 0.45F, 1.1F);
    }

    public static void itemsStored(ServerPlayer player) {
        play(player, SoundEvents.BUNDLE_INSERT, 0.35F, 1.0F);
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        play(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
    }

    private static void play(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                sound,
                SoundSource.PLAYERS,
                player.getX(),
                player.getY(),
                player.getZ(),
                volume,
                pitch,
                player.getRandom().nextLong()));
    }
}
