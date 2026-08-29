package com.reazip.economycraft.admin;

import com.reazip.economycraft.EconomyCommands;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.EconomySounds;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class AdminSettingsUi {
    private AdminSettingsUi() {}

    private static final int SIZE = 45;
    private static final int NAV_ROW_START = 36;
    private static final int BACK = NAV_ROW_START;
    private static final int PREV = NAV_ROW_START + 3;
    private static final int PAGE_INDICATOR = NAV_ROW_START + 4;
    private static final int NEXT = NAV_ROW_START + 5;

    // Same inner-grid layout the settings screen has always used (rows 1-3, columns 1-7);
    // additional settings that don't fit here spill onto the next page.
    private static final int[] GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public static void open(ServerPlayer player, EconomyManager eco) {
        open(player, eco, 0);
    }

    private static void open(ServerPlayer player, EconomyManager eco, int page) {
        MenuUiSupport.openMenu(player, "Settings", (id, inv) -> new SettingsMenu(id, inv, player, eco, page));
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
        STARTING_BALANCE("Starting Balance", "Money a brand new player begins with."),
        DAILY_AMOUNT("Daily Reward", "Paid out once a day per player."),
        DAILY_SELL_LIMIT("Daily Sell Limit", "Most a player can earn selling per day."),
        TAX_RATE("Tax Rate", "Cut the server takes from trades and orders."),
        PVP_LOSS("PvP Money Loss", "Share of the balance a killer takes."),
        SEPARATOR("Number Separator", "Thousands separator shown in prices.",
                "Type \"space\" for a blank one."),
        SCOREBOARD("Balance Sidebar", "The balance leaderboard on the right."),
        SHOP("Shop", "The built-in shop with fixed prices."),
        AUCTION("Auction House", "The marketplace players list their own items on."),
        ORDERS("Orders", "The request board players post wanted items on."),
        SELL("Selling", "The /sell menu and right-click selling."),
        WORTH("Item Values", "The /worth command and Item Value menu."),
        STANDALONE("Short Commands", "Allow /pay, /shop and /ah without the /eco prefix."),
        STANDALONE_ADMIN("Short Admin Commands", "Allow /addmoney without the /eco prefix."),
        TRANSACTION_LOG("Transaction Logs", "Record every balance change to a daily log file."),
        TRANSACTION_LOG_RETENTION("Log Retention", "How many days of transaction logs to keep before deleting them."),
        DYNAMIC_PRICES("Dynamic Prices", "Scale shop buy prices with the active-player median balance."),
        DYNAMIC_PRICE_MIN_MULT("Min Multiplier", "Lowest allowed price scale, even if the median balance craters."),
        DYNAMIC_PRICE_MAX_MULT("Max Multiplier", "Highest allowed price scale, even if the median balance soars."),
        DYNAMIC_PRICE_ACTIVE_DAYS("Active Player Window", "Players must have logged in within this many days to count toward the median.",
                "0 = include every player, active or not."),
        ORDER_EXPIRATION_HOURS("Order Expiration", "Hours before an open order request auto-expires.",
                "0 = never expires."),
        AUCTION_EXPIRATION_HOURS("Auction Expiration", "Hours before an auction listing auto-expires.",
                "0 = never expires."),
        MAX_ACTIVE_ORDERS_PER_PLAYER("Max Order Requests", "Most open order requests a player can have at once.",
                "0 = no limit."),
        MAX_ACTIVE_AUCTIONS_PER_PLAYER("Max Auction Listings", "Most active auction listings a player can have at once.",
                "0 = no limit.");

        final String label;
        final String description;
        final String extra;

        Setting(String label, String description) {
            this(label, description, null);
        }

        Setting(String label, String description, String extra) {
            this.label = label;
            this.description = description;
            this.extra = extra;
        }
    }

    private static class SettingsMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final SimpleContainer container = new SimpleContainer(SIZE);
        private final Setting[] settings = Setting.values();
        private int page;

        SettingsMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, int page) {
            super(MenuType.GENERIC_9x5, id);
            this.viewer = viewer;
            this.eco = eco;
            this.page = page;

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

            int totalPages = MenuUiSupport.totalPages(settings.length, GRID_SLOTS.length);
            page = Math.min(page, totalPages - 1);
            int start = page * GRID_SLOTS.length;

            container.setItem(4, MenuUiSupport.button(Items.BOOK, "Server Settings", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Click a setting to change it."),
                    MenuUiSupport.hint("Changes save straight to config.json.")));

            for (int i = 0; i < GRID_SLOTS.length; i++) {
                int index = start + i;
                if (index >= settings.length) break;
                container.setItem(GRID_SLOTS[i], buildItem(settings[index], config));
            }

            container.setItem(BACK, MenuUiSupport.backButton());
            if (totalPages > 1) {
                if (page > 0) container.setItem(PREV, MenuUiSupport.prevPageButton());
                if (start + GRID_SLOTS.length < settings.length) container.setItem(NEXT, MenuUiSupport.nextPageButton());
                container.setItem(PAGE_INDICATOR, MenuUiSupport.pageIndicator(page, totalPages));
            }

            MenuUiSupport.fillBackground(container);
        }

        private ItemStack buildItem(Setting setting, EconomyConfig config) {
            return switch (setting) {
                case STARTING_BALANCE -> valueItem(setting, Items.GOLD_INGOT, EconomyCraft.formatMoney(config.startingBalance));
                case DAILY_AMOUNT -> valueItem(setting, Items.CLOCK, EconomyCraft.formatMoney(config.dailyAmount));
                case DAILY_SELL_LIMIT -> valueItem(setting, Items.HOPPER, config.dailySellLimit <= 0
                        ? "No limit" : EconomyCraft.formatMoney(config.dailySellLimit));
                case TAX_RATE -> valueItem(setting, Items.PAPER, percent(config.taxRate));
                case PVP_LOSS -> valueItem(setting, Items.IRON_SWORD, config.pvpBalanceLossPercentage <= 0
                        ? "Off" : percent(config.pvpBalanceLossPercentage));
                case SEPARATOR -> valueItem(setting, Items.NAME_TAG, "\"" + config.balanceSeparator + "\" gives "
                        + EconomyCraft.formatMoney(1234567));
                case SCOREBOARD -> toggleItem(setting, config.scoreboardEnabled);
                case SHOP -> toggleItem(setting, config.shopEnabled);
                case AUCTION -> toggleItem(setting, config.auctionEnabled);
                case ORDERS -> toggleItem(setting, config.ordersEnabled);
                case SELL -> toggleItem(setting, config.sellEnabled);
                case WORTH -> toggleItem(setting, config.worthEnabled);
                case STANDALONE -> toggleItem(setting, config.standaloneCommands);
                case STANDALONE_ADMIN -> toggleItem(setting, config.standaloneAdminCommands);
                case TRANSACTION_LOG -> toggleItem(setting, config.transactionLogEnabled);
                case TRANSACTION_LOG_RETENTION -> valueItem(setting, Items.MAP, days(config.transactionLogRetentionDays));
                case DYNAMIC_PRICES -> dynamicPricesItem(config);
                case DYNAMIC_PRICE_MIN_MULT -> valueItem(setting, Items.PAPER, EconomyCraft.formatMultiplier(config.dynamicPriceMinMultiplier));
                case DYNAMIC_PRICE_MAX_MULT -> valueItem(setting, Items.PAPER, EconomyCraft.formatMultiplier(config.dynamicPriceMaxMultiplier));
                case DYNAMIC_PRICE_ACTIVE_DAYS -> valueItem(setting, Items.CLOCK, activeDaysLabel(config.dynamicPriceMinActiveDays));
                case ORDER_EXPIRATION_HOURS -> valueItem(setting, Items.CLOCK, hours(config.orderExpirationHours));
                case AUCTION_EXPIRATION_HOURS -> valueItem(setting, Items.CLOCK, hours(config.auctionExpirationHours));
                case MAX_ACTIVE_ORDERS_PER_PLAYER -> valueItem(setting, Items.WRITABLE_BOOK, limit(config.maxActiveOrdersPerPlayer));
                case MAX_ACTIVE_AUCTIONS_PER_PLAYER -> valueItem(setting, Items.CHEST, limit(config.maxActiveAuctionsPerPlayer));
            };
        }

        private ItemStack dynamicPricesItem(EconomyConfig config) {
            List<Component> lore = new ArrayList<>();
            lore.add(MenuUiSupport.hint(Setting.DYNAMIC_PRICES.description));
            lore.add(MenuUiSupport.labeledValue("Now", config.dynamicPricesEnabled ? "On" : "Off",
                    MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.labeledValue("Current multiplier",
                    EconomyCraft.formatMultiplier(eco.getDynamicPriceMultiplier()), MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.labeledValue("Click", config.dynamicPricesEnabled ? "Turn off" : "Turn on",
                    MenuUiSupport.LABEL_SECONDARY_COLOR));
            return MenuUiSupport.button(
                    config.dynamicPricesEnabled ? ItemsCompat.limeStainedGlassPane() : ItemsCompat.redStainedGlassPane(),
                    Setting.DYNAMIC_PRICES.label, config.dynamicPricesEnabled ? ChatFormatting.GREEN : ChatFormatting.RED,
                    lore.toArray(new Component[0]));
        }

        private ItemStack valueItem(Setting setting, Item icon, String current) {
            List<Component> lore = new ArrayList<>();
            lore.add(MenuUiSupport.hint(setting.description));
            if (setting.extra != null) lore.add(MenuUiSupport.italicHint(setting.extra));
            lore.add(MenuUiSupport.labeledValue("Now", current, MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.labeledValue("Click", "Change it", MenuUiSupport.LABEL_SECONDARY_COLOR));
            return MenuUiSupport.button(icon, setting.label, ChatFormatting.AQUA, lore.toArray(new Component[0]));
        }

        private ItemStack toggleItem(Setting setting, boolean enabled) {
            List<Component> lore = new ArrayList<>();
            lore.add(MenuUiSupport.hint(setting.description));
            if (setting.extra != null) lore.add(MenuUiSupport.italicHint(setting.extra));
            lore.add(MenuUiSupport.labeledValue("Now", enabled ? "On" : "Off", MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.labeledValue("Click", enabled ? "Turn off" : "Turn on",
                    MenuUiSupport.LABEL_SECONDARY_COLOR));
            return MenuUiSupport.button(
                    enabled ? ItemsCompat.limeStainedGlassPane() : ItemsCompat.redStainedGlassPane(),
                    setting.label,
                    enabled ? ChatFormatting.GREEN : ChatFormatting.RED,
                    lore.toArray(new Component[0]));
        }

        private static String percent(double factor) {
            return Math.round(factor * 100) + "%";
        }

        private static String days(long value) {
            return value + (value == 1 ? " day" : " days");
        }

        private static String activeDaysLabel(long value) {
            return value <= 0 ? "All players" : days(value);
        }

        private static String hours(long value) {
            return value <= 0 ? "Never expires" : value + (value == 1 ? " hour" : " hours");
        }

        private static String limit(long value) {
            return value <= 0 ? "Unlimited" : String.valueOf(value);
        }

        private void editMoney(Setting setting, long current, long min, Item icon,
                               java.util.function.LongConsumer apply) {
            NumberInputUi.openMoney(viewer, setting.label, new ItemStack(icon), setting.label, current, min,
                    EconomyManager.MAX,
                    (p, next) -> {
                        apply.accept(next);
                        save(p);
                        EconomySounds.click(p);
                        open(p, eco, page);
                    },
                    p -> open(p, eco, page));
        }

        private void editPercent(Setting setting, double current, Item icon,
                                 java.util.function.DoubleConsumer apply) {
            NumberInputUi.openPercent(viewer, setting.label, new ItemStack(icon), setting.label,
                    Math.round(current * 100),
                    (p, next) -> {
                        apply.accept(next / 100.0);
                        save(p);
                        EconomySounds.click(p);
                        open(p, eco, page);
                    },
                    p -> open(p, eco, page));
        }

        private void editRetentionDays(Setting setting, long current, Item icon,
                                       java.util.function.LongConsumer apply) {
            NumberInputUi.open(viewer, setting.label, new ItemStack(icon), setting.label, current,
                    EconomyConfig.MIN_TRANSACTION_LOG_RETENTION_DAYS, Integer.MAX_VALUE,
                    new int[]{30, 7, 1}, SettingsMenu::days,
                    "Confirm", null,
                    (p, next) -> {
                        apply.accept(next);
                        save(p);
                        EconomySounds.click(p);
                        open(p, eco, page);
                    },
                    p -> open(p, eco, page));
        }

        private void editMultiplier(Setting setting, double current, Item icon,
                                    java.util.function.DoubleConsumer apply) {
            long initial = Math.round(current * 100);
            NumberInputUi.open(viewer, setting.label, new ItemStack(icon), setting.label, initial,
                    0, Math.round(EconomyConfig.MAX_DYNAMIC_PRICE_MULTIPLIER * 100),
                    new int[]{1000, 100, 25, 1}, v -> EconomyCraft.formatMultiplier(v / 100.0),
                    "Confirm", null,
                    (p, next) -> {
                        apply.accept(next / 100.0);
                        EconomyConfig.normalizeDynamicPriceBounds();
                        save(p);
                        eco.refreshDynamicPrices();
                        EconomySounds.click(p);
                        open(p, eco, page);
                    },
                    p -> open(p, eco, page));
        }

        private void editActiveDays(Setting setting, long current, Item icon,
                                    java.util.function.LongConsumer apply) {
            NumberInputUi.open(viewer, setting.label, new ItemStack(icon), setting.label, current,
                    0, Integer.MAX_VALUE, new int[]{30, 7, 1}, SettingsMenu::activeDaysLabel,
                    "Confirm", null,
                    (p, next) -> {
                        apply.accept(next);
                        save(p);
                        eco.refreshDynamicPrices();
                        EconomySounds.click(p);
                        open(p, eco, page);
                    },
                    p -> open(p, eco, page));
        }

        private void editHours(Setting setting, long current, Item icon,
                               java.util.function.LongConsumer apply) {
            NumberInputUi.open(viewer, setting.label, new ItemStack(icon), setting.label, current,
                    0, Integer.MAX_VALUE, new int[]{168, 24, 1}, SettingsMenu::hours,
                    "Confirm", null,
                    (p, next) -> {
                        apply.accept(next);
                        save(p);
                        EconomySounds.click(p);
                        open(p, eco, page);
                    },
                    p -> open(p, eco, page));
        }

        private void editLimit(Setting setting, long current, Item icon,
                               java.util.function.LongConsumer apply) {
            NumberInputUi.open(viewer, setting.label, new ItemStack(icon), setting.label, current,
                    0, Integer.MAX_VALUE, new int[]{100, 10, 5, 1}, SettingsMenu::limit,
                    "Confirm", null,
                    (p, next) -> {
                        apply.accept(next);
                        save(p);
                        EconomySounds.click(p);
                        open(p, eco, page);
                    },
                    p -> open(p, eco, page));
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= SIZE) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot == BACK) {
                EconomySounds.click(viewer);
                AdminUi.open(viewer, eco);
                return true;
            }
            if (slot == PREV && page > 0) {
                EconomySounds.page(viewer);
                page--;
                render();
                return true;
            }
            if (slot == NEXT && (page + 1) * GRID_SLOTS.length < settings.length) {
                EconomySounds.page(viewer);
                page++;
                render();
                return true;
            }

            int gridIndex = indexOfGridSlot(slot);
            if (gridIndex < 0) return true;
            int index = page * GRID_SLOTS.length + gridIndex;
            if (index >= settings.length) return true;

            Setting setting = settings[index];
            EconomySounds.click(viewer);
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
                            EconomySounds.click(p);
                            open(p, eco, page);
                        });
                case SCOREBOARD -> {
                    eco.toggleScoreboard();
                    applyRuntimeSettings(viewer.level().getServer());
                    render();
                }
                case SHOP -> {
                    config.shopEnabled = !config.shopEnabled;
                    save(viewer);
                    render();
                }
                case AUCTION -> {
                    config.auctionEnabled = !config.auctionEnabled;
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
                case WORTH -> {
                    config.worthEnabled = !config.worthEnabled;
                    save(viewer);
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
                case TRANSACTION_LOG -> {
                    config.transactionLogEnabled = !config.transactionLogEnabled;
                    save(viewer);
                    render();
                }
                case TRANSACTION_LOG_RETENTION -> editRetentionDays(setting, config.transactionLogRetentionDays,
                        Items.MAP, v -> EconomyConfig.get().transactionLogRetentionDays = (int) v);
                case DYNAMIC_PRICES -> {
                    config.dynamicPricesEnabled = !config.dynamicPricesEnabled;
                    save(viewer);
                    eco.refreshDynamicPrices();
                    render();
                }
                case DYNAMIC_PRICE_MIN_MULT -> editMultiplier(setting, config.dynamicPriceMinMultiplier, Items.PAPER,
                        v -> EconomyConfig.get().dynamicPriceMinMultiplier = v);
                case DYNAMIC_PRICE_MAX_MULT -> editMultiplier(setting, config.dynamicPriceMaxMultiplier, Items.PAPER,
                        v -> EconomyConfig.get().dynamicPriceMaxMultiplier = v);
                case DYNAMIC_PRICE_ACTIVE_DAYS -> editActiveDays(setting, config.dynamicPriceMinActiveDays, Items.CLOCK,
                        v -> EconomyConfig.get().dynamicPriceMinActiveDays = (int) v);
                case ORDER_EXPIRATION_HOURS -> editHours(setting, config.orderExpirationHours, Items.CLOCK,
                        v -> EconomyConfig.get().orderExpirationHours = (int) v);
                case AUCTION_EXPIRATION_HOURS -> editHours(setting, config.auctionExpirationHours, Items.CLOCK,
                        v -> EconomyConfig.get().auctionExpirationHours = (int) v);
                case MAX_ACTIVE_ORDERS_PER_PLAYER -> editLimit(setting, config.maxActiveOrdersPerPlayer, Items.WRITABLE_BOOK,
                        v -> EconomyConfig.get().maxActiveOrdersPerPlayer = (int) v);
                case MAX_ACTIVE_AUCTIONS_PER_PLAYER -> editLimit(setting, config.maxActiveAuctionsPerPlayer, Items.CHEST,
                        v -> EconomyConfig.get().maxActiveAuctionsPerPlayer = (int) v);
            }
            return true;
        }

        private static int indexOfGridSlot(int slot) {
            for (int i = 0; i < GRID_SLOTS.length; i++) {
                if (GRID_SLOTS[i] == slot) return i;
            }
            return -1;
        }
    }
}
