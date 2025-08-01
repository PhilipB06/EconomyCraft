package com.reazip.economycraft.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Manages market requests and deliveries. */
public class MarketManager {
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

    public void load() {
        if (Files.exists(file)) {
            try {
                CompoundTag tag = com.mojang.serialization.TagParser.parseTag(Files.readString(file));
                nextId = tag.getInt("nextId");
                for (CompoundTag c : tag.getList("requests", 10)) {
                    MarketRequest r = MarketRequest.load(c);
                    requests.put(r.id, r);
                }
                CompoundTag dTag = tag.getCompound("deliveries");
                for (String key : dTag.getAllKeys()) {
                    UUID id = UUID.fromString(key);
                    List<ItemStack> list = new ArrayList<>();
                    for (CompoundTag s : dTag.getList(key, 10)) {
                        list.add(ItemStack.of(s));
                    }
                    deliveries.put(id, list);
                }
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("nextId", nextId);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (MarketRequest r : requests.values()) {
            list.add(r.save());
        }
        tag.put("requests", list);
        CompoundTag dTag = new CompoundTag();
        for (Map.Entry<UUID, List<ItemStack>> e : deliveries.entrySet()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (ItemStack s : e.getValue()) {
                CompoundTag c = new CompoundTag();
                s.save(c);
                l.add(c);
            }
            dTag.put(e.getKey().toString(), l);
        }
        tag.put("deliveries", dTag);
        try {
            Files.writeString(file, tag.toString());
        } catch (IOException ignored) {}
    }
}
