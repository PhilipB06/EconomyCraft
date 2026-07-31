package com.reazip.economycraft.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ContainerPreviewUi {
    private ContainerPreviewUi() {}

    private static final int PREVIEW_SLOTS = 27;
    private static final int PREVIEW_BACK_SLOT = PREVIEW_SLOTS + 4;

    public static void open(ServerPlayer player, ItemStack stack, Runnable onBack) {
        String title = stack.getHoverName().getString() + " Contents";
        MenuUiSupport.openMenu(player, title, (id, inv) -> new PreviewMenu(id, inv, stack, onBack));
    }

    private static class PreviewMenu extends CompatMenu {
        private final Runnable onBack;
        private final SimpleContainer container = new SimpleContainer(PREVIEW_SLOTS + 9);

        PreviewMenu(int id, Inventory inv, ItemStack stack, Runnable onBack) {
            super(MenuType.GENERIC_9x4, id);
            this.onBack = onBack;

            MenuUiSupport.copyContainerContents(stack, container, PREVIEW_SLOTS);
            container.setItem(PREVIEW_BACK_SLOT, MenuUiSupport.backButton());
            MenuUiSupport.fillFooter(container);

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, PREVIEW_SLOTS + 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 4 * 18 + 14)) {
                this.addSlot(slot);
            }
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (kind == ClickKind.PICKUP && slot == PREVIEW_BACK_SLOT) {
                player.closeContainer();
                onBack.run();
                return true;
            }
            return false;
        }
    }
}
