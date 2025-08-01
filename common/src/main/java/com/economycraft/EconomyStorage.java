package com.economycraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public class EconomyStorage {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Double>>(){}.getType();
    private final File saveFile;
    private Map<UUID, Double> balances = new HashMap<>();

    public EconomyStorage(MinecraftServer server) {
        this.saveFile = server.getSavePath(WorldSavePath.ROOT).resolve("economy.json").toFile();
        load();
    }

    public double getBalance(UUID player) {
        return balances.getOrDefault(player, 0.0);
    }

    public void setBalance(UUID player, double amount) {
        balances.put(player, amount);
    }

    public void addBalance(UUID player, double amount) {
        balances.put(player, getBalance(player) + amount);
    }

    public void save() {
        try (FileWriter writer = new FileWriter(saveFile)) {
            GSON.toJson(balances, writer);
        } catch (Exception e) {
            EconomyCraft.LOGGER.error("Failed to save economy data", e);
        }
    }

    public Map<UUID, Double> getEntriesSorted() {
        return balances.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                    (a, b) -> a, LinkedHashMap::new));
    }

    private void load() {
        if (!saveFile.exists()) return;
        try (FileReader reader = new FileReader(saveFile)) {
            balances = GSON.fromJson(reader, TYPE);
            if (balances == null) {
                balances = new HashMap<>();
            }
        } catch (Exception e) {
            EconomyCraft.LOGGER.error("Failed to load economy data", e);
        }
    }
}
