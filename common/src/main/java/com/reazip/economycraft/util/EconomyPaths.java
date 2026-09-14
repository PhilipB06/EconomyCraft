package com.reazip.economycraft.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class EconomyPaths {
    private EconomyPaths() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "economycraft";
    private static final String DATA_DIR_NAME = "data";
    private static final String LOGS_DIR_NAME = "logs";
    private static final String IMPORTED_DIR_NAME = "economycraft_imported";
    private static final String BACKUP_DIR_SUFFIX = "_backup_";
    private static final List<String> SETTINGS_FILES = List.of(
            "config.json",
            "webhook.json",
            "prices.json"
    );
    private static final List<String> DATA_FILES = List.of(
            "balances.json",
            "daily.json",
            "daily_sells.json",
            "deliveries.json",
            "auctions.json",
            "shop.json",
            "orders.json",
            "notifications.json",
            "player_activity.json"
    );

    public static Path configDir(MinecraftServer server) {
        return prepareRoot(server);
    }

    public static Path dataDir(MinecraftServer server) {
        Path dir = prepareRoot(server).resolve(DATA_DIR_NAME);
        createDirectories(dir);
        return dir;
    }

    public static Path logsDir(MinecraftServer server) {
        Path dir = prepareRoot(server).resolve(LOGS_DIR_NAME);
        createDirectories(dir);
        return dir;
    }

    private static Path prepareRoot(MinecraftServer server) {
        Path dir = root(server);
        createDirectories(dir);
        return dir;
    }

    private static Path root(MinecraftServer server) {
        return server.isDedicatedServer()
                ? sharedDir(server)
                : server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
    }

    private static Path sharedDir(MinecraftServer server) {
        return server.getFile("config/" + DIR_NAME);
    }

    public static boolean hasSharedFolder(MinecraftServer server) {
        if (server.isDedicatedServer()) return false;

        Path shared = sharedDir(server);
        if (!Files.isDirectory(shared)) return false;

        return hasAnyOf(shared, SETTINGS_FILES) || hasAnyOf(shared.resolve(DATA_DIR_NAME), DATA_FILES);
    }

    public static boolean importSharedFolder(MinecraftServer server) {
        if (!hasSharedFolder(server)) return false;

        Path shared = sharedDir(server);
        Path dir = root(server);
        Path data = dir.resolve(DATA_DIR_NAME);
        createDirectories(data);

        Path backup = null;
        if (hasAnyOf(dir, SETTINGS_FILES) || hasAnyOf(data, DATA_FILES)) {
            backup = backUpExisting(dir, data);
            if (backup == null) return false;
        }

        if (!copyAll(shared, dir, SETTINGS_FILES) || !copyAll(shared.resolve(DATA_DIR_NAME), data, DATA_FILES)) {
            if (backup != null) {
                LOGGER.error("[EconomyCraft] Import failed part-way; restoring the previous economy from {}", backup);
                if (!restoreBackup(backup, dir, data)) {
                    LOGGER.error("[EconomyCraft] Restore from {} was incomplete; {} may now hold a mix of old and " +
                            "partially-imported files. Manually copy the files from {} into {} to fix this.", backup, dir, backup, dir);
                }
            }
            return false;
        }

        Path imported = shared.resolveSibling(IMPORTED_DIR_NAME);
        try {
            Files.move(shared, imported);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Could not rename {} to {}", shared, imported, e);
            return false;
        }

        LOGGER.info("[EconomyCraft] Imported the settings and economy from {} into {} and renamed the old folder to {}.",
                shared, dir, imported);
        return true;
    }

    private static @Nullable Path backUpExisting(Path dir, Path data) {
        Path backup = dir.resolveSibling(dir.getFileName() + BACKUP_DIR_SUFFIX + System.currentTimeMillis());
        Path backupData = backup.resolve(DATA_DIR_NAME);
        createDirectories(backupData);

        if (!copyAll(dir, backup, SETTINGS_FILES) || !copyAll(data, backupData, DATA_FILES)) {
            LOGGER.error("[EconomyCraft] Could not back up the existing economy at {}; import aborted so nothing is lost.", dir);
            return null;
        }

        LOGGER.info("[EconomyCraft] Backed up the existing economy from {} to {}", dir, backup);
        return backup;
    }

    private static boolean restoreBackup(Path backup, Path dir, Path data) {
        boolean settingsOk = restoreInto(backup, dir, SETTINGS_FILES);
        boolean dataOk = restoreInto(backup.resolve(DATA_DIR_NAME), data, DATA_FILES);
        return settingsOk && dataOk;
    }

    private static boolean restoreInto(Path from, Path to, List<String> names) {
        boolean allOk = true;
        for (String name : names) {
            Path source = from.resolve(name);
            Path target = to.resolve(name);
            try {
                copyOrDelete(source, target);
            } catch (IOException e) {
                LOGGER.error("[EconomyCraft] Could not restore {} from {}; the backup is kept at {}", target, source, from, e);
                allOk = false;
            }
        }
        return allOk;
    }

    private static void copyOrDelete(Path source, Path target) throws IOException {
        if (Files.isRegularFile(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.deleteIfExists(target);
        }
    }

    private static boolean copyAll(Path from, Path to, List<String> names) {
        if (!Files.isDirectory(from)) return true;

        for (String name : names) {
            Path source = from.resolve(name);
            if (!Files.isRegularFile(source)) continue;

            Path target = to.resolve(name);
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.error("[EconomyCraft] Could not copy {} to {}", source, target, e);
                return false;
            }
        }

        return true;
    }

    private static boolean hasAnyOf(Path dir, List<String> names) {
        for (String name : names) {
            if (Files.isRegularFile(dir.resolve(name))) return true;
        }
        return false;
    }

    private static void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Could not create directory: {}", dir, e);
        }
    }
}
