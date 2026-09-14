package com.reazip.economycraft.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public final class TransactionLogWriter {
    private TransactionLogWriter() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    static final String FILE_PREFIX = "transactions-";
    static final String FILE_SUFFIX = ".log";
    private static final ExecutorService EXECUTOR = EconomyExecutors.newSingleThreadExecutor("EconomyCraft-TxLog");
    private static final Map<Path, LocalDate> LAST_CLEANUP_DAY = new ConcurrentHashMap<>();

    public static void append(Path dir, int retentionDays, String jsonLine) {
        EXECUTOR.execute(() -> {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            if (!today.equals(LAST_CLEANUP_DAY.put(dir, today))) {
                cleanupNow(dir, retentionDays);
            }

            Path file = dir.resolve(fileName(today));
            try {
                Files.writeString(file, jsonLine + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOGGER.error("[EconomyCraft] Failed to write transaction log {}", file, e);
            }
        });
    }

    public static void cleanup(Path dir, int retentionDays) {
        EXECUTOR.execute(() -> cleanupNow(dir, retentionDays));
    }

    private static void cleanupNow(Path dir, int retentionDays) {
        if (!Files.isDirectory(dir)) return;

        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (Path candidate : stream) {
                LocalDate fileDate = parseDate(candidate);
                if (fileDate == null || fileDate.isBefore(cutoff)) {
                    deleteQuietly(candidate);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Failed to scan transaction logs at {}", dir, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Failed to delete expired transaction log {}", file, e);
        }
    }

    private static String fileName(LocalDate day) {
        return FILE_PREFIX + day + FILE_SUFFIX;
    }

    private static LocalDate parseDate(Path path) {
        String name = path.getFileName().toString();
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) return null;

        String datePart = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
        try {
            return LocalDate.parse(datePart);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
