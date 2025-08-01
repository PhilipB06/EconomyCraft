package com.reazip.economycraft.shop;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Manages shop listings and deliveries. */
public class ShopManager {
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, ShopListing> listings = new HashMap<>();
    private final Map<UUID, List<ItemStack>> deliveries = new HashMap<>();
    private int nextId = 1;

    public ShopManager(MinecraftServer server) {
        this.server = server;
        this.file = server.getFile("economycraft_shop.json");
        load();
    }

    public Collection<ShopListing> getListings() {
        return listings.values();
    }

    public void addListing(ShopListing listing) {
        listing.id = nextId++;
        listings.put(listing.id, listing);
    }

    public ShopListing removeListing(int id) {
        return listings.remove(id);
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
                for (CompoundTag lTag : tag.getList("listings", 10)) {
                    ShopListing l = ShopListing.load(lTag);
                    listings.put(l.id, l);
                }
                // deliveries
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
        net.minecraft.nbt.ListTag lList = new net.minecraft.nbt.ListTag();
        for (ShopListing l : listings.values()) {
            lList.add(l.save());
        }
        tag.put("listings", lList);
        CompoundTag dTag = new CompoundTag();
        for (Map.Entry<UUID, List<ItemStack>> e : deliveries.entrySet()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (ItemStack s : e.getValue()) {
                CompoundTag c = new CompoundTag();
                s.save(c);
                list.add(c);
            }
            dTag.put(e.getKey().toString(), list);
        }
        tag.put("deliveries", dTag);
        try {
            Files.writeString(file, tag.toString());
        } catch (IOException ignored) {}
    }
}
