package com.reazip.economycraft.orders;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reazip.economycraft.util.DeliveryLedger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Manages order requests and deliveries. */
public class OrderManager {
    private static final Gson GSON = new Gson();
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, OrderRequest> requests = new HashMap<>();
    private final DeliveryLedger deliveries = new DeliveryLedger();
    private int nextId = 1;

    public OrderManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getFile("config/economycraft");
        Path dataDir = dir.resolve("data");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}
        this.file = dataDir.resolve("orders.json");
        load();
    }

    public Collection<OrderRequest> getRequests() {
        return requests.values();
    }

    public OrderRequest getRequest(int id) {
        return requests.get(id);
    }

    public void addRequest(OrderRequest r) {
        r.id = nextId++;
        requests.put(r.id, r);
        deliveries.notifyListeners();
        save();
    }

    public OrderRequest removeRequest(int id) {
        OrderRequest r = requests.remove(id);
        if (r != null) {
            deliveries.notifyListeners();
            save();
        }
        return r;
    }

    public void addDelivery(UUID player, ItemStack stack) {
        deliveries.add(player, stack);
        save();
    }

    /** Returns deliveries for the player without removing them. */
    public List<ItemStack> getDeliveries(UUID player) {
        return deliveries.get(player);
    }

    public void removeDelivery(UUID player, ItemStack stack) {
        if (deliveries.remove(player, stack)) save();
    }

    public List<ItemStack> claimDeliveries(UUID player) {
        List<ItemStack> list = deliveries.claim(player);
        if (list != null) save();
        return list;
    }

    public boolean hasDeliveries(UUID player) {
        return deliveries.has(player);
    }

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                nextId = root.get("nextId").getAsInt();
                for (var el : root.getAsJsonArray("requests")) {
                    OrderRequest r = OrderRequest.load(el.getAsJsonObject(), server.registryAccess());
                    requests.put(r.id, r);
                }
                deliveries.load(root.getAsJsonObject("deliveries"), server.registryAccess());
            } catch (Exception ignored) {}
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
        root.add("deliveries", deliveries.save(server.registryAccess()));
        try {
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ignored) {}
    }

    public void addListener(Runnable run) {
        deliveries.addListener(run);
    }

    public void removeListener(Runnable run) {
        deliveries.removeListener(run);
    }
}
