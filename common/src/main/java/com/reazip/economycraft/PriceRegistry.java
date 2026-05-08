package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import com.reazip.economycraft.util.IdentifierCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class PriceRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_RESOURCE_PATH = "/assets/economycraft/prices.json"; // Путь к встроенному файлу цен
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private final Path file;
    private final Map<IdentifierCompat.Id, PriceEntry> prices = new LinkedHashMap<>(); // Карта цен

    public record ResolvedPrice(IdentifierCompat.Id key, PriceEntry entry) {} // Разрешенная цена

    public PriceRegistry(MinecraftServer server) {
        Path dir = server.getFile("config/economycraft");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Не удалось создать директорию конфига: {}", dir, e);
        }

        this.file = dir.resolve("prices.json");

        if (Files.notExists(this.file)) {
            createFromBundledDefault(); // Создаём из встроенного файла, если пользовательского нет
        } else {
            mergeNewDefaultsFromBundledDefault(); // Добавляем отсутствующие записи из встроенного файла
        }

        reload(); // Перезагружаем цены
    }

    public void reload() {
        this.prices.clear();

        if (Files.notExists(file)) {
            LOGGER.warn("[EconomyCraft] prices.json не найден по пути {} (карта цен будет пустой).", file);
            return;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                LOGGER.error("[EconomyCraft] prices.json пуст или содержит неверный JSON: {}", file);
                return;
            }

            int missingItemCount = 0; // Счётчик отсутствующих предметов
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String key = e.getKey();
                IdentifierCompat.Id id = IdentifierCompat.tryParse(key);
                if (id == null) {
                    LOGGER.warn("[EconomyCraft] Неверный ID предмета в prices.json: {}", key);
                    continue;
                }

                boolean isRealItem = IdentifierCompat.registryContainsKey(BuiltInRegistries.ITEM, id);
                boolean isVirtual = isVirtualPriceId(id); // Виртуальный ID (зелья, зачарованные книги)

                if (!isRealItem && !isVirtual) {
                    missingItemCount++;
                    continue;
                }

                JsonElement el = e.getValue();
                if (el == null || !el.isJsonObject()) {
                    LOGGER.warn("[EconomyCraft] Неверная запись для {} (ожидался объект).", key);
                    continue;
                }

                JsonObject obj = el.getAsJsonObject();
                String category = getString(obj, "category", "misc"); // Категория
                int stack = getInt(obj, "stack", 1); // Размер стака
                long unitBuy = getLong(obj, "unit_buy", 0L); // Цена покупки за единицу
                long unitSell = getLong(obj, "unit_sell", 0L); // Цена продажи за единицу

                PriceEntry entry = new PriceEntry(id, category, stack, unitBuy, unitSell);
                prices.put(id, entry);
            }

            if (missingItemCount > 0) {
                LOGGER.warn("[EconomyCraft] Пропущено {} записей цен для предметов, отсутствующих в этой версии сервера.", missingItemCount);
            }

            LOGGER.info("[EconomyCraft] Загружено {} записей цен из {}", prices.size(), file);
        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Не удалось загрузить prices.json из {}", file, ex);
        }
    }

    public PriceEntry get(IdentifierCompat.Id id) {
        return prices.get(id);
    }

    public PriceEntry get(ItemStack stack) {
        ResolvedPrice rp = resolve(stack);
        return rp != null ? rp.entry() : null;
    }

    public ResolvedPrice resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        for (IdentifierCompat.Id key : resolvePriceKeys(stack)) { // Получаем ключи для поиска цены
            PriceEntry p = prices.get(key);
            if (p != null) return new ResolvedPrice(key, p);
        }
        return null;
    }

    public Long getUnitBuy(ItemStack stack) {
        PriceEntry p = get(stack);
        return (p != null && p.unitBuy() > 0) ? p.unitBuy() : null;
    }

    public Long getUnitSell(ItemStack stack) {
        PriceEntry p = get(stack);
        return (p != null && p.unitSell() > 0) ? p.unitSell() : null;
    }

    public Integer getStackSize(ItemStack stack) {
        PriceEntry p = get(stack);
        return (p != null && p.stack() > 0) ? p.stack() : null;
    }

    public boolean canBuyUnit(ItemStack stack) {
        PriceEntry p = get(stack);
        return p != null && p.unitBuy() > 0;
    }

    public boolean canSellUnit(ItemStack stack) {
        PriceEntry p = get(stack);
        return p != null && p.unitSell() > 0;
    }

    public boolean isSellBlockedByDamage(ItemStack stack) {
        return stack != null && stack.isDamageableItem() && stack.getDamageValue() > 0;
    }

    public boolean isSellBlockedByContents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItems().iterator().hasNext()) return true;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle != null && !bundle.isEmpty();
    }

    public Collection<PriceEntry> all() {
        return Collections.unmodifiableCollection(prices.values());
    }

    public Set<String> categories() {
        Set<String> out = new LinkedHashSet<>();
        for (PriceEntry p : prices.values()) out.add(p.category());
        return out;
    }

    public Set<String> buyCategories() {
        Set<String> out = new LinkedHashSet<>();
        for (PriceEntry p : prices.values()) {
            if (p.unitBuy() > 0) {
                out.add(p.category());
            }
        }
        return out;
    }

    public List<PriceEntry> byCategory(String category) {
        if (category == null) return List.of();
        String c = category.trim().toLowerCase(Locale.ROOT);

        List<PriceEntry> out = new ArrayList<>();
        for (PriceEntry p : prices.values()) {
            if (p.category() != null && p.category().trim().toLowerCase(Locale.ROOT).equals(c)) {
                out.add(p);
            }
        }
        return out;
    }

    public List<PriceEntry> buyableByCategory(String category) {
        if (category == null) return List.of();
        String c = category.trim().toLowerCase(Locale.ROOT);

        List<PriceEntry> out = new ArrayList<>();
        for (PriceEntry p : prices.values()) {
            if (p.unitBuy() <= 0) continue;
            if (p.category() != null && p.category().trim().toLowerCase(Locale.ROOT).equals(c)) {
                out.add(p);
            }
        }
        return out;
    }

    public List<String> buyTopCategories() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (PriceEntry p : prices.values()) {
            if (p.unitBuy() <= 0 || p.category() == null) continue;
            String cat = p.category();
            int dot = cat.indexOf('.'); // Разделитель категорий
            if (dot > 0) {
                out.add(cat.substring(0, dot));
            } else {
                out.add(cat);
            }
        }
        return new ArrayList<>(out);
    }

    public List<String> buySubcategories(String topCategory) {
        if (topCategory == null || topCategory.isBlank()) return List.of();
        String root = topCategory.trim().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (PriceEntry p : prices.values()) {
            if (p.unitBuy() <= 0 || p.category() == null) continue;
            String cat = p.category().trim();
            int dot = cat.indexOf('.');
            if (dot <= 0 || dot >= cat.length() - 1) continue;
            String base = cat.substring(0, dot).toLowerCase(Locale.ROOT);
            String sub = cat.substring(dot + 1);
            if (base.equals(root)) {
                out.add(sub);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Создаёт файл prices.json из встроенного файла по умолчанию.
     * Если встроенного файла нет — создаёт пустой JSON.
     */
    private void createFromBundledDefault() {
        try (InputStream in = PriceRegistry.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.error("[EconomyCraft] Встроенный файл цен по умолчанию не найден по пути {}. Создаётся пустой {}",
                        DEFAULT_RESOURCE_PATH, file);
                Files.writeString(file, "{}", StandardCharsets.UTF_8);
                return;
            }

            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[EconomyCraft] Создан {} из встроенного файла по умолчанию {}", file, DEFAULT_RESOURCE_PATH);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Не удалось создать prices.json по пути {}", file, e);
        }
    }

    /**
     * Сливает текущий пользовательский prices.json со встроенным файлом по умолчанию,
     * добавляя все недостающие записи из встроенного файла.
     */
    private void mergeNewDefaultsFromBundledDefault() {
        JsonObject defaults = readBundledDefaultJson(); // Встроенный эталон
        if (defaults == null) {
            LOGGER.warn("[EconomyCraft] Встроенные настройки по умолчанию не найдены; пропуск слияния.");
            return;
        }

        JsonObject userRoot;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            userRoot = GSON.fromJson(json, JsonObject.class);
            if (userRoot == null) userRoot = new JsonObject();
        } catch (Exception ex) {
            backupBrokenConfig(); // Создаём резервную копию сломанного конфига
            createFromBundledDefault(); // И создаём новый из встроенного
            return;
        }

        int added = 0; // Количество добавленных записей
        for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
            String key = e.getKey();

            if (IdentifierCompat.tryParse(key) == null) {
                LOGGER.warn("[EconomyCraft] Встроенный файл по умолчанию содержит неверный ключ '{}', пропуск.", key);
                continue;
            }

            if (!userRoot.has(key)) {
                JsonElement value = e.getValue();
                userRoot.add(key, value == null ? null : value.deepCopy());
                added++;
            }
        }

        if (added > 0) {
            try {
                Files.writeString(file, GSON.toJson(userRoot), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                LOGGER.error("[EconomyCraft] Не удалось записать объединённый prices.json по пути {}", file, ex);
            }
        }
    }

    /**
     * Читает встроенный prices.json из ресурсов JAR и возвращает его как JsonObject.
     * Возвращает null, если файл не найден.
     */
    private JsonObject readBundledDefaultJson() {
        try (InputStream in = PriceRegistry.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) return null;

            byte[] bytes = in.readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            return root != null ? root : null;

        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Не удалось прочитать встроенный prices.json по умолчанию из {}", DEFAULT_RESOURCE_PATH, ex);
            return null;
        }
    }

    /**
     * Создаёт резервную копию повреждённого файла конфигурации.
     */
    private void backupBrokenConfig() {
        try {
            if (Files.exists(file)) {
                Path backup = file.resolveSibling("prices.json.broken-" + System.currentTimeMillis());
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warn("[EconomyCraft] Создана резервная копия повреждённого prices.json в {}", backup);
            }
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Не удалось создать резервную копию повреждённого prices.json по пути {}", file, e);
        }
    }

    /**
     * Проверяет, является ли ID виртуальным (зелья, стрелы, зачарованные книги).
     */
    private static boolean isVirtualPriceId(IdentifierCompat.Id id) {
        if (!"minecraft".equals(id.namespace())) return false;

        String p = id.path();

        if (p.equals("potion") || p.equals("splash_potion") || p.equals("lingering_potion") || p.equals("tipped_arrow")) {
            return false;
        }

        // Зелья / Стрелы
        if (p.equals("water_bottle") || p.equals("splash_water_bottle") || p.equals("lingering_water_bottle")) return true;
        if (p.endsWith("_potion")) return true; // awkward_potion, mundane_potion, ...
        if (p.startsWith("potion_of_")) return true;
        if (p.startsWith("splash_potion_of_")) return true;
        if (p.startsWith("lingering_potion_of_")) return true;
        if (p.startsWith("arrow_of_")) return true;

        // Зачарованные книги
        if (p.startsWith("enchanted_book_") && looksLikeEnchantedBookKey(p)) return true;

        return false;
    }

    /**
     * Проверяет, похож ли путь на ключ зачарованной книги (например, enchanted_book_sharpness_5).
     */
    private static boolean looksLikeEnchantedBookKey(String path) {
        String rest = path.substring("enchanted_book_".length());
        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= rest.length() - 1) return false;

        String lvlStr = rest.substring(lastUnderscore + 1);
        try {
            int lvl = Integer.parseInt(lvlStr);
            return lvl > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Возвращает список ключей для поиска цены предмета (учитывает зелья и зачарованные книги).
     */
    private static List<IdentifierCompat.Id> resolvePriceKeys(ItemStack stack) {
        List<IdentifierCompat.Id> out = new ArrayList<>(4);

        IdentifierCompat.Id itemId = IdentifierCompat.wrap(BuiltInRegistries.ITEM.getKey(stack.getItem()));

        // Обработка зелий и стрел
        if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.TIPPED_ARROW)) {
            IdentifierCompat.Id potionId = readPotionId(stack);

            if (potionId != null) {
                out.addAll(buildVirtualPotionKeys(stack, potionId));
            } else {
                out.addAll(buildVirtualPotionKeys(stack, IdentifierCompat.withDefaultNamespace("water")));
            }
        }

        // Обработка зачарованных книг
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (Object2IntMap.Entry<Holder<Enchantment>> e : stored.entrySet()) {
                Holder<Enchantment> holder = e.getKey();
                int level = e.getIntValue();
                if (level <= 0) continue;
                IdentifierCompat.Id enchId = holder.unwrapKey().map(IdentifierCompat::fromResourceKey).orElse(null);
                if (enchId == null) continue;
                String base = "enchanted_book_" + enchId.path() + "_" + level;
                IdentifierCompat.Id key = IdentifierCompat.fromNamespaceAndPath(enchId.namespace(), base);
                out.add(key);

                // Алиасы для проклятий
                if ("binding_curse".equals(enchId.path())) {
                    out.add(IdentifierCompat.fromNamespaceAndPath(enchId.namespace(), "enchanted_book_curse_of_binding_" + level));
                } else if ("vanishing_curse".equals(enchId.path())) {
                    out.add(IdentifierCompat.fromNamespaceAndPath(enchId.namespace(), "enchanted_book_curse_of_vanishing_" + level));
                }
            }
        }

        out.add(itemId);
        return out;
    }

    /**
     * Читает ID зелья из ItemStack.
     */
    private static IdentifierCompat.Id readPotionId(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return null;

        Optional<Holder<Potion>> opt = contents.potion();
        if (opt.isEmpty()) return null;

        Potion potion = opt.get().value();
        return IdentifierCompat.wrap(BuiltInRegistries.POTION.getKey(potion));
    }

    /**
     * Создаёт виртуальные ключи для зелий и стрел разных форм.
     */
    private static List<IdentifierCompat.Id> buildVirtualPotionKeys(ItemStack stack, IdentifierCompat.Id potionId) {
        String potionPath = potionId.path();
        String form;
        if (stack.is(Items.SPLASH_POTION)) form = "splash";
        else if (stack.is(Items.LINGERING_POTION)) form = "lingering";
        else if (stack.is(Items.TIPPED_ARROW)) form = "arrow";
        else form = "potion";

        if (potionPath.equals("water")) {
            String key = switch (form) {
                case "splash" -> "splash_water_bottle";
                case "lingering" -> "lingering_water_bottle";
                case "potion" -> "water_bottle";
                case "arrow" -> "arrow_of_water_1";
                default -> "water_bottle";
            };
            return List.of(IdentifierCompat.withDefaultNamespace(key));
        }

        if (potionPath.equals("awkward") || potionPath.equals("mundane") || potionPath.equals("thick")) {
            String key = switch (form) {
                case "potion" -> potionPath + "_potion";
                case "splash" -> potionPath + "_splash_potion";
                case "lingering" -> potionPath + "_lingering_potion";
                case "arrow" -> "arrow_of_" + potionPath + "_1";
                default -> potionPath + "_potion";
            };
            return List.of(IdentifierCompat.withDefaultNamespace(key));
        }

        String effect = potionPath;
        String suffix = "_1";
        if (effect.startsWith("long_")) {
            effect = effect.substring("long_".length());
            suffix = "_extended";
        } else if (effect.startsWith("strong_")) {
            effect = effect.substring("strong_".length());
            suffix = "_2";
        }

        if (effect.equals("turtle_master")) {
            effect = "the_turtle_master";
        }

        String base = switch (form) {
            case "potion" -> "potion_of_" + effect;
            case "splash" -> "splash_potion_of_" + effect;
            case "lingering" -> "lingering_potion_of_" + effect;
            case "arrow" -> "arrow_of_" + effect;
            default -> "potion_of_" + effect;
        };

        if (suffix.equals("_1")) {
            return List.of(
                    IdentifierCompat.withDefaultNamespace(base + "_1"),
                    IdentifierCompat.withDefaultNamespace(base)
            );
        } else {
            return List.of(IdentifierCompat.withDefaultNamespace(base + suffix));
        }
    }

    // Вспомогательные методы для безопасного чтения JSON
    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isString()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()) {
            try {
                return obj.get(key).getAsLong();
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    /**
     * Запись цены для предмета.
     * @param id       ID предмета
     * @param category категория
     * @param stack    размер стака для продажи/покупки
     * @param unitBuy  цена покупки за единицу
     * @param unitSell цена продажи за единицу
     */
    public record PriceEntry(
            IdentifierCompat.Id id,
            String category,
            int stack,
            long unitBuy,
            long unitSell
    ) { }
}