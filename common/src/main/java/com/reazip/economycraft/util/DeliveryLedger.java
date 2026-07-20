package com.reazip.economycraft.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player pending item stacks plus change-listener bookkeeping.
 * Shared by {@code OrderManager} and {@code ShopManager}.
 */
public final class DeliveryLedger {
    private final Map<UUID, List<ItemStack>> deliveries = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public void add(UUID player, ItemStack stack) {
        deliveries.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
    }

    /** Returns deliveries for the player without removing them. */
    public List<ItemStack> get(UUID player) {
        return deliveries.computeIfAbsent(player, k -> new ArrayList<>());
    }

    /** Returns whether a delivery list existed for the player (and was mutated). */
    public boolean remove(UUID player, ItemStack stack) {
        List<ItemStack> list = deliveries.get(player);
        if (list == null) return false;
        list.remove(stack);
        if (list.isEmpty()) deliveries.remove(player);
        return true;
    }

    public boolean has(UUID player) {
        List<ItemStack> list = deliveries.get(player);
        return list != null && !list.isEmpty();
    }

    public void addListener(Runnable run) {
        listeners.add(run);
    }

    public void removeListener(Runnable run) {
        listeners.remove(run);
    }

    public void notifyListeners() {
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }

    public JsonObject save(HolderLookup.Provider provider) {
        JsonObject dObj = new JsonObject();
        for (Map.Entry<UUID, List<ItemStack>> e : deliveries.entrySet()) {
            JsonArray arr = new JsonArray();
            for (ItemStack s : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                o.addProperty("count", s.getCount());
                JsonElement stackEl = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, provider), s).result().orElse(new JsonObject());
                o.add("stack", stackEl);
                arr.add(o);
            }
            dObj.add(e.getKey().toString(), arr);
        }
        return dObj;
    }

    public void load(JsonObject dObj, HolderLookup.Provider provider) {
        deliveries.clear();
        for (String key : dObj.keySet()) {
            UUID id = UUID.fromString(key);
            List<ItemStack> list = new ArrayList<>();
            for (var sEl : dObj.getAsJsonArray(key)) {
                JsonObject o = sEl.getAsJsonObject();
                ItemStack stack = ItemStack.EMPTY;
                if (o.has("stack")) {
                    stack = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, provider), o.get("stack")).result().orElse(ItemStack.EMPTY);
                } else {
                    String itemId = o.get("item").getAsString();
                    int count = o.get("count").getAsInt();
                    IdentifierCompat.Id rl = IdentifierCompat.tryParse(itemId);
                    if (rl != null) {
                        Optional<Item> opt = IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, rl);
                        if (opt.isPresent()) {
                            stack = new ItemStack(opt.get(), count);
                        }
                    }
                }
                if (!stack.isEmpty()) list.add(stack);
            }
            deliveries.put(id, list);
        }
    }
}
