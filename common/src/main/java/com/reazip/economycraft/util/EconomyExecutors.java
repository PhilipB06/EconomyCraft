package com.reazip.economycraft.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EconomyExecutors {
    private EconomyExecutors() {}

    public static ExecutorService newSingleThreadExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }
}
