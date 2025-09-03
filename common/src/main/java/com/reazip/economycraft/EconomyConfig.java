package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and stores configuration for the economy system. */
public class EconomyConfig {
    private static final Gson GSON = new Gson();

    public long startingBalance = 1000;
    public long dailyAmount = 100;
    public double taxRate = 0.10; // 10%
    @SerializedName("pvp_balance_loss_percentage")
    public double pvpBalanceLossPercentage = 0.0; // 0 = disabled
    @SerializedName("standalone_commands")
    public boolean standaloneCommands = true;
    @SerializedName("standalone_admin_commands")
    public boolean standaloneAdminCommands = false;
    @SerializedName("scoreboard_enabled")
    public boolean scoreboardEnabled = true;

    private static EconomyConfig INSTANCE = new EconomyConfig();
    private static Path file;

    public static EconomyConfig get() {
        return INSTANCE;
    }

    /** Loads the config from disk, creating it with defaults if necessary. */
    public static void load(MinecraftServer server) {
        Path dir = server != null ? server.getFile("config/economycraft") : Path.of("config/economycraft");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        file = dir.resolve("config.json");
        if (Files.exists(file)) {
            try {
                INSTANCE = GSON.fromJson(Files.readString(file), EconomyConfig.class);
            } catch (IOException ignored) {}
        }
        save();
    }

    public static void save() {
        if (file == null) return;
        try {
            Files.writeString(file, GSON.toJson(INSTANCE));
        } catch (IOException ignored) {}
    }
}

