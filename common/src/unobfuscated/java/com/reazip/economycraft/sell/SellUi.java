package com.reazip.economycraft.sell;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.ItemsCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public final class SellUi {
    private SellUi() {}

    private static final int DEPOSIT_ROWS = 5;
    private static final int DEPOSIT_SLOTS = DEPOSIT_ROWS * 9;
    private static final int NAV_BALANCE = 0;
    private static final int NAV_CONFIRM = 8;
    private static final int NAV_ROW_SLOTS = 9;
    private static final int NAV_ROW_END = DEPOSIT_SLOTS + NAV_ROW_SLOTS;
    private static final int CONFIRM_SLOT = DEPOSIT_SLOTS + NAV_CONFIRM;

    public static void open(ServerPlayer player, EconomyManager manager) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Sell");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new SellMenu(id, inv, player, manager);
            }
        });
    }

    private static class SellMenu extends AbstractContainerMenu {
        private final ServerPlayer viewer;
        private final EconomyManager manager;
        private final PriceRegistry prices;
        private final SimpleContainer depositContainer = new SimpleContainer(DEPOSIT_SLOTS);
        private final SimpleContainer navContainer = new SimpleContainer(9);

        SellMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager manager) {
            super(MenuType.GENERIC_9x6, id);
            this.viewer = viewer;
            this.manager = manager;
            this.prices = manager.getPrices();

            for (Slot slot : MenuUiSupport.openGridSlots(depositContainer, DEPOSIT_SLOTS,
                    stack -> SellService.sellableResolved(this.prices, stack) != null)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.lockedRowSlots(navContainer, 18 + DEPOSIT_ROWS * 18)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 6 * 18 + 14)) {
                this.addSlot(slot);
            }

            renderNavRow();
        }

        private record SellPreview(int count, long total) {}

        private SellPreview previewTotals() {
            int count = 0;
            long total = 0;
            for (int i = 0; i < DEPOSIT_SLOTS; i++) {
                ItemStack stack = depositContainer.getItem(i);
                if (stack.isEmpty()) continue;
                if (SellService.sellableResolved(prices, stack) == null) continue;
                Long unitSell = prices.getUnitSell(stack);
                if (unitSell == null) continue;
                Long value = safeMultiply(unitSell, stack.getCount());
                if (value == null) continue;
                Long sum = safeAdd(total, value);
                if (sum == null) continue;
                total = sum;
                count += stack.getCount();
            }
            return new SellPreview(count, total);
        }

        private void renderNavRow() {
            navContainer.setItem(NAV_BALANCE, MenuUiSupport.createBalanceItem(viewer));

            SellPreview preview = previewTotals();
            ItemStack confirm = new ItemStack(ItemsCompat.limeStainedGlassPane());
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Confirm").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            confirm.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Sells the items above").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                    MenuUiSupport.labeledValue("Items", String.valueOf(preview.count()), MenuUiSupport.LABEL_SECONDARY_COLOR),
                    MenuUiSupport.labeledValue("Total", EconomyCraft.formatMoney(preview.total()), MenuUiSupport.LABEL_PRIMARY_COLOR))));
            navContainer.setItem(NAV_CONFIRM, confirm);
        }

        private void performSale(ServerPlayer player) {
            int orderGivenTotal = 0;
            long orderPayoutTotal = 0;
            int serverSoldTotal = 0;
            long serverPayoutTotal = 0;
            int limitBlockedTotal = 0;

            for (int i = 0; i < DEPOSIT_SLOTS; i++) {
                ItemStack stack = depositContainer.getItem(i);
                if (stack.isEmpty()) continue;
                if (SellService.sellableResolved(prices, stack) == null) continue;
                Long unitSell = prices.getUnitSell(stack);
                if (unitSell == null) continue;

                SellService.SaleSplit split = SellService.sellHandWithRouting(manager, player, stack, stack.getCount(), unitSell);
                orderGivenTotal += split.orderGiven();
                orderPayoutTotal += split.orderPayout();

                if (split.serverRemaining() > 0) {
                    Long potential = safeMultiply(unitSell, split.serverRemaining());
                    if (potential == null) {
                        // overflow: leave the remainder in the slot, it'll be handed back when the GUI closes
                    } else if (EconomyConfig.get().dailySellLimit > 0 && manager.tryRecordDailySell(player.getUUID(), potential)) {
                        limitBlockedTotal += split.serverRemaining();
                    } else {
                        serverSoldTotal += split.serverRemaining();
                        serverPayoutTotal += potential;
                        stack.shrink(split.serverRemaining());
                        manager.addMoney(player.getUUID(), potential);
                    }
                }

                if (stack.isEmpty()) depositContainer.setItem(i, ItemStack.EMPTY);
            }

            int totalSold = orderGivenTotal + serverSoldTotal;
            if (totalSold > 0) {
                long totalPayout = orderPayoutTotal + serverPayoutTotal;
                Component msg = Component.literal("Successfully sold " + totalSold + " item" + (totalSold == 1 ? "" : "s") +
                                " for " + EconomyCraft.formatMoney(totalPayout) +
                                (orderGivenTotal > 0 ? " (" + orderGivenTotal + " to open orders for a better price)" : "") + ".")
                        .withStyle(ChatFormatting.GREEN);
                player.sendSystemMessage(msg);
            }

            if (limitBlockedTotal > 0) {
                long remaining = manager.getDailySellRemaining(player.getUUID());
                player.sendSystemMessage(Component.literal(limitBlockedTotal + " item" + (limitBlockedTotal == 1 ? "" : "s") +
                                " was not sold: daily sell limit reached" +
                                (remaining > 0 ? " (" + EconomyCraft.formatMoney(remaining) + " left today)." : "."))
                        .withStyle(ChatFormatting.RED));
            }

            renderNavRow();
        }

        @Override
        public void clicked(int slot, int dragType, ContainerInput type, Player player) {
            if (slot == CONFIRM_SLOT) {
                if (type == ContainerInput.PICKUP) {
                    performSale((ServerPlayer) player);
                }
                return;
            }
            if (slot >= DEPOSIT_SLOTS && slot < NAV_ROW_END) {
                return;
            }
            if (slot >= 0 && slot < DEPOSIT_SLOTS) {
                ItemStack carried = this.getCarried();
                if (!carried.isEmpty() && SellService.sellableResolved(prices, carried) == null) {
                    rejectUnsellable(player, carried);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
            renderNavRow();
        }

        private void rejectUnsellable(Player player, ItemStack stack) {
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(stack.getHoverName().getString() + " cannot be sold.")
                        .withStyle(ChatFormatting.RED));
            }
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (player instanceof ServerPlayer sp) {
                performSale(sp);
            }
            clearContainer(player, depositContainer);
        }

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = this.getSlot(index);
            if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

            ItemStack original = slot.getItem();
            ItemStack copy = original.copy();

            boolean moved;
            if (index < DEPOSIT_SLOTS) {
                moved = this.moveItemStackTo(original, NAV_ROW_END, this.slots.size(), true);
            } else if (index >= NAV_ROW_END) {
                if (SellService.sellableResolved(prices, original) == null) {
                    rejectUnsellable(player, original);
                    return ItemStack.EMPTY;
                }
                moved = this.moveItemStackTo(original, 0, DEPOSIT_SLOTS, false);
            } else {
                return ItemStack.EMPTY;
            }

            if (!moved) return ItemStack.EMPTY;

            if (original.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            renderNavRow();
            return copy;
        }

        private static Long safeMultiply(long value, int count) {
            try {
                return Math.multiplyExact(value, count);
            } catch (ArithmeticException ex) {
                return null;
            }
        }

        private static Long safeAdd(long a, long b) {
            try {
                return Math.addExact(a, b);
            } catch (ArithmeticException ex) {
                return null;
            }
        }
    }
}
