package com.reazip.economycraft.util;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TransactionLogReader {
    private TransactionLogReader() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ENTRIES = 2000;

    public static List<TransactionEntry> readForPlayer(Path dir, UUID player) {
        List<TransactionEntry> entries = new ArrayList<>();
        for (Path file : sortedLogFilesNewestFirst(dir)) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("[EconomyCraft] Failed to read transaction log {}", file, e);
                continue;
            }
            for (int i = lines.size() - 1; i >= 0; i--) {
                TransactionEntry entry = parseLine(lines.get(i));
                if (entry != null && player.equals(entry.player())) {
                    entries.add(entry);
                    if (entries.size() >= MAX_ENTRIES) return entries;
                }
            }
        }
        return entries;
    }

    private static @Nullable TransactionEntry parseLine(String line) {
        if (line.isBlank()) return null;
        try {
            return TransactionEntry.fromJson(JsonParser.parseString(line).getAsJsonObject());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<Path> sortedLogFilesNewestFirst(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, TransactionLogWriter.FILE_PREFIX + "*" + TransactionLogWriter.FILE_SUFFIX)) {
            for (Path candidate : stream) {
                files.add(candidate);
            }
        } catch (IOException e) {
            LOGGER.warn("[EconomyCraft] Failed to list transaction logs at {}", dir, e);
            return List.of();
        }
        files.sort(Collections.reverseOrder());
        return files;
    }
}
