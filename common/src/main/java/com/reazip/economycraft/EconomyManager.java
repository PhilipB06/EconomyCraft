package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Long>>(){}.getType();

    private final MinecraftServer server;
    private final Path file;
    private final Map<UUID, Long> balances = new HashMap<>();

    public EconomyManager(MinecraftServer server) {
        this.server = server;
        this.file = server.getFile("economycraft_balances.json");
        load();
    }

    public MinecraftServer getServer() {
        return server;
    }

    public long getBalance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    public void addMoney(UUID player, long amount) {
        balances.put(player, getBalance(player) + amount);
    }

    public void setMoney(UUID player, long amount) {
        balances.put(player, amount);
    }

    public boolean pay(UUID from, UUID to, long amount) {
        long balance = getBalance(from);
        if (balance < amount) return false;
        setMoney(from, balance - amount);
        addMoney(to, amount);
        return true;
    }

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                Map<UUID, Double> map = GSON.fromJson(json, new TypeToken<Map<UUID, Double>>(){}.getType());
                if (map != null) {
                    for (Map.Entry<UUID, Double> e : map.entrySet()) {
                        balances.put(e.getKey(), e.getValue().longValue());
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void save() {
        try {
            Map<UUID, Long> data = new HashMap<>(balances);
            String json = GSON.toJson(data, TYPE);
            Files.writeString(file, json);
        } catch (IOException ignored) {
        }
    }

    public Map<UUID, Long> getBalances() {
        return balances;
    }
}
