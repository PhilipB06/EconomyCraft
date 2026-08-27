package com.reazip.economycraft.util;

public final class ExpirationUtil {
    private ExpirationUtil() {}

    public static long expiresAt(long createdAt, int expirationHours) {
        return expirationHours <= 0 ? 0L : createdAt + expirationHours * 3_600_000L;
    }

    public static boolean isExpired(long expiresAt, long now) {
        return expiresAt > 0 && now >= expiresAt;
    }
}
