package com.reazip.economycraft.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
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
    private static final String IMPORTED_DIR_NAME = "economycraft_imported";
    private static final List<String> SETTINGS_FILES = List.of(
            "config.json",
            "prices.json"
    );
    private static final List<String> DATA_FILES = List.of(
            "balances.json",
            "daily.json",
            "daily_sells.json",
            "deliveries.json",
            "shop.json",
            "orders.json"
    );

    public static Path configDir(MinecraftServer server) {
        return prepareRoot(server);
    }

    public static Path dataDir(MinecraftServer server) {
        Path dir = prepareRoot(server).resolve(DATA_DIR_NAME);
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

        if (!copyAll(shared, dir, SETTINGS_FILES) || !copyAll(shared.resolve(DATA_DIR_NAME), data, DATA_FILES)) {
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
