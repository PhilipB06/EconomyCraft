package com.reazip.economycraft.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.BiConsumer;

public final class TextInputUi {
    private TextInputUi() {}

    public static void openSearch(ServerPlayer player, String title, BiConsumer<ServerPlayer, String> onSearch) {
        open(player, title, "", Items.COMPASS, "Search: ", "Type to search", onSearch);
    }

    public static void open(ServerPlayer player, String title, String initial, Item icon,
                            String confirmPrefix, String placeholder, BiConsumer<ServerPlayer, String> onConfirm) {
        MenuUiSupport.openMenu(player, title, (id, inv) ->
                new InputMenu(id, inv, initial, icon, confirmPrefix, placeholder, onConfirm));
    }

    private static class InputMenu extends AnvilMenu {
        private final Item icon;
        private final String confirmPrefix;
        private final String placeholder;
        private final BiConsumer<ServerPlayer, String> onConfirm;
        private String text;

        InputMenu(int id, Inventory inv, String initial, Item icon, String confirmPrefix, String placeholder,
                  BiConsumer<ServerPlayer, String> onConfirm) {
            super(id, inv, ContainerLevelAccess.NULL);
            this.icon = icon;
            this.confirmPrefix = confirmPrefix;
            this.placeholder = placeholder;
            this.onConfirm = onConfirm;
            this.text = initial == null ? "" : initial;

            ItemStack input = new ItemStack(Items.PAPER);
            input.set(DataComponents.CUSTOM_NAME, Component.literal(this.text));
            this.inputSlots.setItem(INPUT_SLOT, input);
            lockInputSlot(INPUT_SLOT);
            lockInputSlot(ADDITIONAL_SLOT);
            createResult();
        }

        private void lockInputSlot(int index) {
            Slot original = this.slots.get(index);
            this.slots.set(index, MenuUiSupport.lockedSlot(this.inputSlots, index, original.x, original.y));
        }

        @Override
        public boolean setItemName(@NonNull String name) {
            this.text = name;
            createResult();
            return true;
        }

        @Override
        public void createResult() {
            String value = text == null ? "" : text;
            ItemStack result = new ItemStack(icon);
            Component name = value.isBlank()
                    ? Component.literal(placeholder).withStyle(s -> s.withItalic(false))
                    : Component.literal(confirmPrefix + value)
                            .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN));
            result.set(DataComponents.CUSTOM_NAME, name);
            result.set(DataComponents.LORE, new ItemLore(List.of(
                    MenuUiSupport.hint(value.isBlank() ? "Type in the field above" : "Click here to confirm"))));
            this.resultSlots.setItem(0, result);
        }

        @Override
        protected boolean mayPickup(@NonNull Player player, boolean hasItem) {
            return hasItem && text != null && !text.isBlank();
        }

        @Override
        protected void onTake(Player player, @NonNull ItemStack stack) {
            String value = text;
            this.setCarried(ItemStack.EMPTY);
            player.closeContainer();
            ServerPlayer serverPlayer = (ServerPlayer) player;
            serverPlayer.connection.send(new ClientboundSetExperiencePacket(
                    serverPlayer.experienceProgress, serverPlayer.totalExperience, serverPlayer.experienceLevel));
            if (value != null && !value.isBlank()) {
                onConfirm.accept(serverPlayer, value.trim());
            }
        }

        @Override
        public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
