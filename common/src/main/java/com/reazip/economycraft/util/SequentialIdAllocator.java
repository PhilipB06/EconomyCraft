package com.reazip.economycraft.util;

import org.slf4j.Logger;

import java.util.Map;

public final class SequentialIdAllocator {
    private SequentialIdAllocator() {}

    public static int nextId(int candidate, Map<Integer, ?> existing, Logger logger, String what) {
        if (candidate != Integer.MAX_VALUE || !existing.containsKey(candidate)) {
            return candidate;
        }
        logger.error("[EconomyCraft] {} id space exhausted at {}; scanning for a free id instead of overwriting an existing entry.", what, candidate);
        for (int probe = 1; probe < Integer.MAX_VALUE; probe++) {
            if (!existing.containsKey(probe)) return probe;
        }
        throw new IllegalStateException("No free " + what + " id available; the id space is completely full.");
    }

    public static int advance(int id) {
        return id < Integer.MAX_VALUE ? id + 1 : Integer.MAX_VALUE;
    }
}
