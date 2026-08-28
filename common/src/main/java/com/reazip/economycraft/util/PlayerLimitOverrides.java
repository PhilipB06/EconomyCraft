package com.reazip.economycraft.util;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerLimitOverrides {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, Integer> overrides = new ConcurrentHashMap<>();

    public Integer get(UUID player) {
        return overrides.get(player);
    }

    public void set(UUID player, Integer limit) {
        if (limit == null) {
            overrides.remove(player);
        } else {
            overrides.put(player, limit);
        }
    }

    public int effectiveLimit(UUID player, int serverDefault) {
        Integer override = overrides.get(player);
        return override != null ? override : serverDefault;
    }

    public boolean hasReachedLimit(UUID player, int activeCount, int serverDefault) {
        int limit = effectiveLimit(player, serverDefault);
        return limit > 0 && activeCount >= limit;
    }

    public void loadFrom(JsonObject root) {
        if (!root.has("playerLimits")) return;
        for (var e : root.getAsJsonObject("playerLimits").entrySet()) {
            try {
                overrides.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
            } catch (RuntimeException ex) {
                LOGGER.error("[EconomyCraft] Dropping an unreadable player limit override entry '{}'", e.getKey(), ex);
            }
        }
    }

    public void saveTo(JsonObject root) {
        JsonObject limitsObj = new JsonObject();
        for (var e : overrides.entrySet()) {
            limitsObj.addProperty(e.getKey().toString(), e.getValue());
        }
        root.add("playerLimits", limitsObj);
    }
}
