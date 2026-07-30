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

import java.util.function.BiConsumer;

/**
 * Free-text search box built on a virtual anvil menu (no block in the world) - the rename field is
 * the only native text input vanilla offers inside a container screen. Typing updates the
 * result-slot icon live; taking it submits the query to {@code onSearch} and closes the menu.
 * Never overrides {@code clicked(...)}, so unlike the shop/orders/server-shop menus this needs no
 * obfuscated/unobfuscated split.
 */
public final class SearchInputUi {
    private SearchInputUi() {}

    public static void open(ServerPlayer player, String title, BiConsumer<ServerPlayer, String> onSearch) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(title);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
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
            // The client only enables the anvil's rename text field when the input slot holds an
            // item, so a placeholder has to sit there for typing to work at all. Both anvil input
            // slots are then locked against pickup/place: this is a virtual menu with no block
            // (ContainerLevelAccess.NULL), so vanilla's usual "give the input slots back to the
            // player on close" never runs, and anything left there would just be destroyed.
            ItemStack placeholder = new ItemStack(Items.PAPER);
            // The client seeds the rename text field from this stack's hover name (AnvilScreen#slotChanged),
            // so an explicit blank custom name is what makes the field start empty instead of "Paper".
            placeholder.set(DataComponents.CUSTOM_NAME, Component.literal(""));
            this.inputSlots.setItem(INPUT_SLOT, placeholder);
            lockInputSlot(INPUT_SLOT);
            lockInputSlot(ADDITIONAL_SLOT);
            createResult();
        }

        private void lockInputSlot(int index) {
            Slot original = this.slots.get(index);
            this.slots.set(index, new Slot(this.inputSlots, index, original.x, original.y) {
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }

        @Override
        public boolean setItemName(String name) {
            this.query = name == null ? "" : name;
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
        protected boolean mayPickup(Player player, boolean hasItem) {
            return hasItem && query != null && !query.isBlank();
        }

        @Override
        protected void onTake(Player player, ItemStack stack) {
            String q = query;
            this.setCarried(ItemStack.EMPTY);
            player.closeContainer();
            ServerPlayer sp = (ServerPlayer) player;
            // The client predicts this take against its own separate, unmodified AnvilMenu mirror
            // (only the menu type crosses the network, not this subclass), which can locally and
            // visually decrement the player's displayed XP even though nothing changes server-side.
            // ServerPlayer's own give/setExperience helpers all skip re-sending when the value they'd
            // apply doesn't change anything, so the correction has to be sent directly like this,
            // the same way ServerPlayer itself pushes experience updates to the client.
            sp.connection.send(new ClientboundSetExperiencePacket(sp.experienceProgress, sp.totalExperience, sp.experienceLevel));
            if (q != null && !q.isBlank()) {
                onSearch.accept(sp, q.trim());
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
