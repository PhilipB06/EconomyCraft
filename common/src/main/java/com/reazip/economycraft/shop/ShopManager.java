package com.reazip.economycraft.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Manages shop listings and deliveries. */
public class ShopManager {
    private static final Gson GSON = new Gson();
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, ShopListing> listings = new HashMap<>();
    private final Map<UUID, List<ItemStack>> deliveries = new HashMap<>();
    private int nextId = 1;
    private final List<Runnable> listeners = new ArrayList<>();

    public ShopManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getFile("config/economycraft");
        Path dataDir = dir.resolve("data");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}
        this.file = dataDir.resolve("shop.json");
        load();
    }

    public Collection<ShopListing> getListings() {
        return listings.values();
    }

    public ShopListing getListing(int id) {
        return listings.get(id);
    }

    public void addListing(ShopListing listing) {
        listing.id = nextId++;
        listings.put(listing.id, listing);
        notifyListeners();
        save();
    }

    public ShopListing removeListing(int id) {
        ShopListing l = listings.remove(id);
        if (l != null) {
            notifyListeners();
            save();
        }
        return l;
    }

    public MinecraftServer server() {
        return server;
    }

    public void addDelivery(UUID player, ItemStack stack) {
        deliveries.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
        save();
    }

    /** Returns deliveries for the player without removing them. */
    public List<ItemStack> getDeliveries(UUID player) {
        return deliveries.computeIfAbsent(player, k -> new ArrayList<>());
    }

    public void removeDelivery(UUID player, ItemStack stack) {
        List<ItemStack> list = deliveries.get(player);
        if (list != null) {
            list.remove(stack);
            if (list.isEmpty()) deliveries.remove(player);
            save();
        }
    }

    public List<ItemStack> claimDeliveries(UUID player) {
        List<ItemStack> list = deliveries.remove(player);
        if (list != null) save();
        return list;
    }

    public boolean hasDeliveries(UUID player) {
        List<ItemStack> list = deliveries.get(player);
        return list != null && !list.isEmpty();
    }

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                nextId = root.get("nextId").getAsInt();
                for (var el : root.getAsJsonArray("listings")) {
                    ShopListing l = ShopListing.load(el.getAsJsonObject(), server.registryAccess());
                    listings.put(l.id, l);
                }
                JsonObject dObj = root.getAsJsonObject("deliveries");
                for (String key : dObj.keySet()) {
                    UUID id = UUID.fromString(key);
                    List<ItemStack> list = new ArrayList<>();
                    for (var sEl : dObj.getAsJsonArray(key)) {
                        JsonObject o = sEl.getAsJsonObject();
                        ItemStack stack = ItemStack.EMPTY;
                        if (o.has("stack")) {
                            stack = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), o.get("stack")).result().orElse(ItemStack.EMPTY);
                        } else {
                            String itemId = o.get("item").getAsString();
                            int count = o.get("count").getAsInt();
                            ResourceLocation rl = ResourceLocation.tryParse(itemId);
                            if (rl != null) {
                                java.util.Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(rl);

                                if (opt.isPresent()) {
                                    Item item = opt.get();
                                    stack = new ItemStack(item, count);
                                }
                            }
                        }
                        if (!stack.isEmpty()) list.add(stack);
                    }
                    deliveries.put(id, list);
                }
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("nextId", nextId);
        JsonArray listArr = new JsonArray();
        for (ShopListing l : listings.values()) {
            listArr.add(l.save(server.registryAccess()));
        }
        root.add("listings", listArr);
        JsonObject dObj = new JsonObject();
        for (Map.Entry<UUID, List<ItemStack>> e : deliveries.entrySet()) {
            JsonArray arr = new JsonArray();
            for (ItemStack s : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                o.addProperty("count", s.getCount());
                JsonElement stackEl = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), s).result().orElse(new JsonObject());
                o.add("stack", stackEl);
                arr.add(o);
            }
            dObj.add(e.getKey().toString(), arr);
        }
        root.add("deliveries", dObj);
        try {
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ignored) {}
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
