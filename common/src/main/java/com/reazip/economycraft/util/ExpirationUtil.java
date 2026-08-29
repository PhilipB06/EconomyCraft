package com.reazip.economycraft.util;

public final class ExpirationUtil {
    private ExpirationUtil() {}

    public static long expiresAt(long createdAt, int expirationHours) {
        return expirationHours <= 0 ? 0L : createdAt + expirationHours * 3_600_000L;
    }

    public static boolean isExpired(long expiresAt, long now) {
        return expiresAt > 0 && now >= expiresAt;
    }

    public static String expiresInLabel(long expiresAt) {
        if (expiresAt <= 0) return "Never expires";
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) return "Expired";
        long hours = Math.max(1, (long) Math.ceil(remaining / 3_600_000.0));
        return "Expires in " + hours + (hours == 1 ? " hour" : " hours");
    }
}
