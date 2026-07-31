package com.reazip.economycraft.admin;

import com.reazip.economycraft.EconomyCommands;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ItemsCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.NumberInputUi;
import com.reazip.economycraft.util.TextInputUi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class AdminSettingsUi {
    private AdminSettingsUi() {}

    private static final int SIZE = 45;
    private static final int BACK = 36;

    public static void open(ServerPlayer player, EconomyManager eco) {
        MenuUiSupport.openMenu(player, "Settings", (id, inv) -> new SettingsMenu(id, inv, player, eco));
    }

    public static void applyRuntimeSettings(MinecraftServer server) {
        if (server == null) return;
        EconomyCommands.resyncCommands(server);
    }

    private static void save(ServerPlayer player) {
        EconomyConfig.save();
        applyRuntimeSettings(player.level().getServer());
    }

    private enum Setting {
        STARTING_BALANCE(10, "Starting Balance", "Money a brand new player begins with."),
        DAILY_AMOUNT(11, "Daily Reward", "Paid out once a day per player."),
        DAILY_SELL_LIMIT(12, "Daily Sell Limit", "Most a player can earn selling per day."),
        TAX_RATE(13, "Tax Rate", "Cut the server takes from trades and orders."),
        PVP_LOSS(14, "PvP Money Loss", "Share of the balance a killer takes."),
        SEPARATOR(15, "Number Separator", "Thousands separator shown in prices.",
                "Type \"space\" for a blank one."),
        SERVER_SHOP(19, "Server Shop", "The built-in shop with fixed prices."),
        SHOP(20, "Player Shop", "The marketplace players list their own items on."),
        ORDERS(21, "Orders", "The request board players post wanted items on."),
        SELL(22, "Selling", "The /sell menu and right-click selling."),
        SCOREBOARD(23, "Balance Sidebar", "The balance leaderboard on the right."),
        STANDALONE(24, "Short Commands", "Allow /pay and /shop without the /eco prefix."),
        STANDALONE_ADMIN(25, "Short Admin Commands", "Allow /addmoney without the /eco prefix.");

        final int slot;
        final String label;
        final String description;
        final String extra;

        Setting(int slot, String label, String description) {
            this(slot, label, description, null);
        }

        Setting(int slot, String label, String description, String extra) {
            this.slot = slot;
            this.label = label;
            this.description = description;
            this.extra = extra;
        }
    }

    private static class SettingsMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final SimpleContainer container = new SimpleContainer(SIZE);

        SettingsMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco) {
            super(MenuType.GENERIC_9x5, id);
            this.viewer = viewer;
            this.eco = eco;

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

            container.setItem(4, MenuUiSupport.button(Items.BOOK, "Server Settings", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Click a setting to change it."),
                    MenuUiSupport.hint("Changes save straight to config.json.")));

            value(Setting.STARTING_BALANCE, Items.GOLD_INGOT, EconomyCraft.formatMoney(config.startingBalance));
            value(Setting.DAILY_AMOUNT, Items.CLOCK, EconomyCraft.formatMoney(config.dailyAmount));
            value(Setting.DAILY_SELL_LIMIT, Items.HOPPER, config.dailySellLimit <= 0
                    ? "No limit" : EconomyCraft.formatMoney(config.dailySellLimit));
            value(Setting.TAX_RATE, Items.PAPER, percent(config.taxRate));
            value(Setting.PVP_LOSS, Items.IRON_SWORD, config.pvpBalanceLossPercentage <= 0
                    ? "Off" : percent(config.pvpBalanceLossPercentage));
            value(Setting.SEPARATOR, Items.NAME_TAG, "\"" + config.balanceSeparator + "\" gives "
                    + EconomyCraft.formatMoney(1234567));

            toggle(Setting.SERVER_SHOP, config.serverShopEnabled);
            toggle(Setting.SHOP, config.shopEnabled);
            toggle(Setting.ORDERS, config.ordersEnabled);
            toggle(Setting.SELL, config.sellEnabled);
            toggle(Setting.SCOREBOARD, config.scoreboardEnabled);
            toggle(Setting.STANDALONE, config.standaloneCommands);
            toggle(Setting.STANDALONE_ADMIN, config.standaloneAdminCommands);

            container.setItem(BACK, MenuUiSupport.backButton());
            MenuUiSupport.fillBackground(container);
        }

        private void value(Setting setting, net.minecraft.world.item.Item icon, String current) {
            List<Component> lore = new ArrayList<>();
            lore.add(MenuUiSupport.hint(setting.description));
            if (setting.extra != null) lore.add(MenuUiSupport.italicHint(setting.extra));
            lore.add(MenuUiSupport.labeledValue("Now", current, MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.labeledValue("Click", "Change it", MenuUiSupport.LABEL_SECONDARY_COLOR));
            container.setItem(setting.slot, MenuUiSupport.button(icon, setting.label, ChatFormatting.AQUA,
                    lore.toArray(new Component[0])));
        }

        private void toggle(Setting setting, boolean enabled) {
            List<Component> lore = new ArrayList<>();
            lore.add(MenuUiSupport.hint(setting.description));
            if (setting.extra != null) lore.add(MenuUiSupport.italicHint(setting.extra));
            lore.add(MenuUiSupport.labeledValue("Now", enabled ? "On" : "Off", MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.labeledValue("Click", enabled ? "Turn off" : "Turn on",
                    MenuUiSupport.LABEL_SECONDARY_COLOR));
            container.setItem(setting.slot, MenuUiSupport.button(
                    enabled ? ItemsCompat.limeStainedGlassPane() : ItemsCompat.redStainedGlassPane(),
                    setting.label,
                    enabled ? ChatFormatting.GREEN : ChatFormatting.RED,
                    lore.toArray(new Component[0])));
        }

        private static String percent(double factor) {
            return Math.round(factor * 100) + "%";
        }

        private void editMoney(Setting setting, long current, long min, net.minecraft.world.item.Item icon,
                               java.util.function.LongConsumer apply) {
            NumberInputUi.openMoney(viewer, setting.label, new ItemStack(icon), setting.label, current, min,
                    EconomyManager.MAX,
                    (p, next) -> {
                        apply.accept(next);
                        save(p);
                        open(p, eco);
                    },
                    p -> open(p, eco));
        }

        private void editPercent(Setting setting, double current, net.minecraft.world.item.Item icon,
                                 java.util.function.DoubleConsumer apply) {
            NumberInputUi.openPercent(viewer, setting.label, new ItemStack(icon), setting.label,
                    Math.round(current * 100),
                    (p, next) -> {
                        apply.accept(next / 100.0);
                        save(p);
                        open(p, eco);
                    },
                    p -> open(p, eco));
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= SIZE) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot == BACK) {
                AdminUi.open(viewer, eco);
                return true;
            }

            for (Setting setting : Setting.values()) {
                if (setting.slot != slot) continue;
                EconomyConfig config = EconomyConfig.get();
                switch (setting) {
                    case STARTING_BALANCE -> editMoney(setting, config.startingBalance, 0, Items.GOLD_INGOT,
                            v -> EconomyConfig.get().startingBalance = v);
                    case DAILY_AMOUNT -> editMoney(setting, config.dailyAmount, 0, Items.CLOCK,
                            v -> EconomyConfig.get().dailyAmount = v);
                    case DAILY_SELL_LIMIT -> editMoney(setting, config.dailySellLimit, 0, Items.HOPPER,
                            v -> EconomyConfig.get().dailySellLimit = v);
                    case TAX_RATE -> editPercent(setting, config.taxRate, Items.PAPER,
                            v -> EconomyConfig.get().taxRate = v);
                    case PVP_LOSS -> editPercent(setting, config.pvpBalanceLossPercentage, Items.IRON_SWORD,
                            v -> EconomyConfig.get().pvpBalanceLossPercentage = v);
                    case SEPARATOR -> TextInputUi.open(viewer, "Number separator", config.balanceSeparator,
                            Items.NAME_TAG, "Use: ", "Type one character",
                            (p, text) -> {
                                EconomyConfig.get().balanceSeparator =
                                        text.equalsIgnoreCase("space") ? " " : text.substring(0, 1);
                                save(p);
                                open(p, eco);
                            });
                    case SERVER_SHOP -> {
                        config.serverShopEnabled = !config.serverShopEnabled;
                        save(viewer);
                        render();
                    }
                    case SHOP -> {
                        config.shopEnabled = !config.shopEnabled;
                        save(viewer);
                        render();
                    }
                    case ORDERS -> {
                        config.ordersEnabled = !config.ordersEnabled;
                        save(viewer);
                        render();
                    }
                    case SELL -> {
                        config.sellEnabled = !config.sellEnabled;
                        save(viewer);
                        render();
                    }
                    case SCOREBOARD -> {
                        eco.toggleScoreboard();
                        applyRuntimeSettings(viewer.level().getServer());
                        render();
                    }
                    case STANDALONE -> {
                        config.standaloneCommands = !config.standaloneCommands;
                        save(viewer);
                        render();
                    }
                    case STANDALONE_ADMIN -> {
                        config.standaloneAdminCommands = !config.standaloneAdminCommands;
                        save(viewer);
                        render();
                    }
                }
                return true;
            }
            return true;
        }
    }
}
