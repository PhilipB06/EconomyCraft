package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ProfileComponentCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import com.reazip.economycraft.util.IdentifierCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ServerShopUi {
    private static final Component STORED_MSG = Component.literal("Item stored: ")
            .withStyle(ChatFormatting.YELLOW);
    private static final Map<String, IdentifierCompat.Id> CATEGORY_ICONS = buildCategoryIcons();
    private static final List<Integer> STAR_SLOT_ORDER = buildStarSlotOrder(5);
    private static final ChatFormatting LABEL_PRIMARY_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting LABEL_SECONDARY_COLOR = ChatFormatting.AQUA;
    private static final ChatFormatting VALUE_COLOR = ChatFormatting.DARK_PURPLE;
    private static final ChatFormatting BALANCE_NAME_COLOR = ChatFormatting.YELLOW;
    private static final ChatFormatting BALANCE_LABEL_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting BALANCE_VALUE_COLOR = ChatFormatting.DARK_PURPLE;

    private ServerShopUi() {}

    public static void open(ServerPlayer player, EconomyManager eco) {
        open(player, eco, null);
    }

    public static void open(ServerPlayer player, EconomyManager eco, @Nullable String category) {
        if (category == null || category.isBlank()) {
            openRoot(player, eco);
            return;
        }

        PriceRegistry prices = eco.getPrices();
        String cat = category.trim();
        if (cat.contains(".")) {
            openItems(player, eco, cat);
            return;
        }

        List<String> subs = prices.buySubcategories(cat);
        if (!subs.isEmpty()) {
            openSubcategories(player, eco, cat);
            return;
        }

        openItems(player, eco, cat);
    }

    private static void openRoot(ServerPlayer player, EconomyManager eco) {
        Component title = Component.literal("Server Shop");
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new CategoryMenu(id, inv, eco, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static void openSubcategories(ServerPlayer player, EconomyManager eco, String topCategory) {
        Component title = Component.literal(formatCategoryTitle(topCategory));

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new SubcategoryMenu(id, inv, eco, topCategory, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static void openItems(ServerPlayer player, EconomyManager eco, String category) {
        openItems(player, eco, category, null);
    }

    private static void openItems(ServerPlayer player, EconomyManager eco, String category, @Nullable String displayTitle) {
        Component title;
        if (displayTitle != null) {
            title = Component.literal(formatCategoryTitle(displayTitle));
        } else {
            title = Component.literal(formatCategoryTitle(category));
        }

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new ItemMenu(id, inv, eco, category, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static String formatCategoryTitle(String category) {
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

    private static class CategoryMenu extends AbstractContainerMenu {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayer viewer;
        private List<String> categories = new ArrayList<>();
        private final SimpleContainer container;
        private final int itemsPerPage = 45;
        private final int navRowStart = 45;
        private final int[] slotToIndex = new int[54];
        private int page;

        CategoryMenu(int id, Inventory inv, EconomyManager eco, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.eco = eco;
            this.viewer = viewer;
            this.prices = eco.getPrices();

            refreshCategories();
            this.container = new SimpleContainer(54);
            setupSlots(inv);
            updatePage();
        }

        private void refreshCategories() {
            categories = new ArrayList<>();
            for (String cat : prices.buyTopCategories()) {
                if (hasItems(prices, cat)) categories.add(cat);
            }
        }

        private void setupSlots(Inventory inv) {
            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player player) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
            int y = 18 + 6 * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            container.clearContent();
            java.util.Arrays.fill(slotToIndex, -1);
            int start = page * itemsPerPage;
            int totalPages = (int) Math.ceil(categories.size() / (double) itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= categories.size()) break;

                String cat = categories.get(idx);
                ItemStack icon = createCategoryIcon(cat, cat, prices, viewer);
                if (icon.isEmpty()) continue;

                icon.set(DataComponents.CUSTOM_NAME, Component.literal(formatCategoryTitle(cat)).withStyle(s -> s.withItalic(false).withColor(getCategoryColor(cat)).withBold(true)));
                icon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Click to view items").withStyle(s -> s.withItalic(false)))));
                int slot = STAR_SLOT_ORDER.get(i);
                container.setItem(slot, icon);
                slotToIndex[slot] = idx;
            }

            fillEmptyWithPanes(container, itemsPerPage);

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }

            if (start + itemsPerPage < categories.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }

            ItemStack balance = createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (slot < navRowStart) {
                    int index = slotToIndex[slot];
                    if (index >= 0 && index < categories.size()) {
                        String cat = categories.get(index);
                        List<String> subs = prices.buySubcategories(cat);
                        if (subs.isEmpty()) {
                            openItems(viewer, eco, cat);
                        } else {
                            openSubcategories(viewer, eco, cat);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < categories.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static class SubcategoryMenu extends AbstractContainerMenu {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayer viewer;
        private final String topCategory;
        private List<String> subcategories = new ArrayList<>();
        private final SimpleContainer container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private int page;

        SubcategoryMenu(int id, Inventory inv, EconomyManager eco, String topCategory, ServerPlayer viewer) {
            super(getMenuType(requiredRows(eco.getPrices().buySubcategories(topCategory).size())), id);
            this.eco = eco;
            this.viewer = viewer;
            this.topCategory = topCategory;
            this.prices = eco.getPrices();
            refresh();
            this.rows = requiredRows(subcategories.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.container = new SimpleContainer(rows * 9);
            setupSlots(inv);
            updatePage();
        }

        private void refresh() {
            subcategories = new ArrayList<>();
            for (String sub : prices.buySubcategories(topCategory)) {
                if (hasItems(prices, topCategory + "." + sub)) {
                    subcategories.add(sub);
                }
            }
        }

        private void setupSlots(Inventory inv) {
            for (int i = 0; i < rows * 9; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player player) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
            int y = 18 + rows * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            container.clearContent();
            int start = page * itemsPerPage;
            int totalPages = (int) Math.ceil(subcategories.size() / (double) itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= subcategories.size()) break;

                String sub = subcategories.get(idx);
                String full = topCategory + "." + sub;
                ItemStack icon = createCategoryIcon(sub, full, prices, viewer);
                if (icon.isEmpty()) continue;

                icon.set(DataComponents.CUSTOM_NAME, Component.literal(formatCategoryTitle(sub)).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.WHITE).withBold(true)));
                icon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Click to view items").withStyle(s -> s.withItalic(false)))));
                container.setItem(i, icon);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }

            if (start + itemsPerPage < subcategories.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }

            ItemStack back = new ItemStack(Items.BARRIER);
            back.set(DataComponents.CUSTOM_NAME, Component.literal("Back").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_RED).withBold(true)));
            container.setItem(navRowStart + 8, back);

            ItemStack balance = createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (slot < navRowStart) {
                    int index = page * itemsPerPage + slot;
                    if (index < subcategories.size()) {
                        String sub = subcategories.get(index);
                        openItems(viewer, eco, topCategory + "." + sub, sub);
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < subcategories.size()) { page++; updatePage(); return; }
                if (slot == navRowStart + 8) { openRoot(viewer, eco); return; }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static class ItemMenu extends AbstractContainerMenu {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayer viewer;
        private final String category;
        private List<PriceRegistry.PriceEntry> entries = new ArrayList<>();
        private final SimpleContainer container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private int page;

        ItemMenu(int id, Inventory inv, EconomyManager eco, String category, ServerPlayer viewer) {
            super(getMenuType(requiredRows(eco.getPrices().buyableByCategory(category).size())), id);
            this.eco = eco;
            this.viewer = viewer;
            this.category = category;
            this.prices = eco.getPrices();

            refreshEntries();
            this.rows = requiredRows(entries.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.container = new SimpleContainer(rows * 9);
            setupSlots(inv);
            updatePage();
        }

        private void refreshEntries() {
            entries = new ArrayList<>(prices.buyableByCategory(category));
        }

        private void setupSlots(Inventory inv) {
            for (int i = 0; i < rows * 9; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player player) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
            int y = 18 + rows * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            container.clearContent();
            int start = page * itemsPerPage;
            int totalPages = (int) Math.ceil(entries.size() / (double) itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= entries.size()) break;

                PriceRegistry.PriceEntry entry = entries.get(idx);
                ItemStack display = createDisplayStack(entry, viewer);
                if (display.isEmpty()) continue;

                int stackSize = Math.max(1, entry.stack());
                List<Component> lore = new ArrayList<>();
                lore.add(labeledValue("Buy", EconomyCraft.formatMoney(entry.unitBuy()), LABEL_PRIMARY_COLOR));

                Long stackPrice = safeMultiply(entry.unitBuy(), stackSize);
                if (stackSize > 1 && stackPrice != null) {
                    lore.add(labeledValue("Stack (" + stackSize + ")", EconomyCraft.formatMoney(stackPrice), LABEL_PRIMARY_COLOR));
                }

                lore.add(labeledValue("Left click", "Buy 1", LABEL_SECONDARY_COLOR));
                if (stackSize > 1) {
                    lore.add(labeledValue("Shift-click", "Buy " + stackSize, LABEL_SECONDARY_COLOR));
                }

                display.set(DataComponents.LORE, new ItemLore(lore));
                display.setCount(1);
                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }

            if (start + itemsPerPage < entries.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }

            ItemStack back = new ItemStack(Items.BARRIER);
            back.set(DataComponents.CUSTOM_NAME, Component.literal("Back").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_RED).withBold(true)));
            container.setItem(navRowStart + 8, back);

            ItemStack balance = createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (slot < navRowStart) {
                    int index = page * itemsPerPage + slot;
                    if (index < entries.size()) {
                        handlePurchase(entries.get(index), type);
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < entries.size()) { page++; updatePage(); return; }
                if (slot == navRowStart + 8) {
                    if (category.contains(".")) {
                        String topCategory = category.substring(0, category.indexOf('.'));
                        openSubcategories(viewer, eco, topCategory);
                    } else {
                        openRoot(viewer, eco);
                    }
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        private void handlePurchase(PriceRegistry.PriceEntry entry, ClickType clickType) {
            if (entry.unitBuy() <= 0) {
                viewer.sendSystemMessage(Component.literal("This item cannot be purchased.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            ItemStack base = createDisplayStack(entry, viewer);
            if (base.isEmpty()) {
                viewer.sendSystemMessage(Component.literal("Item unavailable.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            int stackSize = Math.max(1, entry.stack());
            int amount = clickType == ClickType.QUICK_MOVE ? stackSize : 1;

            Long total = safeMultiply(entry.unitBuy(), amount);
            if (total == null) {
                viewer.sendSystemMessage(Component.literal("Price too large.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            long balance = eco.getBalance(viewer.getUUID(), true);
            if (balance < total) {
                viewer.sendSystemMessage(Component.literal("Not enough balance.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            if (!eco.removeMoney(viewer.getUUID(), total)) {
                viewer.sendSystemMessage(Component.literal("Not enough balance.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            boolean stored = giveToPlayer(base, amount);

            Component success = Component.literal(
                    "Purchased " + amount + "x " + base.getHoverName().getString() +
                            " for " + EconomyCraft.formatMoney(total))
                    .withStyle(ChatFormatting.GREEN);
            viewer.sendSystemMessage(success);

            if (stored) {
                sendStoredMessage(viewer);
            }

            updatePage();
        }

        private boolean giveToPlayer(ItemStack base, int amount) {
            int remaining = amount;
            boolean stored = false;
            while (remaining > 0) {
                int give = Math.min(base.getMaxStackSize(), remaining);
                ItemStack stack = base.copyWithCount(give);
                if (!viewer.getInventory().add(stack)) {
                    eco.getShop().addDelivery(viewer.getUUID(), stack);
                    stored = true;
                }
                remaining -= give;
            }
            return stored;
        }

        private void sendStoredMessage(ServerPlayer player) {
            ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
            if (ev != null) {
                player.sendSystemMessage(STORED_MSG.copy()
                        .append(Component.literal("[Claim]")
                                .withStyle(s -> s.withUnderlined(true)
                                        .withColor(ChatFormatting.GREEN)
                                        .withClickEvent(ev))));
            } else {
                ChatCompat.sendRunCommandTellraw(player, "Item stored: ", "[Claim]", "/eco orders claim");
            }
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static ItemStack createCategoryIcon(String displayKey, String categoryKey, PriceRegistry prices, ServerPlayer viewer) {
        IdentifierCompat.Id iconId = CATEGORY_ICONS.get(normalizeCategoryKey(displayKey));
        if (iconId == null && categoryKey != null) {
            iconId = CATEGORY_ICONS.get(normalizeCategoryKey(categoryKey));
        }

        if (iconId != null) {
            Optional<?> item = IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, iconId);
            if (item.isPresent()) {
                Item resolved = resolveItemValue(item.get(), iconId, "category icon");
                if (resolved != null) {
                    return new ItemStack(resolved);
                }
            }
        }

        List<PriceRegistry.PriceEntry> entries = prices.buyableByCategory(categoryKey);
        if (entries.isEmpty() && categoryKey != null && !categoryKey.contains(".")) {
            for (String sub : prices.buySubcategories(categoryKey)) {
                List<PriceRegistry.PriceEntry> subEntries = prices.buyableByCategory(categoryKey + "." + sub);
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
        String cleaned = key.replace('.', ' ').replace('-', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        return cleaned;
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

    private static ChatFormatting getCategoryColor(String key) {
        String norm = normalizeCategoryKey(key);
        return switch (norm) {
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
            case "deep dark" -> ChatFormatting.DARK_BLUE;
            case "deep_dark" -> ChatFormatting.DARK_BLUE;
            case "archaeology" -> ChatFormatting.GOLD;
            case "ice" -> ChatFormatting.AQUA;
            case "dyed" -> ChatFormatting.BLUE;
            case "discs" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    private static boolean hasItems(PriceRegistry prices, String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()) return false;
        if (!prices.buyableByCategory(categoryKey).isEmpty()) return true;
        if (!categoryKey.contains(".")) {
            for (String sub : prices.buySubcategories(categoryKey)) {
                if (!prices.buyableByCategory(categoryKey + "." + sub).isEmpty()) return true;
            }
        }
        return false;
    }

    private static void fillEmptyWithPanes(SimpleContainer container, int limit) {
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < limit && i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, filler.copy());
            }
        }
    }

    private static MenuType<?> getMenuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static int requiredRows(int itemCount) {
        int contentRows = (int) Math.ceil(Math.max(1, itemCount) / 9.0);
        return Math.min(6, Math.max(2, contentRows + 1));
    }

    private static Component labeledValue(String label, String value, ChatFormatting labelColor) {
        return Component.literal(label + ": ")
                .withStyle(s -> s.withItalic(false).withColor(labelColor))
                .append(Component.literal(value)
                        .withStyle(s -> s.withItalic(false).withColor(VALUE_COLOR)));
    }

    private static Component balanceLore(long balance) {
        return Component.literal("Balance: ")
                .withStyle(s -> s.withItalic(false).withColor(BALANCE_LABEL_COLOR))
                .append(Component.literal(EconomyCraft.formatMoney(balance))
                        .withStyle(s -> s.withItalic(false).withColor(BALANCE_VALUE_COLOR)));
    }

    private static ItemStack createBalanceItem(ServerPlayer player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        ProfileComponentCompat.tryResolvedOrUnresolved(player.getGameProfile()).ifPresent(resolvable ->
                head.set(DataComponents.PROFILE, resolvable));
        long balance = EconomyCraft.getManager(player.level().getServer()).getBalance(player.getUUID(), true);
        String name = IdentityCompat.of(player).name();
        head.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false).withColor(BALANCE_NAME_COLOR)));
        head.set(DataComponents.LORE, new ItemLore(List.of(balanceLore(balance))));
        return head;
    }

    private static List<Integer> buildStarSlotOrder() {
        int width = 9;
        int height = 5;
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

    private static ItemStack createDisplayStack(PriceRegistry.PriceEntry entry, ServerPlayer viewer) {
        IdentifierCompat.Id id = entry.id();
        Optional<?> item = IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, id);
        if (item.isPresent()) {
            Item resolved = resolveItemValue(item.get(), id, "display stack");
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
        } else if (path.startsWith("potion_of_")) {
            working = path.substring("potion_of_".length());
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
            if (effect.endsWith("_extended")) {
                effect = effect.substring(0, effect.length() - "_extended".length());
                potionPath = "long_" + effect;
            } else if (effect.endsWith("_2")) {
                effect = effect.substring(0, effect.length() - 2);
                potionPath = "strong_" + effect;
            } else if (effect.endsWith("_1")) {
                effect = effect.substring(0, effect.length() - 2);
                potionPath = effect;
            } else {
                potionPath = effect;
            }
        }

        if ("the_turtle_master".equals(potionPath)) {
            potionPath = "turtle_master";
        }

        IdentifierCompat.Id potionId = IdentifierCompat.fromNamespaceAndPath(key.namespace(), potionPath);
        if (potionId == null) {
            return ItemStack.EMPTY;
        }
        Optional<?> potion = IdentifierCompat.registryGetOptional(BuiltInRegistries.POTION, potionId);
        if (potion.isEmpty()) return ItemStack.EMPTY;

        Holder<Potion> holder = resolvePotionHolder(potion.get(), potionId);
        if (holder == null) {
            return ItemStack.EMPTY;
        }
        return PotionContents.createItemStack(baseItem, holder);
    }

    private static Item resolveItemValue(Object value, IdentifierCompat.Id id, String context) {
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
    private static Holder<Potion> resolvePotionHolder(Object value, IdentifierCompat.Id id) {
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

    private static Long safeMultiply(long a, int b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return null;
        }
    }
}
