package com.reazip.economycraft.admin;

import com.reazip.economycraft.EconomyCommands;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.EconomyPermissions;
import com.reazip.economycraft.util.EconomyPermissions.Nodes;
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
    private static final int HEADER = 4;
    private static final int BACK = 36;
    private static final int SETTINGS_ROW_CENTER = 22;
    private static final int[] CATEGORY_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};

    public static void open(ServerPlayer player, EconomyManager eco) {
        if (!MenuUiSupport.checkOrDeny(player, EconomyPermissions.checkAdmin(player, Nodes.ADMIN_SETTINGS))) return;
        MenuUiSupport.openMenu(player, "Settings", (id, inv) -> new CategoriesMenu(id, inv, player, eco));
    }

    private static void open(ServerPlayer player, EconomyManager eco, Category category) {
        MenuUiSupport.openMenu(player, category.label, (id, inv) -> new SettingsMenu(id, inv, player, eco, category));
    }

    public static void applyRuntimeSettings(MinecraftServer server) {
        if (server == null) return;
        EconomyCommands.resyncCommands(server);
    }

    private static void save(ServerPlayer player) {
        EconomyConfig.save();
        applyRuntimeSettings(player.level().getServer());
    }

    private enum Category {
        ECONOMY("Economy", Items.GOLD_INGOT, "Starting money, daily reward and tax."),
        DISPLAY("Display", Items.OAK_SIGN, "Number format and the balance sidebar."),
        COMMANDS("Commands", Items.COMMAND_BLOCK, "Short command aliases."),
        SHOP("Shop & Selling", Items.EMERALD, "Server shop, selling and item values."),
        DYNAMIC_PRICES("Dynamic Prices", Items.REDSTONE, "Shop buy prices that follow the economy."),
        AUCTION("Auction House", Items.CHEST, "Player listings and their limits."),
        ORDERS("Orders", Items.WRITABLE_BOOK, "Player requests and their limits."),
        TRANSACTION_LOG("Transaction Logs", Items.MAP, "Balance change history on disk.");

        final String label;
        final Item icon;
        final String description;

        Category(String label, Item icon, String description) {
            this.label = label;
            this.icon = icon;
            this.description = description;
        }

        List<Setting> settings() {
            List<Setting> out = new ArrayList<>();
            for (Setting setting : Setting.values()) {
                if (setting.category == this) out.add(setting);
            }
            return out;
        }
    }

    private enum Setting {
        STARTING_BALANCE(Category.ECONOMY, "Starting Balance", "Money new players start with."),
        MAX_BALANCE(Category.ECONOMY, "Max Balance", "Most money a player can have."),
        DAILY_REWARD(Category.ECONOMY, "Daily Reward", "Money from /daily, once a day."),
        TAX_RATE(Category.ECONOMY, "Tax Rate", "Tax on auctions and orders."),
        SEPARATOR(Category.DISPLAY, "Number Separator", "Thousands separator shown in prices.",
                "Type \"space\" for a blank one."),
        SCOREBOARD(Category.DISPLAY, "Balance Sidebar", "The balance leaderboard on the right."),
        STANDALONE(Category.COMMANDS, "Short Commands", "Allow /pay, /shop and /ah without /eco."),
        STANDALONE_ADMIN(Category.COMMANDS, "Short Admin Commands", "Allow /addmoney without /eco."),
        SHOP(Category.SHOP, "Shop", "The server shop."),
        SELL(Category.SHOP, "Selling", "The /sell menu and right-click selling."),
        DAILY_SELL_LIMIT(Category.SHOP, "Daily Sell Limit", "Most a player can earn selling per day."),
        WORTH(Category.SHOP, "Item Values", "The /worth command and Item Value menu."),
        DYNAMIC_PRICES(Category.DYNAMIC_PRICES, "Dynamic Prices", "Shop buy prices follow the median balance."),
        DYNAMIC_PRICE_MIN_MULT(Category.DYNAMIC_PRICES, "Min Multiplier", "Lowest buy price scale."),
        DYNAMIC_PRICE_MAX_MULT(Category.DYNAMIC_PRICES, "Max Multiplier", "Highest buy price scale."),
        DYNAMIC_PRICE_ACTIVE_DAYS(Category.DYNAMIC_PRICES, "Active Player Window", "Days since last login to count as active.",
                "0 = count every player."),
        DYNAMIC_PRICE_UPDATE_MINUTES(Category.DYNAMIC_PRICES, "Price Update Interval", "Minutes between multiplier updates."),
        AUCTION(Category.AUCTION, "Auction House", "The player marketplace."),
        AUCTION_EXPIRATION_HOURS(Category.AUCTION, "Auction Expiration", "Hours until a listing expires.",
                "0 = never."),
        MAX_ACTIVE_AUCTIONS_PER_PLAYER(Category.AUCTION, "Max Auction Listings", "Most active listings per player.",
                "0 = no limit."),
        ORDERS(Category.ORDERS, "Orders", "The player request board."),
        ORDER_EXPIRATION_HOURS(Category.ORDERS, "Order Expiration", "Hours until an order expires.",
                "0 = never."),
        MAX_ACTIVE_ORDERS_PER_PLAYER(Category.ORDERS, "Max Order Requests", "Most open orders per player.",
                "0 = no limit."),
        TRANSACTION_LOG(Category.TRANSACTION_LOG, "Transaction Logs", "Save every balance change to a file."),
        TRANSACTION_LOG_RETENTION(Category.TRANSACTION_LOG, "Log Retention", "Days to keep transaction logs.");

        final Category category;
        final String label;
        final String description;
        final String extra;

        Setting(Category category, String label, String description) {
            this(category, label, description, null);
        }

        Setting(Category category, String label, String description, String extra) {
            this.category = category;
            this.label = label;
            this.description = description;
            this.extra = extra;
        }
    }

    private static int[] settingSlots(int count) {
        int[] slots = new int[count];
        boolean spaced = count <= 4;
        int first = spaced ? SETTINGS_ROW_CENTER - (count - 1) : SETTINGS_ROW_CENTER - count / 2;
        for (int i = 0; i < count; i++) {
            slots[i] = first + (spaced ? 2 * i : i);
        }
        return slots;
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) return i;
        }
        return -1;
    }

    private static class CategoriesMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final SimpleContainer container = new SimpleContainer(SIZE);
        private final Category[] categories = Category.values();

        CategoriesMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco) {
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

            container.setItem(HEADER, MenuUiSupport.button(Items.BOOK, "Server Settings", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Pick a category to change its settings."),
                    MenuUiSupport.hint("Changes save straight to config.json.")));

            for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
                Category category = categories[i];
                container.setItem(CATEGORY_SLOTS[i], MenuUiSupport.button(category.icon, category.label, ChatFormatting.AQUA,
                        MenuUiSupport.hint(category.description),
                        MenuUiSupport.labeledValue("Settings", String.valueOf(category.settings().size()),
                                MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Click", "Open", MenuUiSupport.LABEL_SECONDARY_COLOR)));
            }

            container.setItem(BACK, MenuUiSupport.backButton());
            MenuUiSupport.fillBackground(container);
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

            int index = indexOf(CATEGORY_SLOTS, slot);
            if (index < 0 || index >= categories.length) return true;
            EconomySounds.click(viewer);
            open(viewer, eco, categories[index]);
            return true;
        }
    }

    private static class SettingsMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final SimpleContainer container = new SimpleContainer(SIZE);
        private final Category category;
        private final List<Setting> settings;
        private final int[] slots;

        SettingsMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, Category category) {
            super(MenuType.GENERIC_9x5, id);
            this.viewer = viewer;
            this.eco = eco;
            this.category = category;
            this.settings = category.settings();
            this.slots = settingSlots(settings.size());

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

            container.setItem(HEADER, MenuUiSupport.button(category.icon, category.label, ChatFormatting.YELLOW,
                    MenuUiSupport.hint(category.description),
                    MenuUiSupport.hint("Changes save straight to config.json.")));

            for (int i = 0; i < settings.size(); i++) {
                container.setItem(slots[i], buildItem(settings.get(i), config));
            }

            container.setItem(BACK, MenuUiSupport.backButton());
            MenuUiSupport.fillBackground(container);
        }

        private ItemStack buildItem(Setting setting, EconomyConfig config) {
            return switch (setting) {
                case STARTING_BALANCE -> valueItem(setting, Items.GOLD_INGOT, EconomyCraft.formatMoney(config.economy.startingBalance));
                case MAX_BALANCE -> valueItem(setting, Items.GOLD_BLOCK, EconomyCraft.formatMoney(config.economy.maxBalance));
                case DAILY_REWARD -> valueItem(setting, Items.CLOCK, EconomyCraft.formatMoney(config.economy.dailyReward));
                case DAILY_SELL_LIMIT -> valueItem(setting, Items.HOPPER, config.selling.dailyLimit <= 0
                        ? "No limit" : EconomyCraft.formatMoney(config.selling.dailyLimit));
                case TAX_RATE -> valueItem(setting, Items.PAPER, percent(config.economy.taxRate));
                case SEPARATOR -> valueItem(setting, Items.NAME_TAG, "\"" + config.display.balanceSeparator + "\" gives "
                        + EconomyCraft.formatMoney(1234567));
                case SCOREBOARD -> toggleItem(setting, config.display.scoreboardEnabled);
                case SHOP -> toggleItem(setting, config.shop.enabled);
                case AUCTION -> toggleItem(setting, config.auction.enabled);
                case ORDERS -> toggleItem(setting, config.orders.enabled);
                case SELL -> toggleItem(setting, config.selling.enabled);
                case WORTH -> toggleItem(setting, config.selling.worthEnabled);
                case STANDALONE -> toggleItem(setting, config.commands.standalone);
                case STANDALONE_ADMIN -> toggleItem(setting, config.commands.standaloneAdmin);
                case TRANSACTION_LOG -> toggleItem(setting, config.transactionLog.enabled);
                case TRANSACTION_LOG_RETENTION -> valueItem(setting, Items.MAP, days(config.transactionLog.retentionDays));
                case DYNAMIC_PRICES -> dynamicPricesItem(config);
                case DYNAMIC_PRICE_MIN_MULT -> valueItem(setting, Items.PAPER, EconomyCraft.formatMultiplier(config.dynamicPrices.minMultiplier));
                case DYNAMIC_PRICE_MAX_MULT -> valueItem(setting, Items.PAPER, EconomyCraft.formatMultiplier(config.dynamicPrices.maxMultiplier));
                case DYNAMIC_PRICE_ACTIVE_DAYS -> valueItem(setting, Items.CLOCK, activeDaysLabel(config.dynamicPrices.minActiveDays));
                case DYNAMIC_PRICE_UPDATE_MINUTES -> valueItem(setting, Items.REPEATER, minutes(config.dynamicPrices.updateMinutes));
                case ORDER_EXPIRATION_HOURS -> valueItem(setting, Items.CLOCK, hours(config.orders.expirationHours));
                case AUCTION_EXPIRATION_HOURS -> valueItem(setting, Items.CLOCK, hours(config.auction.expirationHours));
                case MAX_ACTIVE_ORDERS_PER_PLAYER -> valueItem(setting, Items.WRITABLE_BOOK, limit(config.orders.maxActivePerPlayer));
                case MAX_ACTIVE_AUCTIONS_PER_PLAYER -> valueItem(setting, Items.CHEST, limit(config.auction.maxActivePerPlayer));
            };
        }

        private ItemStack dynamicPricesItem(EconomyConfig config) {
            List<Component> lore = new ArrayList<>();
            lore.add(MenuUiSupport.hint(Setting.DYNAMIC_PRICES.description));
            lore.add(MenuUiSupport.labeledValue("Now", config.dynamicPrices.enabled ? "On" : "Off",
                    MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.labeledValue("Current multiplier",
                    EconomyCraft.formatMultiplier(eco.getDynamicPriceMultiplier()), MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.labeledValue("Click", config.dynamicPrices.enabled ? "Turn off" : "Turn on",
                    MenuUiSupport.LABEL_SECONDARY_COLOR));
            return MenuUiSupport.button(
                    config.dynamicPrices.enabled ? ItemsCompat.limeStainedGlassPane() : ItemsCompat.redStainedGlassPane(),
                    Setting.DYNAMIC_PRICES.label, config.dynamicPrices.enabled ? ChatFormatting.GREEN : ChatFormatting.RED,
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
            return NumberInputUi.formatHundredths(Math.round(factor * 10000)) + "%";
        }

        private static String days(long value) {
            return value + (value == 1 ? " day" : " days");
        }

        private static String activeDaysLabel(long value) {
            return value <= 0 ? "All players" : days(value);
        }

        private static String minutes(long value) {
            return value + (value == 1 ? " minute" : " minutes");
        }

        private static String hours(long value) {
            return value <= 0 ? "Never expires" : value + (value == 1 ? " hour" : " hours");
        }

        private static String limit(long value) {
            return value <= 0 ? "Unlimited" : String.valueOf(value);
        }

        private void editMoney(Setting setting, long current, long min, Item icon,
                               java.util.function.LongConsumer apply) {
            editMoney(setting, current, min, EconomyManager.maxBalance(), icon, apply);
        }

        private void editMoney(Setting setting, long current, long min, long max, Item icon,
                               java.util.function.LongConsumer apply) {
            NumberInputUi.openMoney(viewer, setting.label, new ItemStack(icon), setting.label, current, min,
                    max,
                    (p, next) -> {
                        apply.accept(next);
                        save(p);
                        EconomySounds.click(p);
                        open(p, eco, category);
                    },
                    p -> open(p, eco, category));
        }

        private void editPercent(Setting setting, double current, Item icon,
                                 java.util.function.DoubleConsumer apply) {
            NumberInputUi.openDecimal(viewer, setting.label, new ItemStack(icon), setting.label,
                    Math.round(current * 10000), 0, 10000, "%",
                    (p, hundredths) -> {
                        apply.accept(hundredths / 10000.0);
                        save(p);
                        EconomySounds.click(p);
                        open(p, eco, category);
                    },
                    p -> open(p, eco, category));
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
                        open(p, eco, category);
                    },
                    p -> open(p, eco, category));
        }

        private void editMultiplier(Setting setting, double current, Item icon,
                                    java.util.function.DoubleConsumer apply) {
            NumberInputUi.openDecimal(viewer, setting.label, new ItemStack(icon), setting.label,
                    Math.round(current * 100), Math.round(EconomyConfig.MIN_DYNAMIC_PRICE_MULTIPLIER * 100),
                    Math.round(EconomyConfig.MAX_DYNAMIC_PRICE_MULTIPLIER * 100), "x",
                    (p, hundredths) -> {
                        apply.accept(hundredths / 100.0);
                        EconomyConfig.normalizeDynamicPriceBounds();
                        save(p);
                        eco.refreshDynamicPrices();
                        EconomySounds.click(p);
                        open(p, eco, category);
                    },
                    p -> open(p, eco, category));
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
                        open(p, eco, category);
                    },
                    p -> open(p, eco, category));
        }

        private void editUpdateMinutes(Setting setting, long current, Item icon,
                                       java.util.function.LongConsumer apply) {
            NumberInputUi.open(viewer, setting.label, new ItemStack(icon), setting.label, current,
                    EconomyConfig.MIN_DYNAMIC_PRICE_UPDATE_MINUTES, Integer.MAX_VALUE,
                    new int[]{60, 10, 1}, SettingsMenu::minutes,
                    "Confirm", null,
                    (p, next) -> {
                        apply.accept(next);
                        save(p);
                        EconomySounds.click(p);
                        open(p, eco, category);
                    },
                    p -> open(p, eco, category));
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
                        open(p, eco, category);
                    },
                    p -> open(p, eco, category));
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
                        open(p, eco, category);
                    },
                    p -> open(p, eco, category));
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= SIZE) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot == BACK) {
                EconomySounds.click(viewer);
                AdminSettingsUi.open(viewer, eco);
                return true;
            }

            int index = indexOf(slots, slot);
            if (index < 0) return true;

            Setting setting = settings.get(index);
            EconomySounds.click(viewer);
            EconomyConfig config = EconomyConfig.get();
            switch (setting) {
                case STARTING_BALANCE -> editMoney(setting, config.economy.startingBalance, 0, Items.GOLD_INGOT,
                        v -> EconomyConfig.get().economy.startingBalance = v);
                case MAX_BALANCE -> editMoney(setting, config.economy.maxBalance, 1, EconomyManager.ABSOLUTE_MAX_BALANCE,
                        Items.GOLD_BLOCK, v -> EconomyConfig.get().economy.maxBalance = v);
                case DAILY_REWARD -> editMoney(setting, config.economy.dailyReward, 0, Items.CLOCK,
                        v -> EconomyConfig.get().economy.dailyReward = v);
                case DAILY_SELL_LIMIT -> editMoney(setting, config.selling.dailyLimit, 0, Items.HOPPER,
                        v -> EconomyConfig.get().selling.dailyLimit = v);
                case TAX_RATE -> editPercent(setting, config.economy.taxRate, Items.PAPER,
                        v -> EconomyConfig.get().economy.taxRate = v);
                case SEPARATOR -> TextInputUi.open(viewer, "Number separator", config.display.balanceSeparator,
                        Items.NAME_TAG, "Use: ", "Type one character",
                        (p, text) -> {
                            EconomyConfig.get().display.balanceSeparator =
                                    text.equalsIgnoreCase("space") ? " " : text.substring(0, 1);
                            save(p);
                            EconomySounds.click(p);
                            open(p, eco, category);
                        });
                case SCOREBOARD -> {
                    eco.toggleScoreboard();
                    applyRuntimeSettings(viewer.level().getServer());
                    render();
                }
                case SHOP -> {
                    config.shop.enabled = !config.shop.enabled;
                    save(viewer);
                    render();
                }
                case AUCTION -> {
                    config.auction.enabled = !config.auction.enabled;
                    save(viewer);
                    render();
                }
                case ORDERS -> {
                    config.orders.enabled = !config.orders.enabled;
                    save(viewer);
                    render();
                }
                case SELL -> {
                    config.selling.enabled = !config.selling.enabled;
                    save(viewer);
                    render();
                }
                case WORTH -> {
                    config.selling.worthEnabled = !config.selling.worthEnabled;
                    save(viewer);
                    render();
                }
                case STANDALONE -> {
                    config.commands.standalone = !config.commands.standalone;
                    save(viewer);
                    render();
                }
                case STANDALONE_ADMIN -> {
                    config.commands.standaloneAdmin = !config.commands.standaloneAdmin;
                    save(viewer);
                    render();
                }
                case TRANSACTION_LOG -> {
                    config.transactionLog.enabled = !config.transactionLog.enabled;
                    save(viewer);
                    render();
                }
                case TRANSACTION_LOG_RETENTION -> editRetentionDays(setting, config.transactionLog.retentionDays,
                        Items.MAP, v -> EconomyConfig.get().transactionLog.retentionDays = (int) v);
                case DYNAMIC_PRICES -> {
                    config.dynamicPrices.enabled = !config.dynamicPrices.enabled;
                    save(viewer);
                    eco.refreshDynamicPrices();
                    render();
                }
                case DYNAMIC_PRICE_MIN_MULT -> editMultiplier(setting, config.dynamicPrices.minMultiplier, Items.PAPER,
                        v -> EconomyConfig.get().dynamicPrices.minMultiplier = v);
                case DYNAMIC_PRICE_MAX_MULT -> editMultiplier(setting, config.dynamicPrices.maxMultiplier, Items.PAPER,
                        v -> EconomyConfig.get().dynamicPrices.maxMultiplier = v);
                case DYNAMIC_PRICE_ACTIVE_DAYS -> editActiveDays(setting, config.dynamicPrices.minActiveDays, Items.CLOCK,
                        v -> EconomyConfig.get().dynamicPrices.minActiveDays = (int) v);
                case DYNAMIC_PRICE_UPDATE_MINUTES -> editUpdateMinutes(setting, config.dynamicPrices.updateMinutes, Items.REPEATER,
                        v -> EconomyConfig.get().dynamicPrices.updateMinutes = (int) v);
                case ORDER_EXPIRATION_HOURS -> editHours(setting, config.orders.expirationHours, Items.CLOCK,
                        v -> EconomyConfig.get().orders.expirationHours = (int) v);
                case AUCTION_EXPIRATION_HOURS -> editHours(setting, config.auction.expirationHours, Items.CLOCK,
                        v -> EconomyConfig.get().auction.expirationHours = (int) v);
                case MAX_ACTIVE_ORDERS_PER_PLAYER -> editLimit(setting, config.orders.maxActivePerPlayer, Items.WRITABLE_BOOK,
                        v -> EconomyConfig.get().orders.maxActivePerPlayer = (int) v);
                case MAX_ACTIVE_AUCTIONS_PER_PLAYER -> editLimit(setting, config.auction.maxActivePerPlayer, Items.CHEST,
                        v -> EconomyConfig.get().auction.maxActivePerPlayer = (int) v);
            }
            return true;
        }
    }
}
