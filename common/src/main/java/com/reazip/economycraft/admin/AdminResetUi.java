package com.reazip.economycraft.admin;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.auction.AuctionExpiration;
import com.reazip.economycraft.orders.OrderFulfillment;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ConfirmUi;
import com.reazip.economycraft.util.EconomyPermissions;
import com.reazip.economycraft.util.EconomyPermissions.Nodes;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class AdminResetUi {
    private AdminResetUi() {}

    private static final int SIZE = 27;
    private static final int RESET_BALANCES = 2;
    private static final int RESET_DAILY_REWARD = 4;
    private static final int RESET_DAILY_SELL = 6;
    private static final int CLEAR_AUCTIONS = 11;
    private static final int CLEAR_ORDERS = 13;
    private static final int RESET_EVERYTHING = 15;
    private static final int BACK = 22;

    public static void open(ServerPlayer player, EconomyManager eco) {
        if (!MenuUiSupport.checkOrDeny(player, EconomyPermissions.checkAdmin(player, Nodes.ADMIN_RESET))) return;
        MenuUiSupport.openMenu(player, "Reset Tools", (id, inv) -> new ResetMenu(id, inv, player, eco));
    }

    private static class ResetMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final SimpleContainer container = new SimpleContainer(SIZE);

        ResetMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco) {
            super(MenuType.GENERIC_9x3, id);
            this.viewer = viewer;
            this.eco = eco;

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, SIZE)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 3 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private void render() {
            container.clearContent();

            container.setItem(RESET_BALANCES, MenuUiSupport.button(Items.GOLD_INGOT, "Reset All Balances", ChatFormatting.RED,
                    MenuUiSupport.hint("Sets every player's balance"),
                    MenuUiSupport.hint("back to the starting balance.")));

            container.setItem(RESET_DAILY_REWARD, MenuUiSupport.button(Items.CLOCK, "Reset Daily Reward Data", ChatFormatting.RED,
                    MenuUiSupport.hint("Everyone can claim their"),
                    MenuUiSupport.hint("daily reward again immediately.")));

            container.setItem(RESET_DAILY_SELL, MenuUiSupport.button(Items.HOPPER, "Reset Daily Sell Limits", ChatFormatting.RED,
                    MenuUiSupport.hint("Everyone's daily sell limit"),
                    MenuUiSupport.hint("resets to full immediately.")));

            container.setItem(CLEAR_AUCTIONS, MenuUiSupport.button(Items.CHEST, "Clear Auctions", ChatFormatting.RED,
                    MenuUiSupport.hint("Cancels every active listing."),
                    MenuUiSupport.hint("Items are returned to sellers.")));

            container.setItem(CLEAR_ORDERS, MenuUiSupport.button(Items.WRITABLE_BOOK, "Clear Orders", ChatFormatting.RED,
                    MenuUiSupport.hint("Cancels every open order."),
                    MenuUiSupport.hint("Escrowed money is refunded.")));

            container.setItem(RESET_EVERYTHING, MenuUiSupport.button(Items.TNT, "Reset Entire Economy", ChatFormatting.DARK_RED,
                    MenuUiSupport.line("Everything above, all at once.", ChatFormatting.RED)));

            container.setItem(BACK, MenuUiSupport.button(Items.NETHER_STAR, "Back", ChatFormatting.YELLOW));
            MenuUiSupport.fillBackground(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= SIZE) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;
            if (!EconomyPermissions.checkAdmin(viewer, Nodes.ADMIN_RESET)) return true;

            switch (slot) {
                case RESET_BALANCES -> {
                    EconomySounds.click(viewer);
                    confirmResetBalances(viewer, eco);
                }
                case RESET_DAILY_REWARD -> {
                    EconomySounds.click(viewer);
                    confirmResetDailyReward(viewer, eco);
                }
                case RESET_DAILY_SELL -> {
                    EconomySounds.click(viewer);
                    confirmResetDailySell(viewer, eco);
                }
                case CLEAR_AUCTIONS -> {
                    EconomySounds.click(viewer);
                    confirmClearAuctions(viewer, eco);
                }
                case CLEAR_ORDERS -> {
                    EconomySounds.click(viewer);
                    confirmClearOrders(viewer, eco);
                }
                case RESET_EVERYTHING -> {
                    EconomySounds.click(viewer);
                    confirmResetEverything(viewer, eco);
                }
                case BACK -> {
                    EconomySounds.click(viewer);
                    AdminUi.open(viewer, eco);
                }
                default -> {
                }
            }
            return true;
        }
    }

    private static void confirmResetBalances(ServerPlayer viewer, EconomyManager eco) {
        long starting = EconomyConfig.get().startingBalance;
        ConfirmUi.open(viewer, "Reset all balances?", warningIcon(Items.GOLD_INGOT, "Reset All Balances"),
                "Reset balances",
                List.of(MenuUiSupport.line("Every player's balance is set to " + EconomyCraft.formatMoney(starting) + ".", ChatFormatting.RED),
                        MenuUiSupport.hint("This cannot be undone.")),
                p -> {
                    int changed = eco.resetAllBalances();
                    announce(p, "Reset " + changed + " balance" + (changed == 1 ? "" : "s")
                            + " to " + EconomyCraft.formatMoney(starting) + ".");
                    open(p, eco);
                },
                p -> open(p, eco));
    }

    private static void confirmResetDailyReward(ServerPlayer viewer, EconomyManager eco) {
        ConfirmUi.open(viewer, "Reset daily reward data?", warningIcon(Items.CLOCK, "Reset Daily Reward Data"),
                "Reset daily rewards",
                List.of(MenuUiSupport.line("Everyone can claim their daily reward again immediately.", ChatFormatting.RED),
                        MenuUiSupport.hint("Balances are not affected.")),
                p -> {
                    eco.resetDailyRewards();
                    announce(p, "Reset daily reward data.");
                    open(p, eco);
                },
                p -> open(p, eco));
    }

    private static void confirmResetDailySell(ServerPlayer viewer, EconomyManager eco) {
        ConfirmUi.open(viewer, "Reset daily sell limits?", warningIcon(Items.HOPPER, "Reset Daily Sell Limits"),
                "Reset sell limits",
                List.of(MenuUiSupport.line("Everyone's daily sell limit resets to full immediately.", ChatFormatting.RED),
                        MenuUiSupport.hint("Balances are not affected.")),
                p -> {
                    eco.resetDailySellLimits();
                    announce(p, "Reset daily sell limits.");
                    open(p, eco);
                },
                p -> open(p, eco));
    }

    private static void confirmClearAuctions(ServerPlayer viewer, EconomyManager eco) {
        ConfirmUi.open(viewer, "Clear all auctions?", warningIcon(Items.CHEST, "Clear Auctions"),
                "Clear auctions",
                List.of(MenuUiSupport.line("Every active auction listing is cancelled.", ChatFormatting.RED),
                        MenuUiSupport.hint("Items are returned to sellers' deliveries.")),
                p -> {
                    int cleared = AuctionExpiration.clearAll(eco);
                    announce(p, "Cleared " + cleared + " auction listing" + (cleared == 1 ? "" : "s") + ".");
                    open(p, eco);
                },
                p -> open(p, eco));
    }

    private static void confirmClearOrders(ServerPlayer viewer, EconomyManager eco) {
        ConfirmUi.open(viewer, "Clear all orders?", warningIcon(Items.WRITABLE_BOOK, "Clear Orders"),
                "Clear orders",
                List.of(MenuUiSupport.line("Every open order is cancelled.", ChatFormatting.RED),
                        MenuUiSupport.hint("Escrowed money is refunded to requesters.")),
                p -> {
                    int cleared = OrderFulfillment.clearAll(eco);
                    announce(p, "Cleared " + cleared + " order" + (cleared == 1 ? "" : "s") + ".");
                    open(p, eco);
                },
                p -> open(p, eco));
    }

    private static void confirmResetEverything(ServerPlayer viewer, EconomyManager eco) {
        ConfirmUi.open(viewer, "Reset the entire economy?", warningIcon(Items.TNT, "Reset Entire Economy"),
                "Reset everything",
                List.of(MenuUiSupport.line("Resets balances, daily reward data, daily sell limits", ChatFormatting.RED),
                        MenuUiSupport.line("and leaderboard stats.", ChatFormatting.RED),
                        MenuUiSupport.line("Cancels every auction and order.", ChatFormatting.RED),
                        MenuUiSupport.line("Deletes the entire transaction log.", ChatFormatting.RED),
                        MenuUiSupport.hint("This cannot be undone.")),
                p -> {
                    int changed = eco.resetAllBalances();
                    eco.resetDailyRewards();
                    eco.resetDailySellLimits();
                    eco.resetStats();
                    eco.resetTransactionLog();
                    int auctionsCleared = AuctionExpiration.clearAll(eco);
                    int ordersCleared = OrderFulfillment.clearAll(eco);
                    announce(p, "Reset the entire economy: " + changed + " balance" + (changed == 1 ? "" : "s")
                            + ", " + auctionsCleared + " auction" + (auctionsCleared == 1 ? "" : "s")
                            + ", " + ordersCleared + " order" + (ordersCleared == 1 ? "" : "s") + ".");
                    open(p, eco);
                },
                p -> open(p, eco));
    }

    private static ItemStack warningIcon(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)
                .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.RED)));
        return stack;
    }

    private static void announce(ServerPlayer admin, String message) {
        EconomySounds.click(admin);
        admin.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
    }
}
