package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.util.EconomyPaths;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeliveryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Path file;
    private final Map<UUID, List<ItemStack>> deliveries = new ConcurrentHashMap<>();

    public DeliveryManager(MinecraftServer server) {
        this.server = server;
        Path dataDir = EconomyPaths.dataDir(server);
        this.file = dataDir.resolve("deliveries.json");
        load(dataDir);
    }

    public void addDelivery(UUID player, ItemStack stack) {
        addDelivery(player, stack, true);
    }

    public void addDelivery(UUID player, ItemStack stack, boolean persist) {
        if (stack != null && !stack.isEmpty()) {
            deliveries.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
        }
        if (persist) save();
    }

    public List<ItemStack> getDeliveries(UUID player) {
        List<ItemStack> list = deliveries.get(player);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void removeDelivery(UUID player, ItemStack stack) {
        List<ItemStack> list = deliveries.get(player);
        if (list == null) return;
        boolean removed = list.remove(stack);
        if (list.isEmpty()) deliveries.remove(player);
        if (removed) save();
    }

    public boolean hasDeliveries(UUID player) {
        List<ItemStack> list = deliveries.get(player);
        return list != null && !list.isEmpty();
    }

    private void load(Path dataDir) {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root != null) mergeFrom(root);
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
            mergeFrom(legacy);
            return true;
        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Failed to read legacy deliveries from {}", legacyFile, ex);
            return false;
        }
    }

    private void mergeFrom(JsonObject dObj) {
        for (String key : dObj.keySet()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                LOGGER.error("[EconomyCraft] Dropping deliveries for invalid player id '{}'", key);
                continue;
            }

            List<ItemStack> list = deliveries.computeIfAbsent(id, k -> new ArrayList<>());
            try {
                for (var sEl : dObj.getAsJsonArray(key)) {
                    try {
                        JsonObject o = sEl.getAsJsonObject();
                        if (!o.has("stack")) {
                            LOGGER.error("[EconomyCraft] Dropping a delivery for {} with no 'stack' field", key);
                            continue;
                        }
                        ItemStack stack = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), o.get("stack")).result().orElse(ItemStack.EMPTY);
                        if (stack.isEmpty()) {
                            LOGGER.error("[EconomyCraft] Dropping a delivery for {} with an unreadable item", key);
                            continue;
                        }
                        list.add(stack);
                    } catch (Exception ex) {
                        LOGGER.error("[EconomyCraft] Dropping an unreadable delivery entry for {}", key, ex);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("[EconomyCraft] Failed to read deliveries for {}", key, ex);
            }
        }
    }

    public void save() {
        JsonObject dObj = new JsonObject();
        for (Map.Entry<UUID, List<ItemStack>> e : deliveries.entrySet()) {
            JsonArray arr = new JsonArray();
            for (ItemStack s : e.getValue()) {
                JsonObject o = new JsonObject();
                o.add("stack", ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), s).result().orElse(new JsonObject()));
                arr.add(o);
            }
            dObj.add(e.getKey().toString(), arr);
        }
        AsyncFileWriter.writeAsync(file, GSON.toJson(dObj));
    }
}
