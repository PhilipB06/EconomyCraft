package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.util.EconomyPaths;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class WebhookConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_RESOURCE_PATH = "/assets/economycraft/webhook.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    @SerializedName("webhook_enabled")
    public boolean webhookEnabled = false;
    @SerializedName("webhook_url")
    public String webhookUrl = "";
    @SerializedName("webhook_min_amount")
    public long webhookMinAmount = 0;

    private static WebhookConfig INSTANCE = new WebhookConfig();
    private static Path file;

    public static WebhookConfig get() {
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        file = EconomyPaths.configDir(server).resolve("webhook.json");

        if (Files.notExists(file)) {
            copyDefaultFromJarOrThrow();
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            WebhookConfig parsed = GSON.fromJson(json, WebhookConfig.class);
            if (parsed == null) {
                throw new IllegalStateException("webhook.json parsed to null");
            }
            if (parsed.webhookMinAmount < 0) {
                LOGGER.warn("[EconomyCraft] webhook_min_amount ({}) is negative; clamping to 0.", parsed.webhookMinAmount);
                parsed.webhookMinAmount = 0;
            }
            if (parsed.webhookUrl == null) {
                parsed.webhookUrl = "";
            }
            INSTANCE = parsed;
        } catch (Exception e) {
            throw new IllegalStateException("[EconomyCraft] Failed to read/parse webhook.json at " + file, e);
        }
    }

    private static void copyDefaultFromJarOrThrow() {
        try (InputStream in = WebhookConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "[EconomyCraft] Missing bundled default " + DEFAULT_RESOURCE_PATH +
                                " (did you forget to include it in resources?)"
                );
            }
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[EconomyCraft] Created {} from bundled default {}", file, DEFAULT_RESOURCE_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("[EconomyCraft] Failed to create webhook.json at " + file, e);
        }
    }
}
