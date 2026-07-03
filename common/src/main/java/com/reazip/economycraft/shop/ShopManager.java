package com.reazip.economycraft.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.util.DeliveryLedger;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
    private final DeliveryLedger deliveries = new DeliveryLedger();
    private int nextId = 1;

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
        deliveries.notifyListeners();
        save();
    }

    public ShopListing removeListing(int id) {
        ShopListing l = listings.remove(id);
        if (l != null) {
            deliveries.notifyListeners();
            save();
        }
        return l;
    }

    public void notifySellerSale(ShopListing listing, ServerPlayer buyer) {
        if (listing == null || buyer == null) return;

        UUID sellerId = listing.seller;
        if (sellerId == null) return;

        ServerPlayer seller = server.getPlayerList().getPlayer(sellerId);
        if (seller == null) return;

        ItemStack stack = listing.item;
        int amount = (stack == null || stack.isEmpty()) ? 0 : stack.getCount();
        String itemName = (stack == null || stack.isEmpty())
                ? "item"
                : stack.getHoverName().getString();

        String buyerName = IdentityCompat.of(buyer).name();
        long price = listing.price;

        Component msg = Component.literal(
                "Sold " + amount + "x " + itemName +
                        " to " + buyerName +
                        " for " + EconomyCraft.formatMoney(price)
        ).withStyle(ChatFormatting.GREEN);

        seller.sendSystemMessage(msg);
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
                for (var el : root.getAsJsonArray("listings")) {
                    ShopListing l = ShopListing.load(el.getAsJsonObject(), server.registryAccess());
                    listings.put(l.id, l);
                }
                deliveries.load(root.getAsJsonObject("deliveries"), server.registryAccess());
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
