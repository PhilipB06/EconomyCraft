package com.reazip.economycraft.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UuidLongMapStore {
    private UuidLongMapStore() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Long>>(){}.getType();

    public static void load(Path file, Map<UUID, Long> target) {
        if (Files.notExists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<UUID, Long> map = GSON.fromJson(json, TYPE);
            if (map != null) {
                for (Map.Entry<UUID, Long> e : map.entrySet()) {
                    if (e.getValue() != null) target.put(e.getKey(), e.getValue());
                }
            }
        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Failed to read {}", file, ex);
        }
    }

    public static void persist(Path file, Map<UUID, Long> map) {
        AsyncFileWriter.writeAsync(file, GSON.toJson(new HashMap<>(map), TYPE));
    }
}
