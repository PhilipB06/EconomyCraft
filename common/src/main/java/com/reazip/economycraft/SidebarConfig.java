package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SidebarConfig {
    public static final int CURRENT_VERSION = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private static final int MIN_REFRESH_SECONDS = 5;

    public int version = CURRENT_VERSION;
    public boolean enabled = true;
    public List<WidgetConfig> widgets = new ArrayList<>();

    private static SidebarConfig INSTANCE = new SidebarConfig();
    private static Path file;

    public static SidebarConfig get() {
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        Path dir = server != null ? server.getFile("config/economycraft") : Path.of("config/economycraft");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        file = dir.resolve("sidebar-config.json");

        if (Files.notExists(file)) {
            INSTANCE = defaultConfig();
            save();
            LOGGER.info("[EconomyCraft] Created {}", file);
            warnDeprecatedScoreboardConfig();
            return;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            SidebarConfig parsed = parse(json);
            INSTANCE = parsed;
        } catch (Exception ex) {
            LOGGER.warn("[EconomyCraft] Failed to read sidebar-config.json; using defaults.", ex);
            INSTANCE = defaultConfig();
            save();
        }
        warnDeprecatedScoreboardConfig();
    }

    public static void save() {
        if (file == null) {
            throw new IllegalStateException("[EconomyCraft] SidebarConfig not initialized. Call load() first.");
        }
        try {
            JsonElement json = toJson(INSTANCE);
            Files.writeString(
                    file,
                    GSON.toJson(json),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException("[EconomyCraft] Failed to save sidebar-config.json at " + file, e);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static void warnDeprecatedScoreboardConfig() {
        LOGGER.warn("[EconomyCraft] Deprecated: scoreboard_enabled/scoreboard_mode/scoreboard_stats are ignored. " +
                "Use config/economycraft/sidebar-config.json instead.");
    }

    private static SidebarConfig defaultConfig() {
        SidebarConfig config = new SidebarConfig();
        config.version = CURRENT_VERSION;
        config.enabled = true;
        LeaderboardWidgetConfig widget = new LeaderboardWidgetConfig();
        widget.id = "top_balance";
        widget.type = WidgetType.LEADERBOARD.id;
        widget.enabled = true;
        widget.title = "TOP BALANCE";
        widget.refreshSeconds = 60;
        widget.leaderboard = new LeaderboardWidgetConfig.LeaderboardConfig();
        widget.leaderboard.metric = "balance";
        widget.leaderboard.limit = 5;
        config.widgets.add(widget);
        return config;
    }

    private static SidebarConfig parse(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            LOGGER.warn("[EconomyCraft] sidebar-config.json root is not an object. Using defaults.");
            return defaultConfig();
        }
        JsonObject root = parsed.getAsJsonObject();
        SidebarConfig config = new SidebarConfig();
        config.version = getInt(root, "version", CURRENT_VERSION);
        config.enabled = getBoolean(root, "enabled", true);
        config.widgets = parseWidgets(root.getAsJsonArray("widgets"));
        return config;
    }

    private static List<WidgetConfig> parseWidgets(JsonArray array) {
        List<WidgetConfig> widgets = new ArrayList<>();
        if (array == null) return widgets;
        Set<String> ids = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                LOGGER.warn("[EconomyCraft] Sidebar widget entry is not an object; skipping.");
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String type = getString(obj, "type", null);
            if (type == null || type.isBlank()) {
                LOGGER.warn("[EconomyCraft] Sidebar widget missing type; skipping.");
                continue;
            }
            WidgetConfig widget = switch (type) {
                case "leaderboard" -> parseLeaderboardWidget(obj);
                case "player_stats" -> parsePlayerStatsWidget(obj);
                default -> {
                    LOGGER.warn("[EconomyCraft] Unknown sidebar widget type '{}'; skipping.", type);
                    yield null;
                }
            };
            if (widget == null) continue;
            if (widget.id == null || widget.id.isBlank()) {
                LOGGER.warn("[EconomyCraft] Sidebar widget missing id; skipping.");
                continue;
            }
            if (!ids.add(widget.id)) {
                LOGGER.warn("[EconomyCraft] Duplicate sidebar widget id '{}'; skipping.", widget.id);
                continue;
            }
            if (widget.title == null || widget.title.isBlank()) {
                LOGGER.warn("[EconomyCraft] Sidebar widget '{}' missing title; skipping.", widget.id);
                continue;
            }
            widgets.add(widget);
        }
        return widgets;
    }

    private static LeaderboardWidgetConfig parseLeaderboardWidget(JsonObject obj) {
        LeaderboardWidgetConfig widget = new LeaderboardWidgetConfig();
        widget.id = getString(obj, "id", null);
        widget.type = WidgetType.LEADERBOARD.id;
        widget.enabled = getBoolean(obj, "enabled", true);
        widget.title = getString(obj, "title", "TOP BALANCE");
        widget.refreshSeconds = clampRefresh(getInt(obj, "refresh_seconds", 60));
        JsonObject lbObj = obj.getAsJsonObject("leaderboard");
        if (lbObj == null) {
            LOGGER.warn("[EconomyCraft] Leaderboard widget missing leaderboard settings; skipping.");
            return null;
        }
        LeaderboardWidgetConfig.LeaderboardConfig lb = new LeaderboardWidgetConfig.LeaderboardConfig();
        lb.metric = getString(lbObj, "metric", "balance");
        lb.limit = Math.max(1, getInt(lbObj, "limit", 5));
        if (!"balance".equalsIgnoreCase(lb.metric)) {
            LOGGER.warn("[EconomyCraft] Unsupported leaderboard metric '{}'; skipping widget '{}'.", lb.metric, widget.id);
            return null;
        }
        widget.leaderboard = lb;
        return widget;
    }

    private static PlayerStatsWidgetConfig parsePlayerStatsWidget(JsonObject obj) {
        PlayerStatsWidgetConfig widget = new PlayerStatsWidgetConfig();
        widget.id = getString(obj, "id", null);
        widget.type = WidgetType.PLAYER_STATS.id;
        widget.enabled = getBoolean(obj, "enabled", true);
        widget.title = getString(obj, "title", "STATS");
        widget.refreshSeconds = clampRefresh(getInt(obj, "refresh_seconds", 30));
        widget.target = getString(obj, "target", "viewer");
        if (!"viewer".equalsIgnoreCase(widget.target)) {
            LOGGER.warn("[EconomyCraft] Unsupported player_stats target '{}' for widget '{}'; skipping.", widget.target, widget.id);
            return null;
        }
        JsonArray linesArray = obj.getAsJsonArray("lines");
        List<PlayerStatsWidgetConfig.LineConfig> lines = new ArrayList<>();
        if (linesArray != null) {
            for (JsonElement element : linesArray) {
                if (!element.isJsonObject()) continue;
                JsonObject lineObj = element.getAsJsonObject();
                String key = getString(lineObj, "key", null);
                if (key == null || key.isBlank()) {
                    LOGGER.warn("[EconomyCraft] player_stats line missing key; skipping.");
                    continue;
                }
                PlayerStatsWidgetConfig.LineConfig line = new PlayerStatsWidgetConfig.LineConfig();
                line.key = key;
                line.enabled = getBoolean(lineObj, "enabled", true);
                line.label = getString(lineObj, "label", key);
                JsonObject sourceObj = lineObj.getAsJsonObject("source");
                line.source = parseSource(sourceObj);
                JsonObject formatObj = lineObj.getAsJsonObject("format");
                line.format = parseFormat(formatObj);
                lines.add(line);
            }
        }
        widget.lines = lines;
        return widget;
    }

    private static PlayerStatsWidgetConfig.SourceConfig parseSource(JsonObject obj) {
        PlayerStatsWidgetConfig.SourceConfig source = new PlayerStatsWidgetConfig.SourceConfig();
        if (obj == null) {
            source.type = "unknown";
            return source;
        }
        source.type = getString(obj, "type", "unknown");
        source.stat = getString(obj, "stat", null);
        return source;
    }

    private static PlayerStatsWidgetConfig.FormatConfig parseFormat(JsonObject obj) {
        PlayerStatsWidgetConfig.FormatConfig format = new PlayerStatsWidgetConfig.FormatConfig();
        if (obj == null) {
            format.type = "text";
            return format;
        }
        format.type = getString(obj, "type", "text");
        format.decimals = getInt(obj, "decimals", 0);
        format.unit = getString(obj, "unit", null);
        format.style = getString(obj, "style", null);
        format.fallback = getString(obj, "fallback", null);
        return format;
    }

    private static int clampRefresh(int refreshSeconds) {
        return Math.max(MIN_REFRESH_SECONDS, refreshSeconds);
    }

    private static JsonElement toJson(SidebarConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("version", config.version);
        root.addProperty("enabled", config.enabled);
        JsonArray widgets = new JsonArray();
        for (WidgetConfig widget : config.widgets) {
            JsonObject widgetJson = new JsonObject();
            widgetJson.addProperty("id", widget.id);
            widgetJson.addProperty("type", widget.type);
            widgetJson.addProperty("enabled", widget.enabled);
            widgetJson.addProperty("title", widget.title);
            widgetJson.addProperty("refresh_seconds", widget.refreshSeconds);
            if (widget instanceof LeaderboardWidgetConfig leaderboardWidget) {
                JsonObject lb = new JsonObject();
                lb.addProperty("metric", leaderboardWidget.leaderboard.metric);
                lb.addProperty("limit", leaderboardWidget.leaderboard.limit);
                widgetJson.add("leaderboard", lb);
            } else if (widget instanceof PlayerStatsWidgetConfig statsWidget) {
                widgetJson.addProperty("target", statsWidget.target);
                JsonArray lines = new JsonArray();
                for (PlayerStatsWidgetConfig.LineConfig line : statsWidget.lines) {
                    JsonObject lineJson = new JsonObject();
                    lineJson.addProperty("key", line.key);
                    lineJson.addProperty("enabled", line.enabled);
                    lineJson.addProperty("label", line.label);
                    JsonObject source = new JsonObject();
                    source.addProperty("type", line.source.type);
                    if (line.source.stat != null) {
                        source.addProperty("stat", line.source.stat);
                    }
                    lineJson.add("source", source);
                    JsonObject format = new JsonObject();
                    format.addProperty("type", line.format.type);
                    if (line.format.decimals != null) {
                        format.addProperty("decimals", line.format.decimals);
                    }
                    if (line.format.unit != null) {
                        format.addProperty("unit", line.format.unit);
                    }
                    if (line.format.style != null) {
                        format.addProperty("style", line.format.style);
                    }
                    if (line.format.fallback != null) {
                        format.addProperty("fallback", line.format.fallback);
                    }
                    lineJson.add("format", format);
                    lines.add(lineJson);
                }
                widgetJson.add("lines", lines);
            }
            widgets.add(widgetJson);
        }
        root.add("widgets", widgets);
        return root;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            return element.getAsString();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            return element.getAsInt();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            return element.getAsBoolean();
        } catch (Exception ex) {
            return fallback;
        }
    }

    public enum WidgetType {
        LEADERBOARD("leaderboard"),
        PLAYER_STATS("player_stats");

        public final String id;

        WidgetType(String id) {
            this.id = id;
        }
    }

    public static abstract class WidgetConfig {
        public String id;
        public String type;
        public boolean enabled;
        public String title;
        public int refreshSeconds;
    }

    public static final class LeaderboardWidgetConfig extends WidgetConfig {
        public LeaderboardConfig leaderboard;

        public static final class LeaderboardConfig {
            public String metric = "balance";
            public int limit = 5;
        }
    }

    public static final class PlayerStatsWidgetConfig extends WidgetConfig {
        public String target = "viewer";
        public List<LineConfig> lines = new ArrayList<>();

        public static final class LineConfig {
            public String key;
            public boolean enabled;
            public String label;
            public SourceConfig source = new SourceConfig();
            public FormatConfig format = new FormatConfig();
        }

        public static final class SourceConfig {
            public String type;
            public String stat;
        }

        public static final class FormatConfig {
            public String type;
            public Integer decimals;
            public String unit;
            public String style;
            public String fallback;
        }
    }
}
