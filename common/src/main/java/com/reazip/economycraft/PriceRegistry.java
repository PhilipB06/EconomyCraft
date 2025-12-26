package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
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
    private static final String DEFAULT_RESOURCE_PATH = "/assets/economycraft/prices.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private final Path file;
    private final Map<ResourceLocation, PriceEntry> prices = new HashMap<>();

    public PriceRegistry(MinecraftServer server) {
        Path dir = server.getFile("config/economycraft");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Could not create config directory: {}", dir, e);
        }

        this.file = dir.resolve("prices.json");

        if (Files.notExists(this.file)) {
            createFromBundledDefault();
        } else {
            mergeNewDefaultsFromBundledDefault();
        }

        reload();
    }

    public void reload() {
        this.prices.clear();

        if (Files.notExists(file)) {
            LOGGER.warn("[EconomyCraft] prices.json not found at {} (prices map will be empty).", file);
            return;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                LOGGER.error("[EconomyCraft] prices.json is empty or invalid JSON: {}", file);
                return;
            }

            int missingItemCount = 0;
            final int missingItemExamplesLimit = 100;
            List<String> missingItemExamples = new ArrayList<>(missingItemExamplesLimit);

            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String key = e.getKey();
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id == null) {
                    LOGGER.warn("[EconomyCraft] Invalid item id in prices.json: {}", key);
                    continue;
                }

                boolean isRealItem = BuiltInRegistries.ITEM.containsKey(id);
                boolean isVirtual = isVirtualPriceId(id);

                if (!isRealItem && !isVirtual) {
                    missingItemCount++;
                    if (missingItemExamples.size() < missingItemExamplesLimit) {
                        missingItemExamples.add(key);
                    }
                    continue;
                }

                JsonElement el = e.getValue();
                if (el == null || !el.isJsonObject()) {
                    LOGGER.warn("[EconomyCraft] Invalid entry for {} (expected object).", key);
                    continue;
                }

                JsonObject obj = el.getAsJsonObject();

                String name = getString(obj, "name", id.getPath());
                String category = getString(obj, "category", "misc");

                int stack = getInt(obj, "stack", 1);
                long unitBuy = getLong(obj, "unit_buy", 0L);
                long unitSell = getLong(obj, "unit_sell", 0L);
                long stackBuy = getLong(obj, "stack_buy", 0L);
                long stackSell = getLong(obj, "stack_sell", 0L);

                PriceEntry entry = new PriceEntry(id, name, category, stack, unitBuy, unitSell, stackBuy, stackSell);
                prices.put(id, entry);
            }

            if (missingItemCount > 0) {
                if (missingItemExamples.isEmpty()) {
                    LOGGER.warn("[EconomyCraft] Skipped {} price entries for items not present in this server version.", missingItemCount);
                } else {
                    LOGGER.warn(
                            "[EconomyCraft] Skipped {} price entries for items not present in this server version. Examples: {}{}",
                            missingItemCount,
                            String.join(", ", missingItemExamples),
                            (missingItemCount > missingItemExamples.size() ? ", ..." : "")
                    );
                }
            }

            LOGGER.info("[EconomyCraft] Loaded {} price entries from {}", prices.size(), file);

        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Failed to load prices.json from {}", file, ex);
        }
    }

    public PriceEntry get(ResourceLocation id) {
        return prices.get(id);
    }

    public PriceEntry get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        for (ResourceLocation key : resolvePriceKeys(stack)) {
            PriceEntry p = prices.get(key);
            if (p != null) return p;
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

    public Long getStackBuy(ItemStack stack) {
        PriceEntry p = get(stack);
        return (p != null && p.stackBuy() > 0) ? p.stackBuy() : null;
    }

    public Long getStackSell(ItemStack stack) {
        PriceEntry p = get(stack);
        return (p != null && p.stackSell() > 0) ? p.stackSell() : null;
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

    public boolean canBuyStack(ItemStack stack) {
        PriceEntry p = get(stack);
        return p != null && p.stackBuy() > 0 && p.stack() > 0;
    }

    public boolean canSellStack(ItemStack stack) {
        PriceEntry p = get(stack);
        return p != null && p.stackSell() > 0 && p.stack() > 0;
    }

    public Collection<PriceEntry> all() {
        return Collections.unmodifiableCollection(prices.values());
    }

    public Set<String> categories() {
        Set<String> out = new TreeSet<>();
        for (PriceEntry p : prices.values()) out.add(p.category());
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

        out.sort(Comparator.comparing(PriceEntry::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private void createFromBundledDefault() {
        try (InputStream in = PriceRegistry.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.error("[EconomyCraft] Default prices resource not found at {}. Creating empty {}",
                        DEFAULT_RESOURCE_PATH, file);
                Files.writeString(file, "{}", StandardCharsets.UTF_8);
                return;
            }

            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[EconomyCraft] Created {} from bundled default {}", file, DEFAULT_RESOURCE_PATH);

        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Failed to create prices.json at {}", file, e);
        }
    }

    private void mergeNewDefaultsFromBundledDefault() {
        JsonObject defaults = readBundledDefaultJson();
        if (defaults == null) {
            LOGGER.warn("[EconomyCraft] No bundled defaults found; skipping merge.");
            return;
        }

        JsonObject userRoot;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            userRoot = GSON.fromJson(json, JsonObject.class);
            if (userRoot == null) userRoot = new JsonObject();
        } catch (Exception ex) {
            backupBrokenConfig();
            createFromBundledDefault();
            return;
        }

        int added = 0;
        for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
            String key = e.getKey();

            if (ResourceLocation.tryParse(key) == null) {
                LOGGER.warn("[EconomyCraft] Bundled default contains invalid key '{}', skipping.", key);
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
                LOGGER.info("[EconomyCraft] Added {} new price entries to {}", added, file);
            } catch (IOException ex) {
                LOGGER.error("[EconomyCraft] Failed to write merged prices.json at {}", file, ex);
            }
        } else {
            LOGGER.info("[EconomyCraft] prices.json up-to-date (no new entries).");
        }
    }

    private JsonObject readBundledDefaultJson() {
        try (InputStream in = PriceRegistry.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) return null;

            byte[] bytes = in.readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            return root != null ? root : null;

        } catch (Exception ex) {
            LOGGER.error("[EconomyCraft] Failed to read bundled default prices.json from {}", DEFAULT_RESOURCE_PATH, ex);
            return null;
        }
    }

    private void backupBrokenConfig() {
        try {
            if (Files.exists(file)) {
                Path backup = file.resolveSibling("prices.json.broken-" + System.currentTimeMillis());
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warn("[EconomyCraft] Backed up broken prices.json to {}", backup);
            }
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Failed to backup broken prices.json at {}", file, e);
        }
    }

    private static boolean isVirtualPriceId(ResourceLocation id) {
        if (!"minecraft".equals(id.getNamespace())) return false;

        String p = id.getPath();

        // echte Items (NICHT virtuell)
        if (p.equals("potion") || p.equals("splash_potion") || p.equals("lingering_potion") || p.equals("tipped_arrow")) {
            return false;
        }

        // Potions / Arrows (dein Schema)
        if (p.equals("water_bottle") || p.equals("splash_water_bottle") || p.equals("lingering_water_bottle")) return true;
        if (p.endsWith("_potion")) return true;                    // awkward_potion, mundane_potion, ...
        if (p.startsWith("potion_of_")) return true;
        if (p.startsWith("splash_potion_of_")) return true;
        if (p.startsWith("lingering_potion_of_")) return true;
        if (p.startsWith("arrow_of_")) return true;

        // Enchanted books (dein Schema: enchanted_book_<enchant>_<level>)
        if (p.startsWith("enchanted_book_") && looksLikeEnchantedBookKey(p)) return true;

        return false;
    }

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

    private static List<ResourceLocation> resolvePriceKeys(ItemStack stack) {
        List<ResourceLocation> out = new ArrayList<>(4);

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.TIPPED_ARROW)) {
            ResourceLocation potionId = readPotionId(stack);

            if (potionId != null) {
                out.addAll(buildVirtualPotionKeys(stack, potionId));
            } else {
                out.addAll(buildVirtualPotionKeys(stack, ResourceLocation.withDefaultNamespace("water")));
            }
        }

        out.add(itemId);
        return out;
    }

    private static ResourceLocation readPotionId(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return null;

        Optional<Holder<Potion>> opt = contents.potion();
        if (opt.isEmpty()) return null;

        Potion potion = opt.get().value();
        return BuiltInRegistries.POTION.getKey(potion);
    }

    private static List<ResourceLocation> buildVirtualPotionKeys(ItemStack stack, ResourceLocation potionId) {
        String potionPath = potionId.getPath();
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
            return List.of(ResourceLocation.withDefaultNamespace(key));
        }

        if (potionPath.equals("awkward") || potionPath.equals("mundane") || potionPath.equals("thick")) {
            String key = switch (form) {
                case "potion" -> potionPath + "_potion";
                case "splash" -> potionPath + "_splash_potion";
                case "lingering" -> potionPath + "_lingering_potion";
                case "arrow" -> "arrow_of_" + potionPath + "_1";
                default -> potionPath + "_potion";
            };
            return List.of(ResourceLocation.withDefaultNamespace(key));
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
                    ResourceLocation.withDefaultNamespace(base + "_1"),
                    ResourceLocation.withDefaultNamespace(base)
            );
        } else {
            return List.of(ResourceLocation.withDefaultNamespace(base + suffix));
        }
    }

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

    public record PriceEntry(
            ResourceLocation id,
            String name,
            String category,
            int stack,
            long unitBuy,
            long unitSell,
            long stackBuy,
            long stackSell
    ) {
        public boolean hasAnyBuy() {
            return unitBuy > 0 || stackBuy > 0;
        }

        public boolean hasAnySell() {
            return unitSell > 0 || stackSell > 0;
        }
    }
}
