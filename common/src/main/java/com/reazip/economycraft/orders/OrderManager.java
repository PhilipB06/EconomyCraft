package com.reazip.economycraft.orders;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.DeliveryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Manages order requests. Deliveries are owned by the shared {@link DeliveryManager}. */
public class OrderManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, OrderRequest> requests = new HashMap<>();
    private final DeliveryManager deliveries;
    private final List<Runnable> listeners = new ArrayList<>();
    private int nextId = 1;

    public OrderManager(MinecraftServer server, DeliveryManager deliveries) {
        this.server = server;
        Path dir = server.getFile("config/economycraft");
        Path dataDir = dir.resolve("data");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}
        this.file = dataDir.resolve("orders.json");
        this.deliveries = deliveries;
        load();
    }

    /** Requests, newest first. */
    public List<OrderRequest> getRequests() {
        List<OrderRequest> out = new ArrayList<>(requests.values());
        out.sort((a, b) -> Integer.compare(b.id, a.id));
        return out;
    }

    public OrderRequest getRequest(int id) {
        return requests.get(id);
    }

    public void addRequest(OrderRequest r) {
        r.id = nextId++;
        requests.put(r.id, r);
        notifyListeners();
        save();
    }

    public OrderRequest removeRequest(int id) {
        OrderRequest r = requests.remove(id);
        if (r != null) {
            notifyListeners();
            save();
        }
        return r;
    }

    /**
     * Persists and broadcasts an in-place change to an existing request (e.g. a partial
     * fulfillment reducing its outstanding amount/price), refreshing any open orders view.
     */
    public void markChanged() {
        notifyListeners();
        save();
    }

    public void addDelivery(UUID player, ItemStack stack) {
        deliveries.addDelivery(player, stack);
    }

    /** Returns deliveries for the player without removing them. */
    public List<ItemStack> getDeliveries(UUID player) {
        return deliveries.getDeliveries(player);
    }

    public void removeDelivery(UUID player, ItemStack stack) {
        deliveries.removeDelivery(player, stack);
    }

    public boolean hasDeliveries(UUID player) {
        return deliveries.hasDeliveries(player);
    }

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                nextId = root.get("nextId").getAsInt();
                for (var el : root.getAsJsonArray("requests")) {
                    try {
                        OrderRequest r = OrderRequest.load(el.getAsJsonObject(), server.registryAccess());
                        if (r.item == null || r.item.isEmpty()) {
                            LOGGER.error("[EconomyCraft] Dropping order request {} with an unreadable item in {}", r.id, file);
                            continue;
                        }
                        requests.put(r.id, r);
                    } catch (Exception ex) {
                        LOGGER.error("[EconomyCraft] Dropping an unreadable order request in {}", file, ex);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("[EconomyCraft] Failed to load {}", file, ex);
            }
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("nextId", nextId);
        JsonArray reqArr = new JsonArray();
        for (OrderRequest r : requests.values()) {
            reqArr.add(r.save(server.registryAccess()));
        }
        root.add("requests", reqArr);
        try {
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ex) {
            LOGGER.error("[EconomyCraft] Failed to save {}", file, ex);
        }
    }

    public void addListener(Runnable run) {
        listeners.add(run);
    }

    public void removeListener(Runnable run) {
        listeners.remove(run);
    }

    private void notifyListeners() {
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }
}
