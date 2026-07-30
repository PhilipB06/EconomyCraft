package com.reazip.economycraft.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.util.function.BiConsumer;

public final class SearchInputUi {
    private SearchInputUi() {}

    public static void open(ServerPlayer player, String title, BiConsumer<ServerPlayer, String> onSearch) {
        player.openMenu(new MenuProvider() {
            @Override
            public @NonNull Component getDisplayName() {
                return Component.literal(title);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, @NonNull Inventory inv, @NonNull Player p) {
                return new SearchMenu(id, inv, onSearch);
            }
        });
    }

    private static class SearchMenu extends AnvilMenu {
        private final BiConsumer<ServerPlayer, String> onSearch;
        private String query = "";

        SearchMenu(int id, Inventory inv, BiConsumer<ServerPlayer, String> onSearch) {
            super(id, inv, ContainerLevelAccess.NULL);
            this.onSearch = onSearch;
            ItemStack placeholder = new ItemStack(Items.PAPER);
            placeholder.set(DataComponents.CUSTOM_NAME, Component.literal(""));
            this.inputSlots.setItem(INPUT_SLOT, placeholder);
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
            this.query = name;
            createResult();
            return true;
        }

        @Override
        public void createResult() {
            String q = query == null ? "" : query;
            ItemStack icon = new ItemStack(Items.COMPASS);
            Component name = q.isBlank()
                    ? Component.literal("Type to search").withStyle(s -> s.withItalic(false))
                    : Component.literal("Search: " + q).withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN));
            icon.set(DataComponents.CUSTOM_NAME, name);
            this.resultSlots.setItem(0, icon);
        }

        @Override
        protected boolean mayPickup(@NonNull Player player, boolean hasItem) {
            return hasItem && query != null && !query.isBlank();
        }

        @Override
        protected void onTake(Player player, @NonNull ItemStack stack) {
            String q = query;
            this.setCarried(ItemStack.EMPTY);
            player.closeContainer();
            ServerPlayer sp = (ServerPlayer) player;
            sp.connection.send(new ClientboundSetExperiencePacket(sp.experienceProgress, sp.totalExperience, sp.experienceLevel));
            if (q != null && !q.isBlank()) {
                onSearch.accept(sp, q.trim());
            }
        }

        @Override
        public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
