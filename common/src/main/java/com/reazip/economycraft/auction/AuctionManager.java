package com.reazip.economycraft.auction;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.DeliveryManager;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.util.EconomyPaths;
import com.reazip.economycraft.util.ExpirationUtil;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.EconomySounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, AuctionListing> listings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> limitOverrides = new ConcurrentHashMap<>();
    private final DeliveryManager deliveries;
    private final List<Runnable> listeners = new ArrayList<>();
    private int nextId = 1;

    public AuctionManager(MinecraftServer server, DeliveryManager deliveries) {
        this.server = server;
        this.file = prepareAuctionFile(EconomyPaths.dataDir(server));
        this.deliveries = deliveries;
        load();
    }

    private static Path prepareAuctionFile(Path dataDir) {
        Path file = dataDir.resolve("auctions.json");
        Path legacy = dataDir.resolve("shop.json");
        if (Files.exists(file)) {
            if (Files.exists(legacy)) {
                LOGGER.warn("[EconomyCraft] Both {} and {} exist; using {} and ignoring {}.",
                        file, legacy, file, legacy);
            }
            return file;
        }
        if (Files.notExists(legacy)) return file;

        try {
            Files.move(legacy, file);
            LOGGER.info("[EconomyCraft] Migrated auction listings from {} to {}.", legacy, file);
            return file;
        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Failed to rename legacy auction file {}; using it for now.", legacy, ex);
            return legacy;
        }
    }

    public List<AuctionListing> getListings() {
        List<AuctionListing> out = new ArrayList<>(listings.values());
        out.sort((a, b) -> Integer.compare(b.id, a.id));
        return out;
    }

    public AuctionListing getListing(int id) {
        return listings.get(id);
    }

    public void addListing(AuctionListing listing) {
        listing.id = nextId++;
        listings.put(listing.id, listing);
        notifyListeners();
        save();
    }

    public AuctionListing removeListing(int id) {
        AuctionListing l = listings.remove(id);
        if (l != null) {
            notifyListeners();
            save();
        }
        return l;
    }

    public void notifySellerSale(AuctionListing listing, ServerPlayer buyer) {
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

        EconomySounds.moneyReceived(seller);
        seller.sendSystemMessage(msg);
    }

    public void addDelivery(UUID player, ItemStack stack) {
        deliveries.addDelivery(player, stack);
    }

    public int countActive(UUID player) {
        int count = 0;
        for (AuctionListing l : listings.values()) {
            if (player.equals(l.seller)) count++;
        }
        return count;
    }

    public Integer getLimitOverride(UUID player) {
        return limitOverrides.get(player);
    }

    public void setLimitOverride(UUID player, Integer limit) {
        if (limit == null) {
            limitOverrides.remove(player);
        } else {
            limitOverrides.put(player, limit);
        }
        save();
    }

    public int getEffectiveLimit(UUID player) {
        Integer override = limitOverrides.get(player);
        return override != null ? override : EconomyConfig.get().maxActiveAuctionsPerPlayer;
    }

    public boolean hasReachedLimit(UUID player) {
        int limit = getEffectiveLimit(player);
        return limit > 0 && countActive(player) >= limit;
    }

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root == null) return;

                if (root.has("nextId")) {
                    nextId = root.get("nextId").getAsInt();
                }
                JsonArray savedListings = root.has("listings")
                        ? root.getAsJsonArray("listings")
                        : new JsonArray();
                long now = System.currentTimeMillis();
                boolean migrated = false;
                for (var el : savedListings) {
                    try {
                        AuctionListing l = AuctionListing.load(el.getAsJsonObject(), server.registryAccess());
                        if (l.item == null || l.item.isEmpty()) {
                            LOGGER.error("[EconomyCraft] Dropping auction listing {} with an unreadable item in {}", l.id, file);
                            continue;
                        }
                        if (l.createdAt <= 0) {
                            l.createdAt = now;
                            l.expiresAt = ExpirationUtil.expiresAt(now, EconomyConfig.get().auctionExpirationHours);
                            migrated = true;
                        }
                        listings.put(l.id, l);
                    } catch (Exception ex) {
                        LOGGER.error("[EconomyCraft] Dropping an unreadable auction listing in {}", file, ex);
                    }
                }
                if (root.has("playerLimits")) {
                    for (var e : root.getAsJsonObject("playerLimits").entrySet()) {
                        try {
                            limitOverrides.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                        } catch (Exception ex) {
                            LOGGER.error("[EconomyCraft] Dropping an unreadable auction limit override in {}", file, ex);
                        }
                    }
                }
                if (migrated) save();
            } catch (Exception ex) {
                LOGGER.error("[EconomyCraft] Failed to load {}", file, ex);
            }
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("nextId", nextId);
        JsonArray listArr = new JsonArray();
        for (AuctionListing l : listings.values()) {
            listArr.add(l.save(server.registryAccess()));
        }
        root.add("listings", listArr);
        JsonObject limitsObj = new JsonObject();
        for (var e : limitOverrides.entrySet()) {
            limitsObj.addProperty(e.getKey().toString(), e.getValue());
        }
        root.add("playerLimits", limitsObj);
        AsyncFileWriter.writeAsync(file, GSON.toJson(root));
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
