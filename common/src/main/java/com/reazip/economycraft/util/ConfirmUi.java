package com.reazip.economycraft.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.function.Consumer;

public final class ConfirmUi {
    private ConfirmUi() {}

    private static final int CANCEL_SLOT = MenuUiSupport.ROW_CANCEL;
    private static final int SUBJECT_SLOT = MenuUiSupport.ROW_SUBJECT;
    private static final int CONFIRM_SLOT = MenuUiSupport.ROW_CONFIRM;

    public static void open(ServerPlayer player, String title, ItemStack subject, String confirmLabel,
                            List<Component> lore, Consumer<ServerPlayer> onConfirm, Consumer<ServerPlayer> onCancel) {
        MenuUiSupport.openMenu(player, title, (id, inv) ->
                new ConfirmMenu(id, inv, player, subject, confirmLabel, lore, onConfirm, onCancel));
    }

    private static class ConfirmMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final Consumer<ServerPlayer> onConfirm;
        private final Consumer<ServerPlayer> onCancel;
        private final SimpleContainer container = new SimpleContainer(9);

        ConfirmMenu(int id, Inventory inv, ServerPlayer viewer, ItemStack subject, String confirmLabel,
                    List<Component> lore, Consumer<ServerPlayer> onConfirm, Consumer<ServerPlayer> onCancel) {
            super(MenuType.GENERIC_9x1, id);
            this.viewer = viewer;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;

            container.setItem(CONFIRM_SLOT, MenuUiSupport.confirmButton(confirmLabel));

            ItemStack display = subject == null || subject.isEmpty() ? new ItemStack(Items.PAPER) : subject.copy();
            display.setCount(1);
            display.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(SUBJECT_SLOT, display);

            container.setItem(CANCEL_SLOT, MenuUiSupport.cancelButton());
            MenuUiSupport.fillBackground(container);

            for (Slot slot : MenuUiSupport.confirmRowSlots(container)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 40)) {
                this.addSlot(slot);
            }
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 9) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot == CONFIRM_SLOT) {
                viewer.closeContainer();
                onConfirm.accept(viewer);
                return true;
            }
            if (slot == CANCEL_SLOT) {
                viewer.closeContainer();
                if (onCancel != null) onCancel.accept(viewer);
                return true;
            }
            return true;
        }
    }
}
