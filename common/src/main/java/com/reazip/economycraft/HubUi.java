package com.reazip.economycraft;

import com.reazip.economycraft.admin.AdminUi;
import com.reazip.economycraft.orders.OrdersUi;
import com.reazip.economycraft.sell.SellUi;
import com.reazip.economycraft.shop.ServerShopUi;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ItemPickerUi;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.NumberInputUi;
import com.reazip.economycraft.util.PermissionCompat;
import com.reazip.economycraft.util.PlayerPickerUi;
import net.minecraft.ChatFormatting;
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

import java.util.ArrayList;
import java.util.List;

public final class HubUi {
    private HubUi() {}

    private static final int SIZE = 45;
    private static final int BALANCE = 4;
    private static final int SERVER_SHOP = 10;
    private static final int PLAYER_SHOP = 12;
    private static final int SELL = 14;
    private static final int ORDERS = 16;
    private static final int DAILY = 19;
    private static final int PAY = 21;
    private static final int TOP = 23;
    private static final int WORTH = 25;
    private static final int DELIVERIES = 30;
    private static final int HELP = 32;
    private static final int CLOSE = 40;
    private static final int ADMIN = 44;

    public static void open(ServerPlayer player) {
        MenuUiSupport.openMenu(player, "EconomyCraft", (id, inv) -> new HubMenu(id, inv, player));
    }

    public static void openTop(ServerPlayer player) {
        MenuUiSupport.openMenu(player, "Top Balances", (id, inv) -> new TopMenu(id, inv, player));
    }

    private static void startPay(ServerPlayer player) {
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        PlayerPickerUi.open(player, "Pay who?", false,
                (picker, target) -> {
                    long balance = eco.getBalance(picker.getUUID(), true);
                    if (balance <= 0) {
                        picker.sendSystemMessage(MenuUiSupport.line("You have no money to send.", ChatFormatting.RED));
                        open(picker);
                        return;
                    }
                    ItemStack subject = MenuUiSupport.createBalanceItem(eco, target.id(), null, target.name());
                    NumberInputUi.openMoney(picker, "Pay " + target.name(), subject, "Amount",
                            Math.min(100, balance), 1, balance,
                            "Confirm and pay",
                            amount -> List.of(
                                    MenuUiSupport.labeledValue("Sending", EconomyCraft.formatMoney(amount),
                                            MenuUiSupport.LABEL_PRIMARY_COLOR),
                                    MenuUiSupport.labeledValue("To", target.name(), MenuUiSupport.LABEL_PRIMARY_COLOR),
                                    MenuUiSupport.hint("This cannot be undone.")),
                            (p, amount) -> pay(p, target, amount),
                            HubUi::startPay);
                },
                HubUi::open);
    }

    private static void pay(ServerPlayer player, PlayerPickerUi.Target target, long amount) {
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        if (eco.pay(player.getUUID(), target.id(), amount)) {
            player.sendSystemMessage(Component.literal("Paid " + EconomyCraft.formatMoney(amount) + " to " + target.name())
                    .withStyle(ChatFormatting.GREEN));
            ServerPlayer online = player.level().getServer().getPlayerList().getPlayer(target.id());
            if (online != null) {
                online.sendSystemMessage(Component.literal(player.getName().getString() + " sent you "
                        + EconomyCraft.formatMoney(amount)).withStyle(ChatFormatting.GREEN));
            }
        } else {
            player.sendSystemMessage(MenuUiSupport.line("Not enough balance.", ChatFormatting.RED));
        }
        open(player);
    }

    private static void startWorth(ServerPlayer player) {
        PriceRegistry prices = EconomyCraft.getManager(player.level().getServer()).getPrices();
        ItemPickerUi.open(player, "Check an item's value", ItemPickerUi.Source.INVENTORY_AND_ALL, null,
                (picker, choice) -> {
                    PriceRegistry.PriceEntry entry = prices.resolve(choice.prototype());
                    String name = choice.prototype().getHoverName().getString();
                    if (entry == null || (entry.unitBuy() <= 0 && entry.unitSell() <= 0)) {
                        picker.sendSystemMessage(MenuUiSupport.line(name + " has no price on this server.", ChatFormatting.RED));
                    } else {
                        picker.sendSystemMessage(Component.literal(name
                                        + " - Buy: " + (entry.unitBuy() > 0 ? EconomyCraft.formatMoney(entry.unitBuy()) : "not for sale")
                                        + ", Sell: " + (entry.unitSell() > 0 ? EconomyCraft.formatMoney(entry.unitSell()) : "not sellable"))
                                .withStyle(ChatFormatting.YELLOW));
                    }
                    startWorth(picker);
                },
                HubUi::open);
    }

