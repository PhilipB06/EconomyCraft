package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PriceRegistry {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Path file;
    private final Map<ResourceLocation, ItemPrice> prices = new HashMap<>();

    public PriceRegistry(MinecraftServer server) {
        Path dir = server.getFile("config/economycraft");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        this.file = dir.resolve("prices.json");

        boolean existed = Files.exists(this.file);

        if (existed) {
            load();
        } else {
            generateDefaults();
        }

        if (normalizePrices() || !existed) {
            save();
        }
    }

    public ItemPrice getPrice(ResourceLocation id) {
        return prices.get(id);
    }

    public ItemPrice getPrice(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return prices.get(id);
    }

    public Long getBuy(ResourceLocation id) {
        ItemPrice p = prices.get(id);
        return p != null ? p.buy() : null;
    }

    public Long getSell(ResourceLocation id) {
        ItemPrice p = prices.get(id);
        return p != null ? p.sell() : null;
    }

    public boolean canBuy(Item item) {
        ItemPrice p = getPrice(item);
        return p != null && p.buy() != null;
    }

    public boolean canSell(Item item) {
        ItemPrice p = getPrice(item);
        return p != null && p.sell() != null;
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<ResourceLocation, ItemPrice> e : prices.entrySet()) {
            ItemPrice p = e.getValue();
            JsonObject obj = new JsonObject();
            if (p != null) {
                if (p.buy() != null) {
                    obj.addProperty("buy", p.buy());
                }
                if (p.sell() != null) {
                    obj.addProperty("sell", p.sell());
                }
            }
            root.add(e.getKey().toString(), obj);
        }

        try {
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ignored) {
        }
    }

    private void load() {
        prices.clear();
        try {
            String json = Files.readString(file);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            for (String key : root.keySet()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id == null) continue;

                JsonElement el = root.get(key);
                if (!el.isJsonObject()) continue;

                JsonObject obj = el.getAsJsonObject();
                Long buy = null;
                Long sell = null;

                if (obj.has("buy")
                        && obj.get("buy").isJsonPrimitive()
                        && obj.get("buy").getAsJsonPrimitive().isNumber()) {
                    buy = obj.get("buy").getAsLong();
                }
                if (obj.has("sell")
                        && obj.get("sell").isJsonPrimitive()
                        && obj.get("sell").getAsJsonPrimitive().isNumber()) {
                    sell = obj.get("sell").getAsLong();
                }

                prices.put(id, new ItemPrice(buy, sell));
            }
        } catch (IOException ignored) {
        }
    }

    private void generateDefaults() {
        prices.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            ItemPrice def = createDefaultPrice(id, item);
            prices.put(id, Objects.requireNonNullElseGet(def, () -> new ItemPrice(null, null)));
        }
    }

    private boolean normalizePrices() {
        boolean changed = false;

        Set<ResourceLocation> validIds = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            validIds.add(id);
        }

        if (prices.keySet().retainAll(validIds)) {
            changed = true;
        }

        for (ResourceLocation id : validIds) {
            if (!prices.containsKey(id)) {
                prices.put(id, new ItemPrice(null, null));
                changed = true;
            }
        }

        ResourceLocation airId = BuiltInRegistries.ITEM.getKey(Items.AIR);

        for (Map.Entry<ResourceLocation, ItemPrice> entry : prices.entrySet()) {
            ResourceLocation id = entry.getKey();
            ItemPrice old = entry.getValue();
            Long buy = old != null ? old.buy() : null;
            Long sell = old != null ? old.sell() : null;

            String path = id.getPath();

            if ("enchanted_book".equals(path)) {
                if (buy != null || sell != null) {
                    entry.setValue(new ItemPrice(null, null));
                    changed = true;
                }
                continue;
            }

            if (id.equals(airId)
                    || isAdminOnlyBlock(path)
                    || isSpawnEgg(path)
                    || isMusicDisc(path)
                    || isPatternTrimOrSherd(path)
                    || isPotionItem(path)
                    || isWeaponOrArmor(path)
                    || isTestOrDebugItem(path)) {
                if (buy != null || sell != null) {
                    entry.setValue(new ItemPrice(null, null));
                    changed = true;
                }
                continue;
            }

            ItemPrice forced = getForcedPrice(path);
            if (forced != null) {
                Long fb = forced.buy();
                Long fs = forced.sell();
                if (!Objects.equals(buy, fb) || !Objects.equals(sell, fs)) {
                    entry.setValue(forced);
                    changed = true;
                }
                continue;
            }

            if (buy != null && buy <= 0) {
                buy = null;
            }
            if (sell != null && sell <= 0) {
                sell = null;
            }

            if (buy != null && sell != null && sell > buy) {
                sell = buy;
            }

            if (!Objects.equals(buy, old != null ? old.buy() : null)
                    || !Objects.equals(sell, old != null ? old.sell() : null)) {
                entry.setValue(new ItemPrice(buy, sell));
                changed = true;
            }
        }

        if (applyOreRules()) {
            changed = true;
        }

        if (applyCompressedBlockRules()) {
            changed = true;
        }

        if (applyWoodRules()) {
            changed = true;
        }

        if (applyFoodOverrides()) {
            changed = true;
        }

        if (normalizeRawCookedPairs()) {
            changed = true;
        }

        return changed;
    }

    private ItemPrice createDefaultPrice(ResourceLocation id, Item item) {
        if (item == Items.AIR) return null;

        String path = id.getPath();

        if (isAdminOnlyBlock(path)
                || isSpawnEgg(path)
                || isMusicDisc(path)
                || isPatternTrimOrSherd(path)
                || isPotionItem(path)
                || isWeaponOrArmor(path)
                || isTestOrDebugItem(path)) {
            return null;
        }

        ItemPrice p;

        p = getBossLootPrice(path);
        if (p != null) return p;

        p = getBundleOrHarnessPrice(path);
        if (p != null) return p;

        p = getBookOrPaperPrice(path);
        if (p != null) return p;

        p = getBrewingOrProgressionPrice(path);
        if (p != null) return p;

        p = getDyePrice(path);
        if (p != null) return p;

        p = getRedstoneComponentPrice(path);
        if (p != null) return p;

        p = getResourceBlockPrice(path);
        if (p != null) return p;

        switch (path) {
            case "diamond" -> {
                return new ItemPrice(500L, 250L);
            }
            case "emerald" -> {
                return new ItemPrice(400L, 200L);
            }
            case "ancient_debris" -> {
                return new ItemPrice(1200L, 600L);
            }
            case "netherite_ingot" -> {
                return new ItemPrice(2000L, 1000L);
            }
            case "amethyst_shard" -> {
                return new ItemPrice(150L, 75L);
            }
            case "lapis_lazuli" -> {
                return new ItemPrice(80L, 40L);
            }
            case "quartz" -> {
                return new ItemPrice(40L, 20L);
            }
            case "wheat" -> {
                return new ItemPrice(8L, 4L);
            }
        }

        if (isOre(path) || isRawOre(path)) {
            if (path.equals("nether_quartz_ore")) {
                return new ItemPrice(80L, 40L);
            }
            return new ItemPrice(120L, 60L);
        }

        if (isIngot(path) || isCoalLike(path) || path.equals("redstone")) {
            return new ItemPrice(80L, 40L);
        }

        if (isNugget(path)) {
            return new ItemPrice(10L, 5L);
        }

        if (isFood(item)) {
            return new ItemPrice(8L, 4L);
        }

        if (isLogOrPlank(path)) {
            return new ItemPrice(16L, 8L);
        }

        if (isStairsSlabOrWall(path)) {
            return new ItemPrice(2L, 1L);
        }

        if (item instanceof BlockItem) {
            return new ItemPrice(3L, 1L);
        }

        if (isCommonMobDrop(path)) {
            return new ItemPrice(10L, 5L);
        }

        return null;
    }

    private static final String[][] ORE_TO_DROP = new String[][]{
            {"diamond_ore", "diamond"},
            {"deepslate_diamond_ore", "diamond"},
            {"emerald_ore", "emerald"},
            {"deepslate_emerald_ore", "emerald"},
            {"lapis_ore", "lapis_lazuli"},
            {"deepslate_lapis_ore", "lapis_lazuli"},
            {"redstone_ore", "redstone"},
            {"deepslate_redstone_ore", "redstone"},
            {"nether_quartz_ore", "quartz"},
            {"nether_gold_ore", "gold_nugget"}
    };

    private boolean applyOreRules() {
        boolean changed = false;

        for (String[] pair : ORE_TO_DROP) {
            ResourceLocation oreId = ResourceLocation.withDefaultNamespace(pair[0]);
            ResourceLocation dropId = ResourceLocation.withDefaultNamespace(pair[1]);

            ItemPrice drop = prices.get(dropId);
            if (drop == null || drop.sell() == null || drop.sell() <= 0) {
                continue;
            }

            Long targetSell = drop.sell();
            ItemPrice existing = prices.get(oreId);
            Long existingBuy = existing != null ? existing.buy() : null;
            Long existingSell = existing != null ? existing.sell() : null;

            if (!Objects.equals(existingBuy, null) || !Objects.equals(existingSell, targetSell)) {
                prices.put(oreId, new ItemPrice(null, targetSell));
                changed = true;
            }
        }

        return changed;
    }

    private static final Object[][] COMPRESSED_DEFS = new Object[][]{
            {"diamond_block", "diamond", 9},
            {"emerald_block", "emerald", 9},
            {"coal_block", "coal", 9},
            {"lapis_block", "lapis_lazuli", 9},
            {"redstone_block", "redstone", 9},
            {"iron_block", "iron_ingot", 9},
            {"gold_block", "gold_ingot", 9},
            {"copper_block", "copper_ingot", 9},
            {"raw_iron_block", "raw_iron", 9},
            {"raw_gold_block", "raw_gold", 9},
            {"raw_copper_block", "raw_copper", 9},
            {"bone_block", "bone", 9},
            {"slime_block", "slime_ball", 9},
            {"dried_kelp_block", "dried_kelp", 9},
            {"hay_block", "wheat", 9},
            {"melon", "melon_slice", 9},
            {"honey_block", "honey_bottle", 4},
            {"amethyst_block", "amethyst_shard", 4},
            {"quartz_block", "quartz", 4},
            {"netherite_block", "netherite_ingot", 9}
    };

    private boolean applyCompressedBlockRules() {
        boolean changed = false;

        for (Object[] def : COMPRESSED_DEFS) {
            String blockPath = (String) def[0];
            String basePath = (String) def[1];
            int factor = (Integer) def[2];

            ResourceLocation blockId = ResourceLocation.withDefaultNamespace(blockPath);
            ResourceLocation baseId = ResourceLocation.withDefaultNamespace(basePath);

            ItemPrice base = prices.get(baseId);
            if (base == null || (base.buy() == null && base.sell() == null)) {
                continue;
            }

            Long baseBuy = base.buy();
            Long baseSell = base.sell();

            Long targetBuy = baseBuy != null ? baseBuy * factor : null;
            Long targetSell = baseSell != null ? baseSell * factor : null;

            ItemPrice current = prices.get(blockId);
            Long curBuy = current != null ? current.buy() : null;
            Long curSell = current != null ? current.sell() : null;

            boolean looksGeneric =
                    current == null
                            || (curBuy == null && curSell == null)
                            || (Objects.equals(curBuy, 3L) && Objects.equals(curSell, 1L));

            if (!looksGeneric) {
                continue;
            }

            if (!Objects.equals(curBuy, targetBuy) || !Objects.equals(curSell, targetSell)) {
                prices.put(blockId, new ItemPrice(targetBuy, targetSell));
                changed = true;
            }
        }

        return changed;
    }

    private static final String[] WOOD_TYPES = new String[]{
            "oak",
            "spruce",
            "birch",
            "jungle",
            "acacia",
            "dark_oak",
            "mangrove",
            "cherry",
            "pale_oak"
    };

    private static final String[] NETHER_WOOD_TYPES = new String[]{
            "crimson",
            "warped"
    };

    private boolean applyWoodRules() {
        boolean changed = false;

        for (String type : WOOD_TYPES) {
            if (applyPlankFix(type + "_planks")) {
                changed = true;
            }
        }

        for (String type : NETHER_WOOD_TYPES) {
            if (applyPlankFix(type + "_planks")) {
                changed = true;
            }
        }

        return changed;
    }

    private boolean applyPlankFix(String plankPath) {
        ResourceLocation planksId = ResourceLocation.withDefaultNamespace(plankPath);
        ItemPrice current = prices.get(planksId);
        Long curBuy = current != null ? current.buy() : null;
        Long curSell = current != null ? current.sell() : null;

        boolean isDefaultLogPrice = Objects.equals(curBuy, 16L) && Objects.equals(curSell, 8L);

        if (current == null || (curBuy == null && curSell == null) || isDefaultLogPrice) {
            ItemPrice np = new ItemPrice(3L, 1L);
            prices.put(planksId, np);
            return true;
        }
        return false;
    }

    private boolean applyFoodOverrides() {
        boolean changed = false;

        for (Map.Entry<ResourceLocation, ItemPrice> entry : prices.entrySet()) {
            ResourceLocation id = entry.getKey();
            Optional<Holder.Reference<Item>> holderOpt = BuiltInRegistries.ITEM.get(id);
            if (holderOpt.isEmpty()) continue;
            Item item = holderOpt.get().value();
            if (item == Items.AIR) continue;

            if (!isFood(item)) continue;

            ItemPrice current = entry.getValue();
            Long buy = current != null ? current.buy() : null;
            Long sell = current != null ? current.sell() : null;

            if (buy == null && sell == null) {
                entry.setValue(new ItemPrice(8L, 4L));
                changed = true;
                continue;
            }

            boolean looksBlockLike =
                    (Objects.equals(buy, 3L) && Objects.equals(sell, 1L))
                            || (Objects.equals(buy, 2L) && Objects.equals(sell, 1L));

            if (looksBlockLike) {
                entry.setValue(new ItemPrice(8L, 4L));
                changed = true;
            }
        }

        return changed;
    }

    private boolean isOre(String path) {
        return path.endsWith("_ore");
    }

    private boolean isRawOre(String path) {
        return path.startsWith("raw_");
    }

    private boolean isIngot(String path) {
        return path.endsWith("_ingot");
    }

    private boolean isNugget(String path) {
        return path.endsWith("_nugget");
    }

    private boolean isCoalLike(String path) {
        return path.equals("coal") || path.equals("charcoal");
    }

    private boolean isLogOrPlank(String path) {
        return path.contains("log") || path.contains("wood") || path.contains("planks");
    }

    private boolean isStairsSlabOrWall(String path) {
        return path.endsWith("_stairs") || path.endsWith("_slab") || path.endsWith("_wall");
    }

    private boolean isCommonMobDrop(String path) {
        return COMMON_DROPS.contains(path);
    }

    private boolean isFood(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.get(DataComponents.FOOD) != null;
    }

    private ItemPrice getResourceBlockPrice(String path) {
        return switch (path) {
            case "diamond_block" -> new ItemPrice(9 * 500L, 9 * 250L);
            case "emerald_block" -> new ItemPrice(9 * 400L, 9 * 200L);
            case "netherite_block" -> new ItemPrice(9 * 2000L, 9 * 1000L);

            case "gold_block",
                 "iron_block",
                 "coal_block",
                 "redstone_block",
                 "lapis_block" -> new ItemPrice(9 * 80L, 9 * 40L);

            case "raw_gold_block",
                 "raw_iron_block",
                 "raw_copper_block" -> new ItemPrice(9 * 120L, 9 * 60L);

            case "slime_block" -> new ItemPrice(9 * 10L, 9 * 5L);
            case "bone_block" -> new ItemPrice(9 * 10L, 9 * 5L);
            case "amethyst_block" -> new ItemPrice(4 * 150L, 4 * 75L);
            case "quartz_block" -> new ItemPrice(4 * 40L, 4 * 20L);
            case "dried_kelp_block" -> new ItemPrice(9 * 8L, 9 * 4L);
            case "honey_block" -> new ItemPrice(4 * 8L, 4 * 4L);
            case "hay_block" -> new ItemPrice(9 * 8L, 9 * 4L);
            default -> null;
        };
    }

    private boolean isAdminOnlyBlock(String path) {
        return switch (path) {
            case "bedrock",
                 "barrier",
                 "command_block",
                 "repeating_command_block",
                 "chain_command_block",
                 "structure_block",
                 "structure_void",
                 "end_portal_frame",
                 "spawner",
                 "trial_spawner",
                 "debug_stick",
                 "light",
                 "jigsaw" -> true;
            default -> false;
        };
    }

    private boolean isSpawnEgg(String path) {
        return path.endsWith("_spawn_egg");
    }

    private boolean isMusicDisc(String path) {
        return path.startsWith("music_disc_");
    }

    private boolean isPatternTrimOrSherd(String path) {
        return path.endsWith("_banner_pattern")
                || path.endsWith("_armor_trim_smithing_template")
                || path.endsWith("_pottery_sherd");
    }

    private boolean isPotionItem(String path) {
        return path.equals("potion")
                || path.equals("splash_potion")
                || path.equals("lingering_potion")
                || path.equals("tipped_arrow");
    }

    private boolean isWeaponOrArmor(String path) {
        return path.endsWith("_sword")
                || path.endsWith("_axe")
                || path.endsWith("_pickaxe")
                || path.endsWith("_shovel")
                || path.endsWith("_hoe")
                || path.endsWith("_helmet")
                || path.endsWith("_chestplate")
                || path.endsWith("_leggings")
                || path.endsWith("_boots")
                || path.endsWith("_horse_armor")
                || path.equals("trident")
                || path.equals("bow")
                || path.equals("crossbow")
                || path.equals("shield")
                || path.equals("turtle_helmet")
                || path.equals("mace");
    }

    private boolean isTestOrDebugItem(String path) {
        return path.startsWith("test_")
                || path.equals("test_block")
                || path.equals("test_instance_block")
                || path.equals("dried_ghast");
    }

    private ItemPrice getForcedPrice(String path) {
        ItemPrice p;

        if ("raw_iron".equals(path) || "iron_ore".equals(path) || "deepslate_iron_ore".equals(path)) {
            return new ItemPrice(80L, 40L);
        }
        if ("iron_ingot".equals(path)) {
            return new ItemPrice(120L, 60L);
        }
        if ("obsidian".equals(path)) {
            return new ItemPrice(80L, 40L);
        }

        p = getBossLootPrice(path);
        if (p != null) return p;

        p = getBundleOrHarnessPrice(path);
        if (p != null) return p;

        p = getBookOrPaperPrice(path);
        if (p != null) return p;

        p = getBrewingOrProgressionPrice(path);
        if (p != null) return p;

        p = getDyePrice(path);
        if (p != null) return p;

        p = getRedstoneComponentPrice(path);
        return p;
    }

    private ItemPrice getBossLootPrice(String path) {
        return switch (path) {
            case "totem_of_undying" -> new ItemPrice(1000L, 500L);
            case "nether_star" -> new ItemPrice(4000L, 2000L);
            case "wither_skeleton_skull" -> new ItemPrice(500L, 250L);
            case "dragon_head" -> new ItemPrice(1000L, 500L);
            case "elytra" -> new ItemPrice(5000L, 2500L);
            case "heart_of_the_sea" -> new ItemPrice(600L, 300L);
            case "dragon_egg" -> new ItemPrice(null, null); // nicht handelbar
            case "beacon" -> new ItemPrice(6000L, 3000L);
            case "conduit" -> new ItemPrice(2500L, 1250L);
            default -> null;
        };
    }

    private ItemPrice getBundleOrHarnessPrice(String path) {
        if (path.equals("bundle") || path.endsWith("_bundle")) {
            return new ItemPrice(100L, 50L);
        }
        if (path.endsWith("_harness")) {
            return new ItemPrice(40L, 20L);
        }
        return null;
    }

    private ItemPrice getBookOrPaperPrice(String path) {
        return switch (path) {
            case "paper" -> new ItemPrice(2L, 1L);
            case "book" -> new ItemPrice(16L, 8L);
            case "writable_book" -> new ItemPrice(24L, 12L);
            case "written_book" -> new ItemPrice(24L, 12L);
            case "enchanted_book" -> new ItemPrice(100L, 50L);
            default -> null;
        };
    }

    private ItemPrice getBrewingOrProgressionPrice(String path) {
        return switch (path) {
            case "sugar" -> new ItemPrice(2L, 1L);
            case "nether_wart" -> new ItemPrice(10L, 5L);
            case "blaze_powder" -> new ItemPrice(10L, 5L);
            case "magma_cream" -> new ItemPrice(10L, 5L);
            case "glistering_melon_slice" -> new ItemPrice(20L, 10L);
            case "golden_carrot" -> new ItemPrice(20L, 10L);
            case "rabbit_foot" -> new ItemPrice(14L, 7L);
            case "dragon_breath" -> new ItemPrice(50L, 25L);
            case "ender_eye" -> new ItemPrice(20L, 10L);
            default -> null;
        };
    }

    private ItemPrice getDyePrice(String path) {
        if (path.endsWith("_dye")) {
            return new ItemPrice(2L, 1L);
        }
        if (path.equals("bone_meal")) {
            return new ItemPrice(3L, 1L);
        }
        return null;
    }

    private ItemPrice getRedstoneComponentPrice(String path) {
        return switch (path) {
            case "redstone_torch",
                 "repeater",
                 "comparator",
                 "lever",
                 "target",
                 "daylight_detector",
                 "redstone_lamp",
                 "tripwire_hook",
                 "observer",
                 "piston",
                 "sticky_piston",
                 "dispenser",
                 "dropper",
                 "note_block",
                 "powered_rail",
                 "detector_rail",
                 "activator_rail",
                 "rail",
                 "hopper",
                 "hopper_minecart",
                 "minecart",
                 "tnt_minecart",
                 "furnace_minecart",
                 "command_block_minecart" -> new ItemPrice(10L, 5L);
            default -> null;
        };
    }

    private boolean normalizeRawCookedPairs() {
        boolean changed = false;
        for (Map.Entry<String, String> e : COOKED_TO_RAW.entrySet()) {
            ResourceLocation cookedId = ResourceLocation.withDefaultNamespace(e.getKey());
            ResourceLocation rawId = ResourceLocation.withDefaultNamespace(e.getValue());
            if (!prices.containsKey(cookedId) || !prices.containsKey(rawId)) {
                continue;
            }
            ItemPrice cooked = prices.get(cookedId);
            if (cooked == null || (cooked.buy() == null && cooked.sell() == null)) {
                continue;
            }
            Long cb = cooked.buy();
            Long cs = cooked.sell();
            Long rawBuy = cb != null ? cb / 2 : null;
            Long rawSell = cs != null ? cs / 2 : null;
            ItemPrice rawOld = prices.get(rawId);
            Long ob = rawOld != null ? rawOld.buy() : null;
            Long os = rawOld != null ? rawOld.sell() : null;
            if (!Objects.equals(rawBuy, ob) || !Objects.equals(rawSell, os)) {
                prices.put(rawId, new ItemPrice(rawBuy, rawSell));
                changed = true;
            }
        }
        return changed;
    }

    private static final Set<String> COMMON_DROPS = new HashSet<>();
    private static final Map<String, String> COOKED_TO_RAW = new HashMap<>();

    static {
        COMMON_DROPS.add("rotten_flesh");
        COMMON_DROPS.add("bone");
        COMMON_DROPS.add("string");
        COMMON_DROPS.add("spider_eye");
        COMMON_DROPS.add("gunpowder");
        COMMON_DROPS.add("ender_pearl");
        COMMON_DROPS.add("slime_ball");
        COMMON_DROPS.add("arrow");
        COMMON_DROPS.add("leather");
        COMMON_DROPS.add("feather");
        COMMON_DROPS.add("ink_sac");
        COMMON_DROPS.add("glow_ink_sac");
        COMMON_DROPS.add("blaze_rod");
        COMMON_DROPS.add("ghast_tear");
        COMMON_DROPS.add("phantom_membrane");
        COMMON_DROPS.add("magma_cream");
        COMMON_DROPS.add("rabbit_hide");
        COMMON_DROPS.add("prismarine_shard");
        COMMON_DROPS.add("prismarine_crystals");
        COMMON_DROPS.add("nautilus_shell");
        COMMON_DROPS.add("shulker_shell");

        COOKED_TO_RAW.put("cooked_beef", "beef");
        COOKED_TO_RAW.put("cooked_porkchop", "porkchop");
        COOKED_TO_RAW.put("cooked_mutton", "mutton");
        COOKED_TO_RAW.put("cooked_chicken", "chicken");
        COOKED_TO_RAW.put("cooked_cod", "cod");
        COOKED_TO_RAW.put("cooked_salmon", "salmon");
        COOKED_TO_RAW.put("cooked_rabbit", "rabbit");
        COOKED_TO_RAW.put("baked_potato", "potato");
        COOKED_TO_RAW.put("dried_kelp", "kelp");
    }

    public static final class ItemPrice {
        private final Long buy;
        private final Long sell;

        public ItemPrice(Long buy, Long sell) {
            this.buy = buy;
            this.sell = sell;
        }

        public Long buy() {
            return buy;
        }

        public Long sell() {
            return sell;
        }
    }
}
