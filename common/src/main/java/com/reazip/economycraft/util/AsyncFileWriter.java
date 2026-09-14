package com.reazip.economycraft.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;

public final class AsyncFileWriter {
    private AsyncFileWriter() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService EXECUTOR = EconomyExecutors.newSingleThreadExecutor("EconomyCraft-IO");

    public static void writeAsync(Path file, String content) {
        EXECUTOR.execute(() -> {
            try {
                writeAtomically(file, content);
            } catch (Exception ex) {
                LOGGER.error("[EconomyCraft] Failed to write {}", file, ex);
            }
        });
    }

    private static void writeAtomically(Path file, String content) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }
    }

    public static void flush() {
        try {
            EXECUTOR.submit(() -> {}).get();
        } catch (Exception ignored) {}
    }
}
