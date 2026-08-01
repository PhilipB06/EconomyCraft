package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.util.DeliveryLedger;
import com.reazip.economycraft.util.EconomyPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class DeliveryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Path file;
    private final DeliveryLedger ledger = new DeliveryLedger();

    public DeliveryManager(MinecraftServer server) {
        this.server = server;
        Path dataDir = EconomyPaths.dataDir(server);
        this.file = dataDir.resolve("deliveries.json");
        load(dataDir);
    }

    public void addDelivery(UUID player, ItemStack stack) {
        ledger.add(player, stack);
        save();
    }

    public List<ItemStack> getDeliveries(UUID player) {
        return ledger.get(player);
    }

    public void removeDelivery(UUID player, ItemStack stack) {
        if (ledger.remove(player, stack)) save();
    }

    public boolean hasDeliveries(UUID player) {
        return ledger.has(player);
    }

    private void load(Path dataDir) {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root != null) ledger.load(root, server.registryAccess());
            } catch (Exception ex) {
                LOGGER.error("[EconomyCraft] Failed to load {}", file, ex);
            }
            return;
        }

        boolean migrated = mergeLegacyDeliveries(dataDir.resolve("shop.json"));
        if (mergeLegacyDeliveries(dataDir.resolve("orders.json"))) migrated = true;
        if (migrated) {
            LOGGER.info("[EconomyCraft] Migrated legacy deliveries from shop.json/orders.json into {}", file);
        }
        save();
    }

    private boolean mergeLegacyDeliveries(Path legacyFile) {
        if (!Files.exists(legacyFile)) return false;
        try {
            String json = Files.readString(legacyFile, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("deliveries")) return false;
            JsonObject legacy = root.getAsJsonObject("deliveries");
            if (legacy.isEmpty()) return false;
            ledger.mergeFrom(legacy, server.registryAccess());
            return true;
        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Failed to read legacy deliveries from {}", legacyFile, ex);
            return false;
        }
    }

    public void save() {
        AsyncFileWriter.writeAsync(file, GSON.toJson(ledger.save(server.registryAccess())));
    }
}
