package com.reazip.economycraft.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebhookNotifier {
    private WebhookNotifier() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EconomyCraft-Webhook");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void send(String url, String message) {
        if (url == null || url.isBlank() || message == null || message.isBlank()) return;

        EXECUTOR.execute(() -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("content", message);

            HttpRequest request;
            try {
                request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
                        .build();
            } catch (IllegalArgumentException e) {
                LOGGER.error("[EconomyCraft] webhook_url is not a valid URL; check webhook.json", e);
                return;
            }

            try {
                HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 300) {
                    LOGGER.warn("[EconomyCraft] Webhook request to {} returned status {}", redact(url), response.statusCode());
                }
            } catch (Exception e) {
                LOGGER.error("[EconomyCraft] Failed to send webhook notification to {}", redact(url), e);
            }
        });
    }

    private static String redact(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null ? uri.getScheme() + "://" + uri.getHost() + "/..." : "<redacted>";
        } catch (IllegalArgumentException e) {
            return "<redacted>";
        }
    }
}