    private static class HubMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final SimpleContainer container = new SimpleContainer(SIZE);

        HubMenu(int id, Inventory inv, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x5, id);
            this.viewer = viewer;
            this.eco = EconomyCraft.getManager(viewer.level().getServer());

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, SIZE)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 5 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private void render() {
            container.clearContent();
            EconomyConfig config = EconomyConfig.get();

            ItemStack balance = MenuUiSupport.createBalanceItem(viewer);
            List<Component> balanceLore = new ArrayList<>();
            balanceLore.add(MenuUiSupport.balanceLore(eco.getBalance(viewer.getUUID(), true)));
            if (config.dailySellLimit > 0) {
                balanceLore.add(MenuUiSupport.labeledValue("Sell limit left today",
                        EconomyCraft.formatMoney(eco.getDailySellRemaining(viewer.getUUID())),
                        MenuUiSupport.LABEL_PRIMARY_COLOR));
            }
            balance.set(DataComponents.LORE, new ItemLore(balanceLore));
            container.setItem(BALANCE, balance);

            if (config.serverShopEnabled) {
                container.setItem(SERVER_SHOP, MenuUiSupport.button(Items.EMERALD, "Server Shop", ChatFormatting.GREEN,
                        MenuUiSupport.hint("Buy and sell at fixed prices."),
                        MenuUiSupport.hint("Stock never runs out.")));
            }

            if (config.shopEnabled) {
                container.setItem(PLAYER_SHOP, MenuUiSupport.button(Items.CHEST, "Player Shop", ChatFormatting.GOLD,
                        MenuUiSupport.hint("Buy from other players,"),
                        MenuUiSupport.hint("or put your own items up for sale.")));
            }

            if (config.sellEnabled) {
                container.setItem(SELL, MenuUiSupport.button(Items.GOLD_INGOT, "Sell Items", ChatFormatting.YELLOW,
                        MenuUiSupport.hint("Drop items in and get paid."),
                        MenuUiSupport.hint("Open orders are matched first.")));
            }

            if (config.ordersEnabled) {
                container.setItem(ORDERS, MenuUiSupport.button(Items.WRITABLE_BOOK, "Orders", ChatFormatting.AQUA,
                        MenuUiSupport.hint("Ask for an item and name your price,"),
                        MenuUiSupport.hint("or earn money filling other requests.")));
            }

            boolean claimed = eco.hasClaimedDailyToday(viewer.getUUID());
            container.setItem(DAILY, MenuUiSupport.button(Items.CLOCK, "Daily Reward",
                    claimed ? ChatFormatting.GRAY : ChatFormatting.GOLD,
                    MenuUiSupport.labeledValue("Amount", EconomyCraft.formatMoney(config.dailyAmount),
                            MenuUiSupport.LABEL_PRIMARY_COLOR),
                    claimed
                            ? MenuUiSupport.line("Already claimed today", ChatFormatting.RED)
                            : MenuUiSupport.line("Ready to claim", ChatFormatting.GREEN),
                    MenuUiSupport.hint(claimed ? "Come back tomorrow." : "Once per day. Click to claim.")));

            container.setItem(PAY, MenuUiSupport.button(Items.PAPER, "Pay a Player", ChatFormatting.GREEN,
                    MenuUiSupport.hint("Send money to someone else.")));

            container.setItem(TOP, MenuUiSupport.button(Items.GOLDEN_APPLE, "Top Balances", ChatFormatting.GOLD,
                    MenuUiSupport.hint("See who is richest on the server.")));

            container.setItem(WORTH, MenuUiSupport.button(Items.SPYGLASS, "Item Value", ChatFormatting.AQUA,
                    MenuUiSupport.hint("Look up what an item buys"),
                    MenuUiSupport.hint("and sells for.")));

            container.setItem(DELIVERIES, MenuUiSupport.button(Items.ENDER_CHEST, "Deliveries",
                    ChatFormatting.LIGHT_PURPLE,
                    MenuUiSupport.hint(eco.getDeliveries().hasDeliveries(viewer.getUUID())
                            ? "You have items waiting!"
                            : "Nothing waiting right now.")));

            container.setItem(HELP, MenuUiSupport.button(Items.BOOK, "How It Works", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Everything here works by clicking."),
                    MenuUiSupport.hint("Claim your daily reward, sell what"),
                    MenuUiSupport.hint("you mine, then buy what you need."),
                    MenuUiSupport.hint("Type /eco to reopen this menu.")));

            container.setItem(CLOSE, MenuUiSupport.closeButton());

            if (PermissionCompat.isAdmin(viewer)) {
                container.setItem(ADMIN, MenuUiSupport.button(Items.COMMAND_BLOCK, "Admin", ChatFormatting.LIGHT_PURPLE,
                        MenuUiSupport.hint("Edit the shop, settings and balances."),
                        MenuUiSupport.hint("Only operators can see this.")));
            }

            MenuUiSupport.fillBackground(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= SIZE) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            EconomyConfig config = EconomyConfig.get();
            switch (slot) {
                case SERVER_SHOP -> {
                    if (config.serverShopEnabled) {
                        ServerShopUi.open(viewer, eco);
                    }
                }
                case PLAYER_SHOP -> {
                    if (config.shopEnabled) {
                        ShopUi.open(viewer, eco.getShop());
                    }
                }
                case SELL -> {
                    if (config.sellEnabled) {
                        SellUi.open(viewer, eco);
                    }
                }
                case ORDERS -> {
                    if (config.ordersEnabled) {
                        OrdersUi.open(viewer, eco);
                    }
                }
                case DELIVERIES -> {
                    viewer.closeContainer();
                    OrdersUi.openClaims(viewer, eco);
                }
                case TOP -> openTop(viewer);
                case PAY -> {
                    viewer.closeContainer();
                    startPay(viewer);
                }
                case WORTH -> {
                    viewer.closeContainer();
                    startWorth(viewer);
                }
                case DAILY -> {
                    if (eco.claimDaily(viewer.getUUID())) {
                        viewer.sendSystemMessage(Component.literal("Claimed "
                                + EconomyCraft.formatMoney(config.dailyAmount)).withStyle(ChatFormatting.GREEN));
                    } else {
                        viewer.sendSystemMessage(MenuUiSupport.line("Already claimed today. Come back tomorrow.",
                                ChatFormatting.RED));
                    }
                    render();
                }
                case ADMIN -> {
                    if (PermissionCompat.isAdmin(viewer)) {
                        AdminUi.open(viewer, eco);
                    }
                }
                case CLOSE -> viewer.closeContainer();
                default -> {
                }
            }
            return true;
        }
    }

    private static class TopMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final SimpleContainer container = new SimpleContainer(27);

        TopMenu(int id, Inventory inv, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x3, id);
            this.viewer = viewer;

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 27)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 3 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private void render() {
            container.clearContent();
            EconomyManager eco = EconomyCraft.getManager(viewer.level().getServer());

            boolean any = false;
            for (int rank = 1; rank <= 10; rank++) {
                EconomyManager.LeaderboardEntry entry = eco.getLeaderboardEntry(rank);
                if (entry == null) break;
                any = true;

                ServerPlayer online = viewer.level().getServer().getPlayerList().getPlayer(entry.id());
                ItemStack head = MenuUiSupport.createBalanceItem(eco, entry.id(), online, entry.name());
                ChatFormatting nameColor = rank == 1 ? ChatFormatting.GOLD : MenuUiSupport.BALANCE_NAME_COLOR;
                head.set(DataComponents.CUSTOM_NAME, Component.literal("#" + rank + " " + entry.name())
                        .withStyle(s -> s.withItalic(false).withBold(true).withColor(nameColor)));
                head.set(DataComponents.LORE, new ItemLore(List.of(MenuUiSupport.balanceLore(entry.balance()))));
                head.setCount(Math.min(64, rank));
                container.setItem(rank <= 5 ? rank + 1 : rank + 5, head);
            }

            if (!any) {
                container.setItem(13, MenuUiSupport.button(Items.BARRIER, "No balances yet", ChatFormatting.RED));
            }

            container.setItem(22, MenuUiSupport.button(Items.NETHER_STAR, "Main menu", ChatFormatting.YELLOW));
            MenuUiSupport.fillBackground(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 27) return false;
            if (kind == ClickKind.PICKUP && slot == 22) {
                HubUi.open(viewer);
            }
            return true;
        }
    }
}
