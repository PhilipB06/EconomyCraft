package com.reazip.economycraft.market;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Manages market requests and deliveries. */
public class MarketManager {
    private static final Gson GSON = new Gson();
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, MarketRequest> requests = new HashMap<>();
    private final Map<UUID, List<ItemStack>> deliveries = new HashMap<>();
    private int nextId = 1;

    public MarketManager(MinecraftServer server) {
        this.server = server;
        this.file = server.getFile("economycraft_market.json");
        load();
    }

    public Collection<MarketRequest> getRequests() {
        return requests.values();
    }

    public void addRequest(MarketRequest r) {
        r.id = nextId++;
        requests.put(r.id, r);
    }

    public MarketRequest removeRequest(int id) {
        return requests.remove(id);
    }

    public void addDelivery(UUID player, ItemStack stack) {
        deliveries.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
    }

    public List<ItemStack> claimDeliveries(UUID player) {
        return deliveries.remove(player);
    }

    public boolean hasDeliveries(UUID player) {
        return deliveries.containsKey(player);
    }

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                nextId = root.get("nextId").getAsInt();
                for (var el : root.getAsJsonArray("requests")) {
                    MarketRequest r = MarketRequest.load(el.getAsJsonObject());
                    requests.put(r.id, r);
                }
                JsonObject dObj = root.getAsJsonObject("deliveries");
                for (String key : dObj.keySet()) {
                    UUID id = UUID.fromString(key);
                    List<ItemStack> list = new ArrayList<>();
                    for (var sEl : dObj.getAsJsonArray(key)) {
                        JsonObject o = sEl.getAsJsonObject();
                        String itemId = o.get("item").getAsString();
                        int count = o.get("count").getAsInt();
                        BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)).ifPresent(h -> list.add(new ItemStack(h.value(), count)));
                    }
                    deliveries.put(id, list);
                }
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("nextId", nextId);
        JsonArray reqArr = new JsonArray();
        for (MarketRequest r : requests.values()) {
            reqArr.add(r.save());
        }
        root.add("requests", reqArr);
        JsonObject dObj = new JsonObject();
        for (Map.Entry<UUID, List<ItemStack>> e : deliveries.entrySet()) {
            JsonArray arr = new JsonArray();
            for (ItemStack s : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                o.addProperty("count", s.getCount());
                arr.add(o);
            }
            dObj.add(e.getKey().toString(), arr);
        }
        root.add("deliveries", dObj);
        try {
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ignored) {}
    }
}
