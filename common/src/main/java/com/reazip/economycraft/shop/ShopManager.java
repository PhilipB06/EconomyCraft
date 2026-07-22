package com.reazip.economycraft.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.DeliveryManager;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Manages shop listings. Deliveries are owned by the shared {@link DeliveryManager}. */
public class ShopManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, ShopListing> listings = new HashMap<>();
    private final DeliveryManager deliveries;
    private final List<Runnable> listeners = new ArrayList<>();
    private int nextId = 1;

    public ShopManager(MinecraftServer server, DeliveryManager deliveries) {
        this.server = server;
        Path dir = server.getFile("config/economycraft");
        Path dataDir = dir.resolve("data");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}
        this.file = dataDir.resolve("shop.json");
        this.deliveries = deliveries;
        load();
    }

    /** Listings, newest first. */
    public List<ShopListing> getListings() {
        List<ShopListing> out = new ArrayList<>(listings.values());
        out.sort((a, b) -> Integer.compare(b.id, a.id));
        return out;
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
                for (var el : root.getAsJsonArray("listings")) {
                    try {
                        ShopListing l = ShopListing.load(el.getAsJsonObject(), server.registryAccess());
                        if (l.item == null || l.item.isEmpty()) {
                            LOGGER.error("[EconomyCraft] Dropping shop listing {} with an unreadable item in {}", l.id, file);
                            continue;
                        }
                        listings.put(l.id, l);
                    } catch (Exception ex) {
                        LOGGER.error("[EconomyCraft] Dropping an unreadable shop listing in {}", file, ex);
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
        JsonArray listArr = new JsonArray();
        for (ShopListing l : listings.values()) {
            listArr.add(l.save(server.registryAccess()));
        }
        root.add("listings", listArr);
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
