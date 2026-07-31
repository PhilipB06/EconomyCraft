package com.reazip.economycraft.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AsyncFileWriter {
    private AsyncFileWriter() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EconomyCraft-IO");
        t.setDaemon(true);
        return t;
    });

    public static void writeAsync(Path file, String content) {
        EXECUTOR.execute(() -> {
            try {
                Files.writeString(file, content, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                LOGGER.error("[EconomyCraft] Failed to write {}", file, ex);
            }
        });
    }

    public static void flush() {
        try {
            EXECUTOR.submit(() -> {}).get();
        } catch (Exception ignored) {}
    }
}
