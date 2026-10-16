package com.reazip.economycraft;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.util.EconomyPaths;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public class EconomyConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_RESOURCE_PATH = "/assets/economycraft/config.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public Economy economy = new Economy();
    public Display display = new Display();
    public Commands commands = new Commands();
    public Shop shop = new Shop();
    public Selling selling = new Selling();
    @SerializedName("dynamic_prices")
    public DynamicPrices dynamicPrices = new DynamicPrices();
    public Auction auction = new Auction();
    public Orders orders = new Orders();
    @SerializedName("transaction_log")
    public TransactionLog transactionLog = new TransactionLog();

    public static class Economy {
        @SerializedName("starting_balance")
        public long startingBalance = 1000;
        @SerializedName("max_balance")
        public long maxBalance = EconomyManager.ABSOLUTE_MAX_BALANCE;
        @SerializedName("daily_reward")
        public long dailyReward = 100;
        @SerializedName("tax_rate")
        public double taxRate = 0.1;
    }

    public static class Display {
        @SerializedName("balance_separator")
        public String balanceSeparator = ".";
        @SerializedName("currency_symbol")
        public String currencySymbol = DEFAULT_CURRENCY_SYMBOL;
        @SerializedName("currency_symbol_position")
        public CurrencyPosition currencySymbolPosition = CurrencyPosition.PREFIX;
        @SerializedName("currency_name")
        public String currencyName = "";
        @SerializedName("scoreboard_enabled")
        public boolean scoreboardEnabled = true;
    }

    public enum CurrencyPosition {
        @SerializedName("prefix") PREFIX,
        @SerializedName("suffix") SUFFIX
    }

    public static class Commands {
        public boolean standalone = true;
        @SerializedName("standalone_admin")
        public boolean standaloneAdmin = false;
    }

    public static class Shop {
        public boolean enabled = true;
    }

    public static class Selling {
        public boolean enabled = true;
        @SerializedName("daily_limit")
        public long dailyLimit = 10000;
        @SerializedName("worth_enabled")
        public boolean worthEnabled = true;
    }

    public static class DynamicPrices {
        public boolean enabled = false;
        @SerializedName("min_multiplier")
        public double minMultiplier = 0.5;
        @SerializedName("max_multiplier")
        public double maxMultiplier = 5.0;
        @SerializedName("min_active_days")
        public int minActiveDays = 30;
        @SerializedName("update_minutes")
        public int updateMinutes = 60;
    }

    public static class Auction {
        public boolean enabled = true;
        @SerializedName("expiration_hours")
        public int expirationHours = 168;
        @SerializedName("max_active_per_player")
        public int maxActivePerPlayer = 0;
    }

    public static class Orders {
        public boolean enabled = true;
        @SerializedName("expiration_hours")
        public int expirationHours = 168;
        @SerializedName("max_active_per_player")
        public int maxActivePerPlayer = 0;
    }

    public static class TransactionLog {
        public boolean enabled = true;
        @SerializedName("retention_days")
        public int retentionDays = 7;
    }

    public static final int MIN_TRANSACTION_LOG_RETENTION_DAYS = 1;
    public static final int WARN_TRANSACTION_LOG_RETENTION_DAYS = 90;
    public static final double MIN_DYNAMIC_PRICE_MULTIPLIER = 0.5;
    public static final double MAX_DYNAMIC_PRICE_MULTIPLIER = 100.0;
    public static final int MIN_DYNAMIC_PRICE_UPDATE_MINUTES = 1;
    public static final String DEFAULT_CURRENCY_SYMBOL = "$";
    public static final int MAX_CURRENCY_SYMBOL_LENGTH = 16;
    public static final int MAX_CURRENCY_NAME_LENGTH = 32;

    private static final String[][] LEGACY_FLAT_KEYS = {
            {"startingBalance", "economy", "starting_balance"},
            {"max_balance", "economy", "max_balance"},
            {"dailyAmount", "economy", "daily_reward"},
            {"taxRate", "economy", "tax_rate"},
            {"balance_separator", "display", "balance_separator"},
            {"scoreboard_enabled", "display", "scoreboard_enabled"},
            {"standalone_commands", "commands", "standalone"},
            {"standalone_admin_commands", "commands", "standalone_admin"},
            {"shop_enabled", "shop", "enabled"},
            {"sell_enabled", "selling", "enabled"},
            {"dailySellLimit", "selling", "daily_limit"},
            {"worth_enabled", "selling", "worth_enabled"},
            {"dynamic_prices_enabled", "dynamic_prices", "enabled"},
            {"dynamic_price_min_multiplier", "dynamic_prices", "min_multiplier"},
            {"dynamic_price_max_multiplier", "dynamic_prices", "max_multiplier"},
            {"dynamic_price_min_active_days", "dynamic_prices", "min_active_days"},
            {"auction_enabled", "auction", "enabled"},
            {"auction_expiration_hours", "auction", "expiration_hours"},
            {"max_active_auctions_per_player", "auction", "max_active_per_player"},
            {"orders_enabled", "orders", "enabled"},
            {"order_expiration_hours", "orders", "expiration_hours"},
            {"max_active_orders_per_player", "orders", "max_active_per_player"},
            {"transaction_log_enabled", "transaction_log", "enabled"},
            {"transaction_log_retention_days", "transaction_log", "retention_days"},
    };

    private static EconomyConfig INSTANCE = new EconomyConfig();
    private static Path file;

    public static EconomyConfig get() {
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        file = EconomyPaths.configDir(server).resolve("config.json");

        if (Files.notExists(file)) {
            copyDefaultFromJarOrThrow();
        } else {
            mergeNewDefaultsFromBundledDefault();
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            EconomyConfig parsed = GSON.fromJson(json, EconomyConfig.class);
            if (parsed == null) {
                throw new IllegalStateException("config.json parsed to null");
            }
            fillMissingSections(parsed);
            parsed.economy.taxRate = clampPercentage("economy.tax_rate", parsed.economy.taxRate);
            if (parsed.economy.dailyReward < 0) {
                LOGGER.warn("[EconomyCraft] economy.daily_reward ({}) is negative; clamping to 0.", parsed.economy.dailyReward);
                parsed.economy.dailyReward = 0;
            }
            if (parsed.display.balanceSeparator == null || parsed.display.balanceSeparator.isEmpty()) {
                LOGGER.warn("[EconomyCraft] display.balance_separator is empty; defaulting to \".\".");
                parsed.display.balanceSeparator = ".";
            } else if (textProblem(parsed.display.balanceSeparator.substring(0, 1)) != null) {
                LOGGER.warn("[EconomyCraft] display.balance_separator ({}) {}; defaulting to \".\".",
                        parsed.display.balanceSeparator, textProblem(parsed.display.balanceSeparator.substring(0, 1)));
                parsed.display.balanceSeparator = ".";
            }
            parsed.display.currencySymbol = validCurrencySymbol(parsed.display.currencySymbol);
            parsed.display.currencyName = validCurrencyName(parsed.display.currencyName);
            if (parsed.display.currencySymbolPosition == null) {
                LOGGER.warn("[EconomyCraft] display.currency_symbol_position must be \"prefix\" or \"suffix\"; defaulting to \"prefix\".");
                parsed.display.currencySymbolPosition = CurrencyPosition.PREFIX;
            }
            parsed.economy.maxBalance = clampMaxBalance(parsed.economy.maxBalance);
            parsed.transactionLog.retentionDays = clampRetentionDays(parsed.transactionLog.retentionDays);
            parsed.orders.expirationHours = clampNonNegative("orders.expiration_hours", parsed.orders.expirationHours);
            parsed.auction.expirationHours = clampNonNegative("auction.expiration_hours", parsed.auction.expirationHours);
            parsed.orders.maxActivePerPlayer = clampNonNegative("orders.max_active_per_player", parsed.orders.maxActivePerPlayer);
            parsed.auction.maxActivePerPlayer = clampNonNegative("auction.max_active_per_player", parsed.auction.maxActivePerPlayer);
            parsed.dynamicPrices.minActiveDays = clampNonNegative("dynamic_prices.min_active_days", parsed.dynamicPrices.minActiveDays);
            parsed.dynamicPrices.updateMinutes = clampDynamicPriceUpdateMinutes(parsed.dynamicPrices.updateMinutes);
            INSTANCE = parsed;
            normalizeDynamicPriceBounds();
        } catch (Exception e) {
            throw new IllegalStateException("[EconomyCraft] Failed to read/parse config.json at " + file, e);
        }
    }

    private static void fillMissingSections(EconomyConfig config) {
        if (config.economy == null) config.economy = new Economy();
        if (config.display == null) config.display = new Display();
        if (config.commands == null) config.commands = new Commands();
        if (config.shop == null) config.shop = new Shop();
        if (config.selling == null) config.selling = new Selling();
        if (config.dynamicPrices == null) config.dynamicPrices = new DynamicPrices();
        if (config.auction == null) config.auction = new Auction();
        if (config.orders == null) config.orders = new Orders();
        if (config.transactionLog == null) config.transactionLog = new TransactionLog();
    }

    private static String validCurrencySymbol(@Nullable String symbol) {
        String trimmed = symbol == null ? DEFAULT_CURRENCY_SYMBOL : symbol.trim();
        String problem = currencySymbolProblem(trimmed);
        if (problem == null) return trimmed;
        LOGGER.warn("[EconomyCraft] display.currency_symbol ({}) {}; defaulting to \"{}\".",
                trimmed, problem, DEFAULT_CURRENCY_SYMBOL);
        return DEFAULT_CURRENCY_SYMBOL;
    }

    private static String validCurrencyName(@Nullable String name) {
        String trimmed = name == null ? "" : name.trim();
        String problem = currencyNameProblem(trimmed);
        if (problem == null) return trimmed;
        LOGGER.warn("[EconomyCraft] display.currency_name ({}) {}; leaving it unset.", trimmed, problem);
        return "";
    }

    public static @Nullable String currencySymbolProblem(String symbol) {
        if (symbol.length() > MAX_CURRENCY_SYMBOL_LENGTH) {
            return "is longer than " + MAX_CURRENCY_SYMBOL_LENGTH + " characters";
        }
        String problem = textProblem(symbol);
        if (problem != null) return problem;
        if (symbol.chars().anyMatch(Character::isDigit)) return "contains a digit";
        if (EconomyCraft.isShortMoneySuffix(symbol)) return "clashes with the " + symbol + " amount shorthand";
        return null;
    }

    public static @Nullable String currencyNameProblem(String name) {
        if (name.length() > MAX_CURRENCY_NAME_LENGTH) {
            return "is longer than " + MAX_CURRENCY_NAME_LENGTH + " characters";
        }
        return textProblem(name);
    }

    public static @Nullable String textProblem(String text) {
        if (text.indexOf('§') >= 0) return "contains a § formatting code";
        if (text.chars().anyMatch(Character::isISOControl)) return "contains a control character";
        return null;
    }

    private static double clampRange(String fieldName, double value, double min, double max, String reason) {
        double clamped = Double.isNaN(value) ? min : Math.clamp(value, min, max);
        if (clamped != value) {
            LOGGER.warn("[EconomyCraft] {} ({}) is outside the valid {}-{} range{}; clamping to {}.",
                    fieldName, value, min, max, reason, clamped);
        }
        return clamped;
    }

    private static double clampPercentage(String fieldName, double value) {
        return clampRange(fieldName, value, 0.0, 1.0, " (decimal factor, e.g. 0.1 = 10%)");
    }

    private static int clampRetentionDays(int days) {
        if (days < MIN_TRANSACTION_LOG_RETENTION_DAYS) {
            LOGGER.warn("[EconomyCraft] transaction_log.retention_days ({}) is below the minimum of {} day(s); clamping to {}.",
                    days, MIN_TRANSACTION_LOG_RETENTION_DAYS, MIN_TRANSACTION_LOG_RETENTION_DAYS);
            return MIN_TRANSACTION_LOG_RETENTION_DAYS;
        }
        if (days > WARN_TRANSACTION_LOG_RETENTION_DAYS) {
            LOGGER.warn("[EconomyCraft] transaction_log.retention_days ({}) is above {} days; transaction logs can take up significant disk space over that long a retention period.",
                    days, WARN_TRANSACTION_LOG_RETENTION_DAYS);
        }
        return days;
    }

    private static long clampMaxBalance(long value) {
        if (value < 1) {
            LOGGER.warn("[EconomyCraft] economy.max_balance ({}) is below 1; clamping to 1.", value);
            return 1;
        }
        if (value > EconomyManager.ABSOLUTE_MAX_BALANCE) {
            LOGGER.warn("[EconomyCraft] economy.max_balance ({}) is above the absolute maximum of {}; clamping to {}.",
                    value, EconomyManager.ABSOLUTE_MAX_BALANCE, EconomyManager.ABSOLUTE_MAX_BALANCE);
            return EconomyManager.ABSOLUTE_MAX_BALANCE;
        }
        return value;
    }

    private static int clampNonNegative(String fieldName, int value) {
        if (value < 0) {
            LOGGER.warn("[EconomyCraft] {} ({}) is negative; clamping to 0 (unlimited).", fieldName, value);
            return 0;
        }
        return value;
    }

    private static int clampDynamicPriceUpdateMinutes(int minutes) {
        if (minutes < MIN_DYNAMIC_PRICE_UPDATE_MINUTES) {
            LOGGER.warn("[EconomyCraft] dynamic_prices.update_minutes ({}) is below the minimum of {} minute(s); clamping to {}.",
                    minutes, MIN_DYNAMIC_PRICE_UPDATE_MINUTES, MIN_DYNAMIC_PRICE_UPDATE_MINUTES);
            return MIN_DYNAMIC_PRICE_UPDATE_MINUTES;
        }
        return minutes;
    }

    public static void normalizeDynamicPriceBounds() {
        DynamicPrices config = INSTANCE.dynamicPrices;
        config.minMultiplier = clampMultiplierRange("dynamic_prices.min_multiplier", config.minMultiplier);
        config.maxMultiplier = clampMultiplierRange("dynamic_prices.max_multiplier", config.maxMultiplier);
        if (config.maxMultiplier < config.minMultiplier) {
            LOGGER.warn("[EconomyCraft] dynamic_prices.max_multiplier ({}) is below dynamic_prices.min_multiplier ({}); raising it to match.",
                    config.maxMultiplier, config.minMultiplier);
            config.maxMultiplier = config.minMultiplier;
        }
    }

    private static double clampMultiplierRange(String fieldName, double value) {
        return clampRange(fieldName, value, MIN_DYNAMIC_PRICE_MULTIPLIER, MAX_DYNAMIC_PRICE_MULTIPLIER, "");
    }

    public static void save() {
        if (file == null) {
            throw new IllegalStateException("[EconomyCraft] EconomyConfig not initialized. Call load() first.");
        }
        try {
            Files.writeString(
                    file,
                    GSON.toJson(INSTANCE),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException("[EconomyCraft] Failed to save config.json at " + file, e);
        }
    }

    private static void copyDefaultFromJarOrThrow() {
        try (InputStream in = EconomyConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "[EconomyCraft] Missing bundled default " + DEFAULT_RESOURCE_PATH +
                                " (did you forget to include it in resources?)"
                );
            }
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[EconomyCraft] Created {} from bundled default {}", file, DEFAULT_RESOURCE_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("[EconomyCraft] Failed to create config.json at " + file, e);
        }
    }

    private static void mergeNewDefaultsFromBundledDefault() {
        JsonObject defaults = readBundledDefaultJson();
        if (defaults == null) {
            LOGGER.warn("[EconomyCraft] No bundled defaults found; skipping config merge.");
            return;
        }

        JsonObject userRoot;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                userRoot = new JsonObject();
            } else {
                JsonElement parsed = JsonParser.parseString(json);
                if (parsed.isJsonNull()) {
                    userRoot = new JsonObject();
                } else if (parsed.isJsonObject()) {
                    userRoot = parsed.getAsJsonObject();
                } else {
                    LOGGER.warn("[EconomyCraft] config.json root is not an object, skipping merge.");
                    return;
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("[EconomyCraft] Failed to read/parse user config.json for merge at " + file, ex);
        }

        boolean migratedShopSettings = migrateShopSettings(userRoot, defaults);
        boolean migratedLayout = migrateFlatLayout(userRoot);
        int[] added = new int[]{0};
        addMissingRecursive(userRoot, defaults, added);
        if (migratedLayout) userRoot = orderLike(userRoot, defaults);

        if (migratedShopSettings || migratedLayout || added[0] > 0) {
            try {
                Files.writeString(file, GSON.toJson(userRoot), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("[EconomyCraft] Failed to write merged config.json at " + file, ex);
            }
        }
    }

    private static boolean migrateShopSettings(JsonObject target, JsonObject defaults) {
        JsonElement legacyShop = target.remove("server_shop_enabled");
        if (legacyShop != null) {
            JsonElement legacyAuction = target.get("shop_enabled");
            if (!target.has("auction_enabled") && legacyAuction != null) {
                target.add("auction_enabled", legacyAuction.deepCopy());
            }
            target.add("shop_enabled", legacyShop.deepCopy());
            LOGGER.info("[EconomyCraft] Migrated shop settings to shop_enabled and auction_enabled.");
            return true;
        }

        if (!target.has("auction_enabled") && target.has("shop_enabled")) {
            JsonElement legacyAuction = target.get("shop_enabled");
            target.add("auction_enabled", legacyAuction.deepCopy());
            JsonElement defaultShop = defaults.get("shop_enabled");
            if (defaultShop != null) target.add("shop_enabled", defaultShop.deepCopy());
            LOGGER.info("[EconomyCraft] Migrated shop_enabled to auction_enabled.");
            return true;
        }
        return false;
    }

    private static boolean migrateFlatLayout(JsonObject target) {
        boolean migrated = false;
        for (String[] move : LEGACY_FLAT_KEYS) {
            JsonElement value = target.remove(move[0]);
            if (value == null) continue;
            migrated = true;
            JsonElement section = target.get(move[1]);
            if (section == null || !section.isJsonObject()) {
                section = new JsonObject();
                target.add(move[1], section);
            }
            JsonObject sectionObject = section.getAsJsonObject();
            if (!sectionObject.has(move[2])) sectionObject.add(move[2], value);
        }
        if (migrated) LOGGER.info("[EconomyCraft] Migrated config.json to the sectioned layout.");
        return migrated;
    }

    private static JsonObject orderLike(JsonObject source, JsonObject template) {
        JsonObject ordered = new JsonObject();
        for (Map.Entry<String, JsonElement> e : template.entrySet()) {
            JsonElement value = source.get(e.getKey());
            if (value == null) continue;
            if (value.isJsonObject() && e.getValue().isJsonObject()) {
                value = orderLike(value.getAsJsonObject(), e.getValue().getAsJsonObject());
            }
            ordered.add(e.getKey(), value);
        }
        for (Map.Entry<String, JsonElement> e : source.entrySet()) {
            if (!ordered.has(e.getKey())) ordered.add(e.getKey(), e.getValue());
        }
        return ordered;
    }

    private static JsonObject readBundledDefaultJson() {
        try (InputStream in = EconomyConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) return null;

            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return null;

            return parsed.getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException("[EconomyCraft] Failed to read bundled default config.json from " + DEFAULT_RESOURCE_PATH, ex);
        }
    }

    private static void addMissingRecursive(JsonObject target, JsonObject defaults, int[] added) {
        for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
            String key = e.getKey();
            JsonElement defVal = e.getValue();

            if (!target.has(key)) {
                target.add(key, defVal == null ? JsonNull.INSTANCE : defVal.deepCopy());
                added[0]++;
                continue;
            }

            JsonElement curVal = target.get(key);
            if (curVal != null && curVal.isJsonObject()
                    && defVal != null && defVal.isJsonObject()) {
                addMissingRecursive(curVal.getAsJsonObject(), defVal.getAsJsonObject(), added);
            }
        }
    }
}
