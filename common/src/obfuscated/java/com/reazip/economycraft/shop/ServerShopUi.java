package com.reazip.economycraft.shop;

import com.mojang.logging.LogUtils;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.ContainerPreviewUi;
import com.reazip.economycraft.util.ItemsCompat;
import com.reazip.economycraft.util.MenuUiSupport;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerShopUi {
    private static final Component STORED_MSG = Component.literal("Item stored: ")
            .withStyle(ChatFormatting.YELLOW);
    private static final Map<String, IdentifierCompat.Id> CATEGORY_ICONS = buildCategoryIcons();
    private static final List<Integer> STAR_SLOT_ORDER = buildStarSlotOrder(5);
    private static final Set<String> LOGGED_UNAVAILABLE = ConcurrentHashMap.newKeySet();

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
                return new CategoryMenu(id, inv, eco, player);
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
                return new SubcategoryMenu(id, inv, eco, topCategory, player);
            }
        });
    }

    private static void openItems(ServerPlayer player, EconomyManager eco, String category) {
        openItems(player, eco, category, null, 0);
    }

    private static void openItems(ServerPlayer player, EconomyManager eco, String category, @Nullable String displayTitle) {
        openItems(player, eco, category, displayTitle, 0);
    }

    private static void openItems(ServerPlayer player, EconomyManager eco, String category, @Nullable String displayTitle, int page) {
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
                return new ItemMenu(id, inv, eco, category, displayTitle, page, player);
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
            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 54)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 6 * 18 + 14)) {
                this.addSlot(slot);
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

            ItemStack balance = MenuUiSupport.createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (slot >= 0 && slot < navRowStart) {
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
            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, rows * 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + rows * 18 + 14)) {
                this.addSlot(slot);
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

            ItemStack balance = MenuUiSupport.createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (slot >= 0 && slot < navRowStart) {
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
        @Nullable private final String displayTitle;
        private List<PriceRegistry.PriceEntry> entries = new ArrayList<>();
        private final SimpleContainer container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private int page;

        ItemMenu(int id, Inventory inv, EconomyManager eco, String category, @Nullable String displayTitle, int page, ServerPlayer viewer) {
            super(getMenuType(requiredRows(eco.getPrices().buyableByCategory(category).size())), id);
            this.eco = eco;
            this.viewer = viewer;
            this.category = category;
            this.displayTitle = displayTitle;
            this.prices = eco.getPrices();

            refreshEntries();
            this.rows = requiredRows(entries.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.page = page;
            this.container = new SimpleContainer(rows * 9);
            setupSlots(inv);
            updatePage();
        }

        private void refreshEntries() {
            entries = new ArrayList<>(prices.buyableByCategory(category));
        }

        private void setupSlots(Inventory inv) {
            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, rows * 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + rows * 18 + 14)) {
                this.addSlot(slot);
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
                boolean canSell = entry.unitSell() > 0 && EconomyConfig.get().sellEnabled;
                boolean hasContents = MenuUiSupport.hasContainerContents(display);

                List<Component> lore = new ArrayList<>();
                Component buyLore = MenuUiSupport.labeledValue("Buy", EconomyCraft.formatMoney(entry.unitBuy()), MenuUiSupport.LABEL_PRIMARY_COLOR);
                if (canSell) {
                    Component sellLore = MenuUiSupport.labeledValue("Sell", EconomyCraft.formatMoney(entry.unitSell()), MenuUiSupport.LABEL_PRIMARY_COLOR);
                    lore.add(MenuUiSupport.joinLore(buyLore, sellLore));
                } else {
                    lore.add(buyLore);
                }

                if (stackSize > 1) {
                    Long buyStack = safeMultiply(entry.unitBuy(), stackSize);
                    Long sellStack = safeMultiply(entry.unitSell(), stackSize);
                    if (buyStack != null) {
                        String label = "Stack (" + stackSize + ")";
                        if (canSell && sellStack != null) {
                            lore.add(MenuUiSupport.labeledValues(label, MenuUiSupport.LABEL_PRIMARY_COLOR,
                                    EconomyCraft.formatMoney(buyStack), EconomyCraft.formatMoney(sellStack)));
                        } else {
                            lore.add(MenuUiSupport.labeledValue(label, EconomyCraft.formatMoney(buyStack), MenuUiSupport.LABEL_PRIMARY_COLOR));
                        }
                    }
                }

                lore.add(MenuUiSupport.labeledValue("Left click", "Buy 1x", MenuUiSupport.LABEL_SECONDARY_COLOR));
                if (canSell) {
                    lore.add(MenuUiSupport.labeledValue("Right click", "Sell 1x", MenuUiSupport.LABEL_SECONDARY_COLOR));
                }
                if (stackSize > 1) {
                    lore.add(MenuUiSupport.labeledValue("Shift-click", (canSell ? "Buy/Sell " : "Buy ") + stackSize + "x", MenuUiSupport.LABEL_SECONDARY_COLOR));
                }
                if (hasContents) {
                    lore.add(MenuUiSupport.labeledValue("Ctrl+Q", "Preview contents", MenuUiSupport.LABEL_SECONDARY_COLOR));
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

            ItemStack balance = MenuUiSupport.createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.THROW && slot >= 0 && slot < navRowStart) {
                int index = page * itemsPerPage + slot;
                if (index < entries.size()) {
                    ItemStack display = createDisplayStack(entries.get(index), viewer);
                    if (MenuUiSupport.hasContainerContents(display)) {
                        ContainerPreviewUi.open(viewer, display, () -> openItems(viewer, eco, category, displayTitle, page));
                    }
                }
                return;
            }
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (slot >= 0 && slot < navRowStart) {
                    int index = page * itemsPerPage + slot;
                    if (index < entries.size()) {
                        PriceRegistry.PriceEntry entry = entries.get(index);
                        int amount = (type == ClickType.QUICK_MOVE) ? Math.max(1, entry.stack()) : 1;
                        if (dragType == 1) {
                            handleSell(entry, amount);
                        } else {
                            handlePurchase(entry, amount);
                        }
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

        private void handlePurchase(PriceRegistry.PriceEntry entry, int amount) {
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

        private void handleSell(PriceRegistry.PriceEntry entry, int amount) {
            if (!EconomyConfig.get().sellEnabled || entry.unitSell() <= 0) {
                viewer.sendSystemMessage(Component.literal("This item cannot be sold.").withStyle(ChatFormatting.RED));
                return;
            }

            // Enchanted items are excluded here (they resell at base price and have no confirm step
            // in the GUI); sell those via "/sell", which asks for confirmation. Doesn't apply to a
            // "components" entry - its price was set for this exact item, enchantments included.
            boolean excludeEnchanted = entry.customItem() == null;
            int have = SellService.countMatching(viewer, prices, entry, excludeEnchanted);
            if (have <= 0) {
                viewer.sendSystemMessage(Component.literal("You have none to sell.").withStyle(ChatFormatting.RED));
                return;
            }

            int toSell = Math.min(amount, have);
            Long total = safeMultiply(entry.unitSell(), toSell);
            if (total == null) {
                viewer.sendSystemMessage(Component.literal("Price too large.").withStyle(ChatFormatting.RED));
                return;
            }

            if (EconomyConfig.get().dailySellLimit > 0 && eco.tryRecordDailySell(viewer.getUUID(), total)) {
                long remaining = eco.getDailySellRemaining(viewer.getUUID());
                viewer.sendSystemMessage(Component.literal(remaining <= 0
                        ? "Daily sell limit reached. Try again tomorrow."
                        : "That exceeds your daily sell limit.").withStyle(ChatFormatting.RED));
                return;
            }

            ItemStack disp = createDisplayStack(entry, viewer);
            String name = disp.isEmpty() ? entry.id().path() : disp.getHoverName().getString();
            SellService.removeMatching(viewer, prices, entry, toSell, excludeEnchanted);
            eco.addMoney(viewer.getUUID(), total);

            viewer.sendSystemMessage(Component.literal("Sold " + toSell + "x " + name + " for " + EconomyCraft.formatMoney(total))
                    .withStyle(ChatFormatting.GREEN));
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
                Item resolved = resolveItemValue(item.get());
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
        ItemStack filler = new ItemStack(ItemsCompat.grayStainedGlassPane());
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
        return Math.clamp(contentRows + 1, 2, 6);
    }

    private static ItemStack createDisplayStack(PriceRegistry.PriceEntry entry, ServerPlayer viewer) {
        ItemStack stack = buildDisplayStack(entry, viewer);
        // Log each unbuildable entry once so bad price ids are diagnosable without letting a
        // spam-clicker flood the log.
        if (stack.isEmpty() && LOGGED_UNAVAILABLE.add(entry.id().asString())) {
            LogUtils.getLogger().warn("[EconomyCraft] Server shop entry '{}' (category '{}') could not be built; it is hidden and shows as unavailable.",
                    entry.id().asString(), entry.category());
        }
        return stack;
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

        // Strip a "potion_of_" infix left after the form prefix (e.g. "splash_potion_of_healing_1"),
        // which also covers the plain "potion_of_x" form.
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

    private static Long safeMultiply(long a, int b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return null;
        }
    }
}
