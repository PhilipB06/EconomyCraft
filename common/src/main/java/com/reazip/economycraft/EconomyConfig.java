package com.reazip.economycraft;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
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

    public long startingBalance;
    public long dailyAmount;
    public double taxRate;
    @SerializedName("pvp_balance_loss_percentage")
    public double pvpBalanceLossPercentage;
    @SerializedName("standalone_commands")
    public boolean standaloneCommands;
    @SerializedName("standalone_admin_commands")
    public boolean standaloneAdminCommands;
    @SerializedName("scoreboard_enabled")
    public boolean scoreboardEnabled;

    private static EconomyConfig INSTANCE = new EconomyConfig();
    private static Path file;

    public static EconomyConfig get() {
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        Path dir = server != null ? server.getFile("config/economycraft") : Path.of("config/economycraft");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        file = dir.resolve("config.json");

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
            INSTANCE = parsed;
        } catch (Exception e) {
            throw new IllegalStateException("[EconomyCraft] Failed to read/parse config.json at " + file, e);
        }
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
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                LOGGER.warn("[EconomyCraft] config.json root is not an object, skipping merge.");
                return;
            }
            userRoot = parsed.getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException("[EconomyCraft] Failed to read/parse user config.json for merge at " + file, ex);
        }

        int[] added = new int[]{0};
        addMissingRecursive(userRoot, defaults, added);

        if (added[0] > 0) {
            try {
                Files.writeString(file, GSON.toJson(userRoot), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("[EconomyCraft] Failed to write merged config.json at " + file, ex);
            }
        }
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
