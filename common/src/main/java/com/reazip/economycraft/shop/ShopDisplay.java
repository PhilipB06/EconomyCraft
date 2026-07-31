package com.reazip.economycraft.shop;

import com.mojang.logging.LogUtils;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.util.IdentifierCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopDisplay {
    private ShopDisplay() {}

    private static final Map<String, IdentifierCompat.Id> CATEGORY_ICONS = buildCategoryIcons();
    private static final Set<String> LOGGED_UNAVAILABLE = ConcurrentHashMap.newKeySet();

    public static final List<Integer> STAR_SLOT_ORDER = buildStarSlotOrder(5);

    public static String formatCategoryTitle(String category) {
        if (category == null || category.isBlank()) return "Server Shop";
        String[] parts = category.replace('.', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? category : sb.toString();
    }

    public static ItemStack createDisplayStack(PriceRegistry.PriceEntry entry, ServerPlayer viewer) {
        ItemStack stack = buildDisplayStack(entry, viewer);
        if (stack.isEmpty() && LOGGED_UNAVAILABLE.add(entry.id().asString())) {
            LogUtils.getLogger().warn("[EconomyCraft] Server shop entry '{}' (category '{}') could not be built; it is hidden and shows as unavailable.",
                    entry.id().asString(), entry.category());
        }
        return stack;
    }

    public static ItemStack createCategoryIcon(String displayKey, String categoryKey, PriceRegistry prices,
                                               ServerPlayer viewer, boolean buyableOnly) {
        IdentifierCompat.Id iconId = CATEGORY_ICONS.get(normalizeCategoryKey(displayKey));
        if (iconId == null && categoryKey != null) {
            iconId = CATEGORY_ICONS.get(normalizeCategoryKey(categoryKey));
        }

        if (iconId != null) {
            Optional<?> item = IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, iconId);
            if (item.isPresent()) {
                Item resolved = resolveItemValue(item.get());
                if (resolved != null) {
                    return new ItemStack(resolved);
                }
            }
        }

        List<PriceRegistry.PriceEntry> entries = entriesIn(prices, categoryKey, buyableOnly);
        if (entries.isEmpty() && categoryKey != null && !categoryKey.contains(".")) {
            for (String sub : subcategoriesOf(prices, categoryKey, buyableOnly)) {
                List<PriceRegistry.PriceEntry> subEntries = entriesIn(prices, categoryKey + "." + sub, buyableOnly);
                if (!subEntries.isEmpty()) {
                    entries = subEntries;
                    break;
                }
            }
        }

        if (!entries.isEmpty()) {
            ItemStack display = createDisplayStack(entries.get(0), viewer);
            if (!display.isEmpty()) {
                return display;
            }
        }

        return new ItemStack(Items.BOOK);
    }

    public static boolean hasItems(PriceRegistry prices, String categoryKey, boolean buyableOnly) {
        if (categoryKey == null || categoryKey.isBlank()) return false;
        if (!entriesIn(prices, categoryKey, buyableOnly).isEmpty()) return true;
        if (!categoryKey.contains(".")) {
            for (String sub : subcategoriesOf(prices, categoryKey, buyableOnly)) {
                if (!entriesIn(prices, categoryKey + "." + sub, buyableOnly).isEmpty()) return true;
            }
        }
        return false;
    }

    private static List<PriceRegistry.PriceEntry> entriesIn(PriceRegistry prices, String categoryKey, boolean buyableOnly) {
        return buyableOnly ? prices.buyableByCategory(categoryKey) : prices.allByCategory(categoryKey);
    }

    private static List<String> subcategoriesOf(PriceRegistry prices, String categoryKey, boolean buyableOnly) {
        return buyableOnly ? prices.buySubcategories(categoryKey) : prices.allSubcategories(categoryKey);
    }

    public static Long safeMultiply(long a, int b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    public static ChatFormatting getCategoryColor(String key) {
        return switch (normalizeCategoryKey(key)) {
            case "redstone" -> ChatFormatting.RED;
            case "food" -> ChatFormatting.GOLD;
            case "ores" -> ChatFormatting.WHITE;
            case "blocks" -> ChatFormatting.DARK_GREEN;
            case "stones" -> ChatFormatting.GRAY;
            case "bricks" -> ChatFormatting.DARK_GRAY;
            case "copper" -> ChatFormatting.AQUA;
            case "earth" -> ChatFormatting.GREEN;
            case "sand" -> ChatFormatting.YELLOW;
            case "wood" -> ChatFormatting.DARK_GREEN;
            case "drops" -> ChatFormatting.GRAY;
            case "utility" -> ChatFormatting.LIGHT_PURPLE;
            case "transport" -> ChatFormatting.BLUE;
            case "light" -> ChatFormatting.YELLOW;
            case "plants" -> ChatFormatting.GREEN;
            case "tools" -> ChatFormatting.AQUA;
            case "weapons" -> ChatFormatting.RED;
            case "armor" -> ChatFormatting.BLUE;
            case "enchantments" -> ChatFormatting.LIGHT_PURPLE;
            case "brewing" -> ChatFormatting.DARK_AQUA;
            case "ocean" -> ChatFormatting.DARK_AQUA;
            case "nether" -> ChatFormatting.RED;
            case "end" -> ChatFormatting.LIGHT_PURPLE;
            case "deep dark", "deep_dark" -> ChatFormatting.DARK_BLUE;
            case "archaeology" -> ChatFormatting.GOLD;
            case "ice" -> ChatFormatting.AQUA;
            case "dyed" -> ChatFormatting.BLUE;
            case "discs" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    private static ItemStack buildDisplayStack(PriceRegistry.PriceEntry entry, ServerPlayer viewer) {
        if (entry.customItem() != null) {
            return entry.customItem().copy();
        }
        try {
            IdentifierCompat.Id id = entry.id();

            Optional<?> item = IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, id);
            if (item.isPresent()) {
                Item resolved = resolveItemValue(item.get());
                if (resolved != null && resolved != Items.AIR) {
                    return new ItemStack(resolved);
                }
                return ItemStack.EMPTY;
            }

            String path = id.path();
            if (path.startsWith("enchanted_book_")) {
                return createEnchantedBookStack(id, viewer);
            }

            return createPotionStack(id);
        } catch (RuntimeException ex) {
            LogUtils.getLogger().error("[EconomyCraft] Failed to create display stack for {}", entry.id().asString(), ex);
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack createEnchantedBookStack(IdentifierCompat.Id key, ServerPlayer viewer) {
        String path = key.path();
        String suffix = path.substring("enchanted_book_".length());
        int lastUnderscore = suffix.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= suffix.length() - 1) return ItemStack.EMPTY;

        String enchantPath = suffix.substring(0, lastUnderscore);
        String levelStr = suffix.substring(lastUnderscore + 1);

        int level;
        try {
            level = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            return ItemStack.EMPTY;
        }

        if (enchantPath.equals("curse_of_binding")) enchantPath = "binding_curse";
        else if (enchantPath.equals("curse_of_vanishing")) enchantPath = "vanishing_curse";

        IdentifierCompat.Id enchantId = IdentifierCompat.fromNamespaceAndPath(key.namespace(), enchantPath);
        if (enchantId == null) {
            return ItemStack.EMPTY;
        }
        HolderLookup.RegistryLookup<Enchantment> lookup = viewer.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ResourceKey<Enchantment> resourceKey = IdentifierCompat.createResourceKey(Registries.ENCHANTMENT, enchantId);
        if (resourceKey == null) {
            return ItemStack.EMPTY;
        }
        Optional<Holder.Reference<Enchantment>> holder = lookup.get(resourceKey);
        if (holder.isEmpty()) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder.get(), level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    private static ItemStack createPotionStack(IdentifierCompat.Id key) {
        String path = key.path();
        Item baseItem = Items.POTION;
        String working = path;

        if (path.startsWith("splash_")) {
            baseItem = Items.SPLASH_POTION;
            working = path.substring("splash_".length());
        } else if (path.startsWith("lingering_")) {
            baseItem = Items.LINGERING_POTION;
            working = path.substring("lingering_".length());
        } else if (path.startsWith("arrow_of_")) {
            baseItem = Items.TIPPED_ARROW;
            working = path.substring("arrow_of_".length());
        }

        if (working.startsWith("potion_of_")) {
            working = working.substring("potion_of_".length());
        }

        if (working.endsWith("_splash_potion")) {
            baseItem = Items.SPLASH_POTION;
            working = working.substring(0, working.length() - "_splash_potion".length());
        } else if (working.endsWith("_lingering_potion")) {
            baseItem = Items.LINGERING_POTION;
            working = working.substring(0, working.length() - "_lingering_potion".length());
        } else if (working.endsWith("_potion")) {
            baseItem = Items.POTION;
            working = working.substring(0, working.length() - "_potion".length());
        }

        String potionPath;
        if (working.equals("water_bottle") || working.equals("water")) {
            potionPath = "water";
        } else {
            String effect = working;
            String strength = "";
            if (effect.endsWith("_extended")) {
                effect = effect.substring(0, effect.length() - "_extended".length());
                strength = "long_";
            } else if (effect.endsWith("_2")) {
                effect = effect.substring(0, effect.length() - 2);
                strength = "strong_";
            } else if (effect.endsWith("_1")) {
                effect = effect.substring(0, effect.length() - 2);
            }
            if ("the_turtle_master".equals(effect)) {
                effect = "turtle_master";
            }
            potionPath = strength + effect;
        }

        IdentifierCompat.Id potionId = IdentifierCompat.fromNamespaceAndPath(key.namespace(), potionPath);
        if (potionId == null) {
            return ItemStack.EMPTY;
        }
        Optional<?> potion = IdentifierCompat.registryGetOptional(BuiltInRegistries.POTION, potionId);
        if (potion.isEmpty()) return ItemStack.EMPTY;

        Holder<Potion> holder = resolvePotionHolder(potion.get());
        if (holder == null) {
            return ItemStack.EMPTY;
        }
        return PotionContents.createItemStack(baseItem, holder);
    }

    private static Item resolveItemValue(Object value) {
        if (value instanceof Item resolved) {
            return resolved;
        }
        if (value instanceof Holder<?> holder) {
            Object inner = holder.value();
            if (inner instanceof Item resolved) {
                return resolved;
            }
            return null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Holder<Potion> resolvePotionHolder(Object value) {
        if (value instanceof Potion potion) {
            return BuiltInRegistries.POTION.wrapAsHolder(potion);
        }
        if (value instanceof Holder<?> holder) {
            Object inner = holder.value();
            if (inner instanceof Potion) {
                return (Holder<Potion>) holder;
            }
            return null;
        }
        return null;
    }

    private static Map<String, IdentifierCompat.Id> buildCategoryIcons() {
        Map<String, IdentifierCompat.Id> map = new HashMap<>();
        map.put(normalizeCategoryKey("Redstone"), IdentifierCompat.withDefaultNamespace("redstone"));
        map.put(normalizeCategoryKey("Food"), IdentifierCompat.withDefaultNamespace("cooked_beef"));
        map.put(normalizeCategoryKey("Ores"), IdentifierCompat.withDefaultNamespace("iron_ingot"));
        map.put(normalizeCategoryKey("Blocks"), IdentifierCompat.withDefaultNamespace("grass_block"));
        map.put(normalizeCategoryKey("Stones"), IdentifierCompat.withDefaultNamespace("cobblestone"));
        map.put(normalizeCategoryKey("Bricks"), IdentifierCompat.withDefaultNamespace("bricks"));
        map.put(normalizeCategoryKey("Copper"), IdentifierCompat.withDefaultNamespace("copper_block"));
        map.put(normalizeCategoryKey("Earth"), IdentifierCompat.withDefaultNamespace("dirt"));
        map.put(normalizeCategoryKey("Sand"), IdentifierCompat.withDefaultNamespace("sand"));
        map.put(normalizeCategoryKey("Wood"), IdentifierCompat.withDefaultNamespace("oak_log"));
        map.put(normalizeCategoryKey("Drops"), IdentifierCompat.withDefaultNamespace("gunpowder"));
        map.put(normalizeCategoryKey("Utility"), IdentifierCompat.withDefaultNamespace("totem_of_undying"));
        map.put(normalizeCategoryKey("Transport"), IdentifierCompat.withDefaultNamespace("saddle"));
        map.put(normalizeCategoryKey("Light"), IdentifierCompat.withDefaultNamespace("lantern"));
        map.put(normalizeCategoryKey("Plants"), IdentifierCompat.withDefaultNamespace("wheat"));
        map.put(normalizeCategoryKey("Tools"), IdentifierCompat.withDefaultNamespace("diamond_pickaxe"));
        map.put(normalizeCategoryKey("Weapons"), IdentifierCompat.withDefaultNamespace("diamond_sword"));
        map.put(normalizeCategoryKey("Armor"), IdentifierCompat.withDefaultNamespace("diamond_chestplate"));
        map.put(normalizeCategoryKey("Enchantments"), IdentifierCompat.withDefaultNamespace("enchanted_book"));
        map.put(normalizeCategoryKey("Brewing"), IdentifierCompat.withDefaultNamespace("water_bottle"));
        map.put(normalizeCategoryKey("Ocean"), IdentifierCompat.withDefaultNamespace("tube_coral"));
        map.put(normalizeCategoryKey("Nether"), IdentifierCompat.withDefaultNamespace("netherrack"));
        map.put(normalizeCategoryKey("End"), IdentifierCompat.withDefaultNamespace("end_stone"));
        map.put(normalizeCategoryKey("Deep dark"), IdentifierCompat.withDefaultNamespace("sculk"));
        map.put(normalizeCategoryKey("Archaeology"), IdentifierCompat.withDefaultNamespace("brush"));
        map.put(normalizeCategoryKey("Ice"), IdentifierCompat.withDefaultNamespace("ice"));
        map.put(normalizeCategoryKey("Dyed"), IdentifierCompat.withDefaultNamespace("blue_dye"));
        map.put(normalizeCategoryKey("Discs"), IdentifierCompat.withDefaultNamespace("music_disc_strad"));
        return map;
    }

    private static String normalizeCategoryKey(String key) {
        if (key == null) return "";
        return key.replace('.', ' ').replace('-', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }

    private static List<Integer> buildStarSlotOrder(int height) {
        int width = 9;
        int centerX = (width - 1) / 2;
        int centerY = (height - 1) / 2;
        List<int[]> entries = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                double dx = x - centerX;
                double dy = y - centerY;
                double dist = Math.sqrt(dx * dx + dy * dy);
                entries.add(new int[]{idx, (int) (dist * 1000), y, x});
            }
        }

        entries.sort(Comparator
                .comparingInt((int[] a) -> a[1])
                .thenComparingInt(a -> a[2])
                .thenComparingInt(a -> a[3]));

        List<Integer> order = new ArrayList<>(entries.size());
        for (int[] e : entries) order.add(e[0]);
        return order;
    }
}
