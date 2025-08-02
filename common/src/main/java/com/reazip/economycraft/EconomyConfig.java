package com.reazip.economycraft;

import com.google.gson.Gson;
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

    private static EconomyConfig INSTANCE = new EconomyConfig();

    public static EconomyConfig get() {
        return INSTANCE;
    }

    /** Loads the config from disk, creating it with defaults if necessary. */
    public static void load(MinecraftServer server) {
        Path file = server.getFile("economycraft_config.json");
        if (Files.exists(file)) {
            try {
                INSTANCE = GSON.fromJson(Files.readString(file), EconomyConfig.class);
            } catch (IOException ignored) {}
        }
        try {
            Files.writeString(file, GSON.toJson(INSTANCE));
        } catch (IOException ignored) {}
    }
}

