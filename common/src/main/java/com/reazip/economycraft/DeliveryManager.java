package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.util.DeliveryLedger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Owns the single delivery ledger shared by {@code ShopManager} and {@code OrderManager}, backed
 * by its own {@code deliveries.json} instead of being duplicated inside each manager's file.
 */
public final class DeliveryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Path file;
    private final DeliveryLedger ledger = new DeliveryLedger();

    public DeliveryManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getFile("config/economycraft");
        Path dataDir = dir.resolve("data");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Could not create data directory: {}", dataDir, e);
        }
        this.file = dataDir.resolve("deliveries.json");
        load(dataDir);
    }

    public void addDelivery(UUID player, ItemStack stack) {
        ledger.add(player, stack);
        save();
    }

    /** Returns deliveries for the player without removing them. */
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

        // First boot after upgrading from per-manager deliveries: pull any pending deliveries out
        // of shop.json/orders.json before those files get resaved without their old "deliveries"
        // section, so nothing a player is still owed gets silently dropped.
        boolean migrated = false;
        if (mergeLegacyDeliveries(dataDir.resolve("shop.json"))) migrated = true;
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
            if (legacy.size() == 0) return false;
            ledger.mergeFrom(legacy, server.registryAccess());
            return true;
        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Failed to read legacy deliveries from {}", legacyFile, ex);
            return false;
        }
    }

    public void save() {
        try {
            Files.writeString(file, GSON.toJson(ledger.save(server.registryAccess())), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.error("[EconomyCraft] Failed to save {}", file, ex);
        }
    }
}
