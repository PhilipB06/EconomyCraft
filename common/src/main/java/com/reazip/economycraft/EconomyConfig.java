package com.reazip.economycraft;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public class EconomyConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_RESOURCE_PATH = "/assets/economycraft/config.json"; // Путь к встроенному файлу конфига по умолчанию
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public long startingBalance;          // Начальный баланс
    public long dailyAmount;              // Ежедневная сумма
    public long dailySellLimit;           // Дневной лимит продажи
    public double taxRate;                // Ставка налога
    @SerializedName("pvp_balance_loss_percentage")
    public double pvpBalanceLossPercentage; // Процент потери баланса при PvP
    @SerializedName("standalone_commands")
    public boolean standaloneCommands;      // Использовать отдельные команды
    @SerializedName("standalone_admin_commands")
    public boolean standaloneAdminCommands; // Использовать отдельные админ-команды
    @SerializedName("scoreboard_enabled")
    public boolean scoreboardEnabled;       // Включить скорборд
    @SerializedName("server_shop_enabled")
    public boolean serverShopEnabled = true; // Включить серверный магазин

    private static EconomyConfig INSTANCE = new EconomyConfig();
    private static Path file;

    public static EconomyConfig get() {
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        Path dir = server != null ? server.getFile("config/economycraft") : Path.of("config/economycraft");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        file = dir.resolve("config.json");

        if (Files.notExists(file)) {
            copyDefaultFromJarOrThrow(); // Копируем встроенный файл, если пользовательского нет
        } else {
            mergeNewDefaultsFromBundledDefault(); // Добавляем отсутствующие поля из встроенного файла
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            EconomyConfig parsed = GSON.fromJson(json, EconomyConfig.class);
            if (parsed == null) {
                throw new IllegalStateException("config.json преобразован в null");
            }
            INSTANCE = parsed;
        } catch (Exception e) {
            throw new IllegalStateException("[EconomyCraft] Не удалось прочитать/разобрать config.json по пути " + file, e);
        }
    }

    public static void save() {
        if (file == null) {
            throw new IllegalStateException("[EconomyCraft] EconomyConfig не инициализирован. Сначала вызовите load().");
        }
        try {
            Files.writeString(
                    file,
                    GSON.toJson(INSTANCE),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException("[EconomyCraft] Не удалось сохранить config.json по пути " + file, e);
        }
    }

    /**
     * Копирует встроенный файл config.json из ресурсов JAR, если он там есть.
     * Если файла в ресурсах нет — выбрасывает исключение.
     */
    private static void copyDefaultFromJarOrThrow() {
        try (InputStream in = EconomyConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "[EconomyCraft] Отсутствует встроенный файл по умолчанию " + DEFAULT_RESOURCE_PATH +
                                " (возможно, вы забыли включить его в ресурсы?)"
                );
            }
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[EconomyCraft] Создан {} из встроенного файла по умолчанию {}", file, DEFAULT_RESOURCE_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("[EconomyCraft] Не удалось создать config.json по пути " + file, e);
        }
    }

    /**
     * Сливает текущий пользовательский config.json со встроенным файлом по умолчанию,
     * добавляя в пользовательский файл все недостающие поля из встроенного.
     * Если встроенного файла нет — слияние пропускается.
     */
    private static void mergeNewDefaultsFromBundledDefault() {
        JsonObject defaults = readBundledDefaultJson(); // Встроенный эталон
        if (defaults == null) {
            LOGGER.warn("[EconomyCraft] Встроенные настройки по умолчанию не найдены; пропуск слияния конфигов.");
            return;
        }

        JsonObject userRoot;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                LOGGER.warn("[EconomyCraft] Корень config.json не является объектом, пропуск слияния.");
                return;
            }
            userRoot = parsed.getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException("[EconomyCraft] Не удалось прочитать/разобрать пользовательский config.json для слияния по пути " + file, ex);
        }

        int[] added = new int[]{0}; // счётчик добавленных полей
        addMissingRecursive(userRoot, defaults, added);

        if (added[0] > 0) {
            try {
                Files.writeString(file, GSON.toJson(userRoot), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("[EconomyCraft] Не удалось записать объединённый config.json по пути " + file, ex);
            }
        }
    }

    /**
     * Читает встроенный config.json из ресурсов JAR и возвращает его как JsonObject.
     * Возвращает null, если файл не найден или его содержимое не является объектом.
     */
    private static JsonObject readBundledDefaultJson() {
        try (InputStream in = EconomyConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) return null;

            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return null;

            return parsed.getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException("[EconomyCraft] Не удалось прочитать встроенный config.json по умолчанию из " + DEFAULT_RESOURCE_PATH, ex);
        }
    }

    /**
     * Рекурсивно добавляет в target все поля из defaults, которые отсутствуют в target.
     * @param target  пользовательский объект JSON (будет изменён)
     * @param defaults эталонный объект JSON из встроенного файла
     * @param added   счётчик добавленных полей (изменяемый массив для передачи по ссылке)
     */
    private static void addMissingRecursive(JsonObject target, JsonObject defaults, int[] added) {
        for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
            String key = e.getKey();
            JsonElement defVal = e.getValue();

            if (!target.has(key)) {
                target.add(key, defVal == null ? JsonNull.INSTANCE : defVal.deepCopy());
                added[0]++;
                continue;
            }

            JsonElement curVal = target.get(key);
            if (curVal != null && curVal.isJsonObject()
                    && defVal != null && defVal.isJsonObject()) {
                addMissingRecursive(curVal.getAsJsonObject(), defVal.getAsJsonObject(), added);
            }
        }
    }
}