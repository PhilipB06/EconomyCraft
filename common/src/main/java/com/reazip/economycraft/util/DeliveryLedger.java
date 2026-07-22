package com.reazip.economycraft.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player pending item stacks, persisted by a single {@code DeliveryManager} shared between
 * {@code ShopManager} and {@code OrderManager}.
 */
public final class DeliveryLedger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<UUID, List<ItemStack>> deliveries = new HashMap<>();

    public void add(UUID player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        deliveries.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
    }

    /** Returns deliveries for the player without removing them. */
    public List<ItemStack> get(UUID player) {
        return deliveries.computeIfAbsent(player, k -> new ArrayList<>());
    }

    /** Returns whether {@code stack} was actually found and removed from the player's list. */
    public boolean remove(UUID player, ItemStack stack) {
        List<ItemStack> list = deliveries.get(player);
        if (list == null) return false;
        boolean removed = list.remove(stack);
        if (list.isEmpty()) deliveries.remove(player);
        return removed;
    }

    public boolean has(UUID player) {
        List<ItemStack> list = deliveries.get(player);
        return list != null && !list.isEmpty();
    }

    public JsonObject save(HolderLookup.Provider provider) {
        JsonObject dObj = new JsonObject();
        for (Map.Entry<UUID, List<ItemStack>> e : deliveries.entrySet()) {
            JsonArray arr = new JsonArray();
            for (ItemStack s : e.getValue()) {
                JsonObject o = new JsonObject();
                o.add("stack", ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, provider), s).result().orElse(new JsonObject()));
                arr.add(o);
            }
            dObj.add(e.getKey().toString(), arr);
        }
        return dObj;
    }

    public void load(JsonObject dObj, HolderLookup.Provider provider) {
        deliveries.clear();
        mergeFrom(dObj, provider);
    }

    /** Merges another ledger's saved JSON into this one without clearing existing entries first. */
    public void mergeFrom(JsonObject dObj, HolderLookup.Provider provider) {
        if (dObj == null) return;
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
                        ItemStack stack = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, provider), o.get("stack")).result().orElse(ItemStack.EMPTY);
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
}
