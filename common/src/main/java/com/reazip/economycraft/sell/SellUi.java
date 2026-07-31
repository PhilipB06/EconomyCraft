package com.reazip.economycraft.sell;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.HubUi;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SellUi {
    private SellUi() {}

    private static final int DEPOSIT_ROWS = 5;
    private static final int DEPOSIT_SLOTS = DEPOSIT_ROWS * 9;
    private static final int NAV_BALANCE = 0;
    private static final int NAV_FILL = 3;
    private static final int NAV_HELP = 4;
    private static final int NAV_MENU = 5;
    private static final int NAV_CONFIRM = 8;
    private static final int NAV_ROW_SLOTS = 9;
    private static final int NAV_ROW_END = DEPOSIT_SLOTS + NAV_ROW_SLOTS;

    public static void open(ServerPlayer player, EconomyManager manager) {
        MenuUiSupport.openMenu(player, "Sell", (id, inv) -> new SellMenu(id, inv, player, manager));
    }

    private static class SellMenu extends CompatMenu {
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
            navContainer.clearContent();
            navContainer.setItem(NAV_BALANCE, MenuUiSupport.createBalanceItem(viewer));

            navContainer.setItem(NAV_HELP, MenuUiSupport.button(Items.BOOK, "How this works", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Drop items in the slots above."),
                    MenuUiSupport.hint("Only items with a sell price fit."),
                    MenuUiSupport.hint("Nothing is sold until you confirm."),
                    MenuUiSupport.hint("Closing gives everything back.")));

            navContainer.setItem(NAV_FILL, MenuUiSupport.button(Items.HOPPER, "Add everything sellable",
                    ChatFormatting.AQUA, MenuUiSupport.hint("Pulls every sellable item from your inventory")));

            navContainer.setItem(NAV_MENU, MenuUiSupport.button(Items.NETHER_STAR, "Main menu", ChatFormatting.YELLOW));

            SellPreview preview = previewTotals();
            navContainer.setItem(NAV_CONFIRM, MenuUiSupport.confirmButton("Confirm",
                    MenuUiSupport.hint("Sells the items above"),
                    MenuUiSupport.labeledValue("Items", String.valueOf(preview.count()), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Total", EconomyCraft.formatMoney(preview.total()), MenuUiSupport.LABEL_PRIMARY_COLOR)));

            MenuUiSupport.fillFooter(navContainer);
        }

        private void fillFromInventory(Player player) {
            Inventory inv = player.getInventory();
            int moved = 0;
            for (int i = 0; i < SellService.MAIN_INVENTORY_SLOTS; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                if (SellService.sellableResolved(prices, stack) == null) continue;

                ItemStack remainder = depositContainer.addItem(stack.copy());
                int placed = stack.getCount() - remainder.getCount();
                if (placed <= 0) continue;
                stack.shrink(placed);
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                moved += placed;
            }

            if (moved == 0) {
                viewer.sendSystemMessage(MenuUiSupport.line("Nothing in your inventory can be sold.", ChatFormatting.RED));
            }
            renderNavRow();
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

                Long potential = split.serverRemaining() > 0 ? safeMultiply(unitSell, split.serverRemaining()) : null;
                if (potential != null) {
                    if (EconomyConfig.get().dailySellLimit > 0 && manager.tryRecordDailySell(player.getUUID(), potential)) {
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
                player.sendSystemMessage(Component.literal("Successfully sold " + totalSold + " item" + (totalSold == 1 ? "" : "s") +
                                " for " + EconomyCraft.formatMoney(totalPayout) +
                                (orderGivenTotal > 0 ? " (" + orderGivenTotal + " to open orders for a better price)" : "") + ".")
                        .withStyle(ChatFormatting.GREEN));
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
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot >= DEPOSIT_SLOTS && slot < NAV_ROW_END) {
                if (kind == ClickKind.PICKUP || kind == ClickKind.QUICK_MOVE) {
                    int navSlot = slot - DEPOSIT_SLOTS;
                    if (navSlot == NAV_CONFIRM) {
                        performSale((ServerPlayer) player);
                    } else if (navSlot == NAV_FILL) {
                        fillFromInventory(player);
                    } else if (navSlot == NAV_MENU) {
                        player.closeContainer();
                        HubUi.open((ServerPlayer) player);
                    }
                }
                return true;
            }
            if (slot >= 0 && slot < DEPOSIT_SLOTS) {
                ItemStack carried = this.getCarried();
                if (!carried.isEmpty() && SellService.sellableResolved(prices, carried) == null) {
                    rejectUnsellable(player, carried);
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void afterClick(int slot, int dragType, ClickKind kind, Player player) {
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
            clearContainer(player, depositContainer);
        }

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
