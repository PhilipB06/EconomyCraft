package com.reazip.economycraft.sell;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.EconomySources;
import com.reazip.economycraft.HubUi;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.item.component.ItemContainerContents;

public final class SellUi {
    private SellUi() {}

    private static final int DEPOSIT_ROWS = 5;
    private static final int DEPOSIT_SLOTS = DEPOSIT_ROWS * 9;
    private static final int NAV_BALANCE = 0;
    private static final int NAV_SHULKER = 2;
    private static final int NAV_FILL = 3;
    private static final int NAV_HELP = 4;
    private static final int NAV_MENU = 5;
    private static final int NAV_CONFIRM = 8;
    private static final int NAV_ROW_SLOTS = 9;
    private static final int NAV_ROW_END = DEPOSIT_SLOTS + NAV_ROW_SLOTS;

    private enum ShulkerSellMode { DISALLOW, CONTENTS_ONLY, EVERYTHING }

    private static final int SHULKER_SLOTS = 27;

    public static void open(ServerPlayer player, EconomyManager manager) {
        MenuUiSupport.openMenu(player, "Sell", (id, inv) -> new SellMenu(id, inv, player, manager));
    }

    private static class SellMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager manager;
        private final PriceRegistry prices;
        private final SimpleContainer depositContainer = new SimpleContainer(DEPOSIT_SLOTS);
        private final SimpleContainer navContainer = new SimpleContainer(9);
        private ShulkerSellMode shulkerMode = ShulkerSellMode.DISALLOW;

        SellMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager manager) {
            super(MenuType.GENERIC_9x6, id);
            this.viewer = viewer;
            this.manager = manager;
            this.prices = manager.getPrices();

            for (Slot slot : MenuUiSupport.openGridSlots(depositContainer, DEPOSIT_SLOTS, this::isDepositAcceptable)) {
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

        private static final class PreviewAccumulator {
            int count;
            long total;
        }

        private void addPreview(PreviewAccumulator acc, ItemStack stack, Long unitSell) {
            if (unitSell == null) return;
            Long value = safeMultiply(unitSell, stack.getCount());
            if (value == null) return;
            Long sum = safeAdd(acc.total, value);
            if (sum == null) return;
            acc.total = sum;
            acc.count += stack.getCount();
        }

        private void previewStack(ItemStack stack, PreviewAccumulator acc) {
            if (SellService.sellableResolved(prices, stack) == null) return;
            addPreview(acc, stack, prices.getUnitSell(stack));
        }

        private NonNullList<ItemStack> readShulkerContents(ItemStack box) {
            ItemContainerContents contents = box.get(DataComponents.CONTAINER);
            if (contents == null) return null;

            NonNullList<ItemStack> inner = NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY);
            contents.copyInto(inner);
            return inner;
        }

        private boolean isShulkerCandidate(ItemStack stack) {
            return stack.getCount() == 1 && SellService.isShulkerBox(stack) && MenuUiSupport.hasContainerContents(stack);
        }

        private boolean hasSellableShulkerContent(ItemStack box) {
            NonNullList<ItemStack> inner = readShulkerContents(box);
            if (inner == null) return false;
            for (ItemStack innerStack : inner) {
                if (!innerStack.isEmpty() && SellService.sellableResolved(prices, innerStack) != null) return true;
            }
            return false;
        }

        private boolean previewShulkerContents(ItemStack box, PreviewAccumulator acc) {
            NonNullList<ItemStack> inner = readShulkerContents(box);
            if (inner == null) return true;

            boolean allSellable = true;
            long before = acc.total;
            for (ItemStack innerStack : inner) {
                if (innerStack.isEmpty()) continue;
                if (SellService.sellableResolved(prices, innerStack) == null) {
                    allSellable = false;
                    continue;
                }
                addPreview(acc, innerStack, prices.getUnitSell(innerStack));
            }

            long contentsValue = acc.total - before;
            if (allSellable && EconomyConfig.get().dailySellLimit > 0
                    && contentsValue > manager.getDailySellRemaining(viewer.getUUID())) {
                allSellable = false;
            }
            if (allSellable && contentsValue > EconomyManager.MAX - manager.getBalance(viewer.getUUID(), true)) {
                allSellable = false;
            }
            return allSellable;
        }

        private SellPreview previewTotals() {
            PreviewAccumulator acc = new PreviewAccumulator();
            for (int i = 0; i < DEPOSIT_SLOTS; i++) {
                ItemStack stack = depositContainer.getItem(i);
                if (stack.isEmpty()) continue;

                if (shulkerMode != ShulkerSellMode.DISALLOW && isShulkerCandidate(stack)) {
                    boolean allContentsSellable = previewShulkerContents(stack, acc);
                    if (shulkerMode == ShulkerSellMode.CONTENTS_ONLY) continue;
                    if (allContentsSellable && SellService.sellableIgnoringContents(prices, stack) != null) {
                        addPreview(acc, stack, prices.getUnitSell(stack));
                    }
                    continue;
                }

                previewStack(stack, acc);
            }
            return new SellPreview(acc.count, acc.total);
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

            navContainer.setItem(NAV_SHULKER, MenuUiSupport.button(Items.SHULKER_BOX, "Shulker boxes",
                    ChatFormatting.LIGHT_PURPLE,
                    MenuUiSupport.italicHint("Click to cycle"),
                    MenuUiSupport.toggleOption("Don't accept shulkers", shulkerMode == ShulkerSellMode.DISALLOW),
                    MenuUiSupport.toggleOption("Sell only contents", shulkerMode == ShulkerSellMode.CONTENTS_ONLY),
                    MenuUiSupport.toggleOption("Sell everything incl. box", shulkerMode == ShulkerSellMode.EVERYTHING)));

            navContainer.setItem(NAV_MENU, MenuUiSupport.button(Items.NETHER_STAR, "Main menu", ChatFormatting.YELLOW));

            SellPreview preview = previewTotals();
            navContainer.setItem(NAV_CONFIRM, MenuUiSupport.confirmButton("Confirm",
                    MenuUiSupport.hint("Sells the items above"),
                    MenuUiSupport.labeledValue("Items", String.valueOf(preview.count()), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Total", EconomyCraft.formatMoney(preview.total()), MenuUiSupport.LABEL_PRIMARY_COLOR)));

            MenuUiSupport.fillFooter(navContainer);
        }

        private boolean isDepositAcceptable(ItemStack stack) {
            if (isShulkerCandidate(stack)) {
                return shulkerMode != ShulkerSellMode.DISALLOW && hasSellableShulkerContent(stack);
            }
            return SellService.sellableResolved(prices, stack) != null;
        }

        private void cycleShulkerMode() {
            shulkerMode = switch (shulkerMode) {
                case DISALLOW -> ShulkerSellMode.CONTENTS_ONLY;
                case CONTENTS_ONLY -> ShulkerSellMode.EVERYTHING;
                case EVERYTHING -> ShulkerSellMode.DISALLOW;
            };
            renderNavRow();
        }

        private void fillFromInventory(Player player) {
            Inventory inv = player.getInventory();
            int moved = 0;
            for (int i = 0; i < SellService.MAIN_INVENTORY_SLOTS; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                if (!isDepositAcceptable(stack)) continue;

                ItemStack remainder = depositContainer.addItem(stack.copy());
                int placed = stack.getCount() - remainder.getCount();
                if (placed <= 0) continue;
                stack.shrink(placed);
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                moved += placed;
            }

            if (moved == 0) {
                EconomySounds.failure(viewer);
                viewer.sendSystemMessage(MenuUiSupport.line("Nothing in your inventory can be sold.", ChatFormatting.RED));
            } else {
                EconomySounds.itemsStored(viewer);
            }
            renderNavRow();
        }

        private static final class SaleTotals {
            int orderGiven;
            long orderPayout;
            int serverSold;
            long serverPayout;
            int limitBlocked;
            int balanceBlocked;
            int shulkerBoxesKept;
        }

        private void sellStack(ServerPlayer player, ItemStack stack, SaleTotals totals) {
            Long unitSell = prices.getUnitSell(stack);
            if (unitSell == null) return;

            SellService.SaleSplit split = SellService.sellHandWithRouting(manager, player, stack, stack.getCount(), unitSell);
            totals.orderGiven += split.orderGiven();
            totals.orderPayout += split.orderPayout();
            if (split.serverRemaining() <= 0) return;

            Long potential = safeMultiply(unitSell, split.serverRemaining());
            if (potential == null) return;

            if (EconomyConfig.get().dailySellLimit > 0
                    && potential > manager.getDailySellRemaining(player.getUUID())) {
                totals.limitBlocked += split.serverRemaining();
                return;
            }

            String detail = EconomyCraft.describeItem(split.serverRemaining(), stack.getHoverName().getString());
            var result = manager.addMoney(player.getUUID(), potential, EconomySources.SHOP_SALE, detail);
            if (!result.successful()) {
                totals.balanceBlocked += split.serverRemaining();
                return;
            }

            if (EconomyConfig.get().dailySellLimit > 0) {
                manager.tryRecordDailySell(player.getUUID(), potential);
            }
            totals.serverSold += split.serverRemaining();
            totals.serverPayout += potential;
            stack.shrink(split.serverRemaining());
        }

        private void sellShulkerContents(ServerPlayer player, ItemStack box, SaleTotals totals) {
            NonNullList<ItemStack> inner = readShulkerContents(box);
            if (inner == null) return;

            for (ItemStack innerStack : inner) {
                if (innerStack.isEmpty()) continue;
                if (SellService.sellableResolved(prices, innerStack) == null) continue;
                sellStack(player, innerStack, totals);
            }

            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inner));
        }

        private void performSale(ServerPlayer player) {
            SaleTotals totals = new SaleTotals();

            for (int i = 0; i < DEPOSIT_SLOTS; i++) {
                ItemStack stack = depositContainer.getItem(i);
                if (stack.isEmpty()) continue;

                boolean keepBox = false;
                if (shulkerMode != ShulkerSellMode.DISALLOW && isShulkerCandidate(stack)) {
                    sellShulkerContents(player, stack, totals);
                    keepBox = shulkerMode == ShulkerSellMode.CONTENTS_ONLY;
                }
                if (keepBox) continue;

                if (SellService.sellableResolved(prices, stack) != null) {
                    sellStack(player, stack, totals);
                } else if (isShulkerCandidate(stack)) {
                    totals.shulkerBoxesKept++;
                }

                if (stack.isEmpty()) depositContainer.setItem(i, ItemStack.EMPTY);
            }

            int totalSold = totals.orderGiven + totals.serverSold;
            if (totalSold > 0) {
                EconomySounds.success(player);
                long totalPayout = totals.orderPayout + totals.serverPayout;
                player.sendSystemMessage(Component.literal("Successfully sold " + totalSold + " item" + (totalSold == 1 ? "" : "s") +
                                " for " + EconomyCraft.formatMoney(totalPayout) +
                                (totals.orderGiven > 0 ? " (" + totals.orderGiven + " to open orders for a better price)" : "") + ".")
                        .withStyle(ChatFormatting.GREEN));
            }

            if (totals.limitBlocked > 0) {
                long remaining = manager.getDailySellRemaining(player.getUUID());
                player.sendSystemMessage(Component.literal(totals.limitBlocked + " item" + (totals.limitBlocked == 1 ? "" : "s") +
                                " was not sold: daily sell limit reached" +
                                (remaining > 0 ? " (" + EconomyCraft.formatMoney(remaining) + " left today)." : "."))
                        .withStyle(ChatFormatting.RED));
            }

            if (totals.balanceBlocked > 0) {
                player.sendSystemMessage(Component.literal(totals.balanceBlocked + " item"
                                + (totals.balanceBlocked == 1 ? " was" : "s were")
                                + " not sold: your balance is too high to receive the payout.")
                        .withStyle(ChatFormatting.RED));
            }

            if (totals.shulkerBoxesKept > 0) {
                player.sendSystemMessage(Component.literal(totals.shulkerBoxesKept + " shulker box"
                                + (totals.shulkerBoxesKept == 1 ? " was" : "es were")
                                + " not sold: it still has unsellable contents.")
                        .withStyle(ChatFormatting.RED));
            }

            if (totalSold == 0) {
                EconomySounds.failure(player);
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
                    } else if (navSlot == NAV_SHULKER) {
                        EconomySounds.click((ServerPlayer) player);
                        cycleShulkerMode();
                    } else if (navSlot == NAV_MENU) {
                        EconomySounds.click((ServerPlayer) player);
                        player.closeContainer();
                        HubUi.open((ServerPlayer) player);
                    }
                }
                return true;
            }
            if (slot >= 0 && slot < DEPOSIT_SLOTS) {
                ItemStack carried = this.getCarried();
                if (!carried.isEmpty() && !isDepositAcceptable(carried)) {
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
                EconomySounds.failure(sp);
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
                if (!isDepositAcceptable(original)) {
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
