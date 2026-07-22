package com.reazip.economycraft.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Read-only preview of a container item's contents (e.g. a filled shulker box), reachable by
 * right-click from any menu that shows the item. Shared by the shop, server shop, and orders UIs.
 */
public final class ContainerPreviewUi {
    private ContainerPreviewUi() {}

    private static final int PREVIEW_SLOTS = 27;
    private static final int PREVIEW_BACK_SLOT = PREVIEW_SLOTS + 4;

    public static void open(ServerPlayer player, ItemStack stack, Runnable onBack) {
        Component title = Component.literal(stack.getHoverName().getString() + " Contents");
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new PreviewMenu(id, inv, stack, onBack);
            }
        });
    }

    private static class PreviewMenu extends AbstractContainerMenu {
        private final Runnable onBack;
        private final SimpleContainer container = new SimpleContainer(PREVIEW_SLOTS + 9);

        PreviewMenu(int id, Inventory inv, ItemStack stack, Runnable onBack) {
            super(MenuType.GENERIC_9x4, id);
            this.onBack = onBack;

            MenuUiSupport.copyContainerContents(stack, container, PREVIEW_SLOTS);

            ItemStack back = new ItemStack(Items.BARRIER);
            back.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Back").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(PREVIEW_BACK_SLOT, back);

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, PREVIEW_SLOTS + 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 4 * 18 + 14)) {
                this.addSlot(slot);
            }
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP && slot == PREVIEW_BACK_SLOT) {
                player.closeContainer();
                onBack.run();
                return;
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }
}
