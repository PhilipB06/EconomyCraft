package com.reazip.economycraft.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reazip.economycraft.api.v1.BalanceMutationType;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record TransactionEntry(
        Instant time,
        BalanceMutationType type,
        UUID player,
        @Nullable String playerName,
        @Nullable UUID counterparty,
        @Nullable String counterpartyName,
        long amount,
        long balanceBefore,
        long balanceAfter,
        @Nullable String source,
        @Nullable String detail
) {
    public static @Nullable TransactionEntry fromJson(JsonObject obj) {
        try {
            return new TransactionEntry(
                    Instant.parse(obj.get("time").getAsString()),
                    BalanceMutationType.valueOf(obj.get("type").getAsString()),
                    UUID.fromString(obj.get("player").getAsString()),
                    optionalString(obj, "player_name"),
                    optionalUuid(obj, "counterparty"),
                    optionalString(obj, "counterparty_name"),
                    obj.get("amount").getAsLong(),
                    obj.get("balance_before").getAsLong(),
                    obj.get("balance_after").getAsLong(),
                    optionalString(obj, "source"),
                    optionalString(obj, "detail")
            );
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable String optionalString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static @Nullable UUID optionalUuid(JsonObject obj, String key) {
        String value = optionalString(obj, key);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
