package com.reazip.economycraft.orders;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.DeliveryManager;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.util.EconomyPaths;
import com.reazip.economycraft.util.ExpirationUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrderManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, OrderRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> limitOverrides = new ConcurrentHashMap<>();
    private final DeliveryManager deliveries;
    private final List<Runnable> listeners = new ArrayList<>();
    private int nextId = 1;

    public OrderManager(MinecraftServer server, DeliveryManager deliveries) {
        this.server = server;
        this.file = EconomyPaths.dataDir(server).resolve("orders.json");
        this.deliveries = deliveries;
        load();
    }

    public List<OrderRequest> getRequests() {
        List<OrderRequest> out = new ArrayList<>(requests.values());
        out.sort((a, b) -> Integer.compare(b.id, a.id));
        return out;
    }

    public OrderRequest getRequest(int id) {
        return requests.get(id);
    }

    public void addRequest(OrderRequest r) {
        r.id = nextId++;
        putAndPersist(r);
    }

    public OrderRequest removeRequest(int id) {
        OrderRequest r = requests.remove(id);
        if (r != null) {
            notifyListeners();
            save();
        }
        return r;
    }

    public void restoreRequest(OrderRequest request) {
        putAndPersist(request);
    }

    private void putAndPersist(OrderRequest request) {
        requests.put(request.id, request);
        notifyListeners();
        save();
    }

    public void markChanged() {
        notifyListeners();
        save();
    }

    public enum ClaimStatus { OK, ORDER_GONE, INVALID_AMOUNT, FULL_AMOUNT_REQUIRED, NOT_ENOUGH_ITEMS }

    public record ClaimResult(ClaimStatus status, OrderRequest order, int given, long payment, long escrowUsed, boolean exhausted) {
        public boolean success() {
            return status == ClaimStatus.OK;
        }
    }

    public ClaimResult claim(int id, int requestedAmount, int held, boolean clampToHeld) {
        ClaimResult[] outcome = new ClaimResult[1];
        requests.computeIfPresent(id, (key, order) -> {
            int give = requestedAmount <= 0 ? order.amount : Math.min(requestedAmount, order.amount);
            if (clampToHeld) {
                give = Math.min(give, held);
            }
            if (give <= 0) {
                outcome[0] = new ClaimResult(ClaimStatus.INVALID_AMOUNT, order, 0, 0, 0, false);
                return order;
            }
            if (OrderFulfillment.requiresCompleteFulfillment(order) && give < order.amount) {
                outcome[0] = new ClaimResult(ClaimStatus.FULL_AMOUNT_REQUIRED, order, 0, 0, 0, false);
                return order;
            }
            if (!clampToHeld && held < give) {
                outcome[0] = new ClaimResult(ClaimStatus.NOT_ENOUGH_ITEMS, order, 0, 0, 0, false);
                return order;
            }

            long payment = OrderFulfillment.partialPayment(order, give);
            long escrowUsed = Math.min(payment, Math.max(0, order.escrow));

            order.amount -= give;
            order.price -= payment;
            order.escrow -= escrowUsed;
            boolean exhausted = order.amount <= 0;

            outcome[0] = new ClaimResult(ClaimStatus.OK, order, give, payment, escrowUsed, exhausted);
            return exhausted ? null : order;
        });

        return outcome[0] != null
                ? outcome[0]
                : new ClaimResult(ClaimStatus.ORDER_GONE, null, 0, 0, 0, false);
    }

    public void rollbackClaim(OrderRequest order, int give, long payment, long escrowUsed, boolean exhausted) {
        requests.compute(order.id, (key, existing) -> {
            if (existing == null && !exhausted) {
                return null;
            }
            OrderRequest target = existing != null ? existing : order;
            target.amount += give;
            target.price += payment;
            target.escrow += escrowUsed;
            return target;
        });
    }

    public int countActive(UUID player) {
        int count = 0;
        for (OrderRequest r : requests.values()) {
            if (player.equals(r.requester)) count++;
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
        return override != null ? override : EconomyConfig.get().maxActiveOrdersPerPlayer;
    }

    public boolean hasReachedLimit(UUID player) {
        int limit = getEffectiveLimit(player);
        return limit > 0 && countActive(player) >= limit;
    }

    public void addDelivery(UUID player, ItemStack stack) {
        deliveries.addDelivery(player, stack);
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
                JsonArray savedRequests = root.has("requests")
                        ? root.getAsJsonArray("requests")
                        : new JsonArray();
                long now = System.currentTimeMillis();
                boolean migrated = false;
                for (var el : savedRequests) {
                    try {
                        OrderRequest r = OrderRequest.load(el.getAsJsonObject(), server.registryAccess());
                        if (r.item == null || r.item.isEmpty()) {
                            LOGGER.error("[EconomyCraft] Dropping order request {} with an unreadable item in {}", r.id, file);
                            continue;
                        }
                        if (r.createdAt <= 0) {
                            r.createdAt = now;
                            r.expiresAt = ExpirationUtil.expiresAt(now, EconomyConfig.get().orderExpirationHours);
                            migrated = true;
                        }
                        requests.put(r.id, r);
                    } catch (Exception ex) {
                        LOGGER.error("[EconomyCraft] Dropping an unreadable order request in {}", file, ex);
                    }
                }
                if (root.has("playerLimits")) {
                    for (var e : root.getAsJsonObject("playerLimits").entrySet()) {
                        try {
                            limitOverrides.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                        } catch (Exception ex) {
                            LOGGER.error("[EconomyCraft] Dropping an unreadable order limit override in {}", file, ex);
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
        JsonArray reqArr = new JsonArray();
        for (OrderRequest r : requests.values()) {
            reqArr.add(r.save(server.registryAccess()));
        }
        root.add("requests", reqArr);
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
