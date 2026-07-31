package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.HubUi;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.admin.AdminShopUi;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ContainerPreviewUi;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.PermissionCompat;
import com.reazip.economycraft.util.SortMode;
import com.reazip.economycraft.util.TextInputUi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ServerShopUi {
    private static final Component STORED_MSG = Component.literal("Item stored: ")
            .withStyle(ChatFormatting.YELLOW);

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

    public static void openSearch(ServerPlayer player, EconomyManager eco, String query) {
        openSearchResults(player, eco, null, query, 0, SortMode.DEFAULT);
    }

    private static void openSearchResults(ServerPlayer player, EconomyManager eco, @Nullable String category,
                                          String query, int page, SortMode sort) {
        MenuUiSupport.openMenu(player, "Search: " + query, (id, inv) ->
                new ItemMenu(id, inv, eco, category, null, query, page, player, sort));
    }

    private static void openRoot(ServerPlayer player, EconomyManager eco) {
        MenuUiSupport.openMenu(player, "Server Shop", (id, inv) -> new CategoryMenu(id, inv, eco, player));
    }

    private static void openSubcategories(ServerPlayer player, EconomyManager eco, String topCategory) {
        MenuUiSupport.openMenu(player, ShopDisplay.formatCategoryTitle(topCategory), (id, inv) ->
                new SubcategoryMenu(id, inv, eco, topCategory, player));
    }

    private static void openItems(ServerPlayer player, EconomyManager eco, String category) {
        openItems(player, eco, category, null, 0, SortMode.DEFAULT);
    }

    private static void openItems(ServerPlayer player, EconomyManager eco, String category, @Nullable String displayTitle) {
        openItems(player, eco, category, displayTitle, 0, SortMode.DEFAULT);
    }

    private static void openItems(ServerPlayer player, EconomyManager eco, String category,
                                  @Nullable String displayTitle, int page, SortMode sort) {
        String title = ShopDisplay.formatCategoryTitle(displayTitle != null ? displayTitle : category);
        MenuUiSupport.openMenu(player, title, (id, inv) ->
                new ItemMenu(id, inv, eco, category, displayTitle, null, page, player, sort));
    }

    private static class CategoryMenu extends CompatMenu {
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
                if (ShopDisplay.hasItems(prices, cat, true)) categories.add(cat);
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
            int totalPages = MenuUiSupport.totalPages(categories.size(), itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= categories.size()) break;

                String cat = categories.get(idx);
                ItemStack icon = ShopDisplay.createCategoryIcon(cat, cat, prices, viewer, true);
                if (icon.isEmpty()) continue;

                icon.set(DataComponents.CUSTOM_NAME, Component.literal(ShopDisplay.formatCategoryTitle(cat))
                        .withStyle(s -> s.withItalic(false).withColor(ShopDisplay.getCategoryColor(cat)).withBold(true)));
                icon.set(DataComponents.LORE, new ItemLore(List.of(MenuUiSupport.hint("Click to view items"))));
                int slot = ShopDisplay.STAR_SLOT_ORDER.get(i);
                container.setItem(slot, icon);
                slotToIndex[slot] = idx;
            }

            if (page > 0) container.setItem(navRowStart + 3, MenuUiSupport.prevPageButton());
            if (start + itemsPerPage < categories.size()) container.setItem(navRowStart + 5, MenuUiSupport.nextPageButton());

            container.setItem(navRowStart, MenuUiSupport.createBalanceItem(viewer));
            container.setItem(navRowStart + 4, MenuUiSupport.pageIndicator(page, totalPages));

            if (PermissionCompat.isAdmin(viewer)) {
                container.setItem(navRowStart + 1, MenuUiSupport.button(Items.COMMAND_BLOCK, "Edit shop",
                        ChatFormatting.LIGHT_PURPLE,
                        MenuUiSupport.hint("Add, edit or remove items"),
                        MenuUiSupport.hint("Only you (an operator) can see this")));
            }

            container.setItem(navRowStart + 7, MenuUiSupport.button(Items.NETHER_STAR, "Main menu",
                    ChatFormatting.YELLOW, MenuUiSupport.hint("Everything EconomyCraft can do")));

            container.setItem(navRowStart + 8, MenuUiSupport.searchButton());

            MenuUiSupport.fillBackground(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return false;

            if (slot >= 0 && slot < navRowStart) {
                int index = slotToIndex[slot];
                if (index >= 0 && index < categories.size()) {
                    String cat = categories.get(index);
                    if (prices.buySubcategories(cat).isEmpty()) {
                        openItems(viewer, eco, cat);
                    } else {
                        openSubcategories(viewer, eco, cat);
                    }
                    return true;
                }
            }
            if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return true; }
            if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < categories.size()) { page++; updatePage(); return true; }
            if (slot == navRowStart + 1 && PermissionCompat.isAdmin(viewer)) {
                AdminShopUi.open(viewer, eco, AdminShopUi.Origin.SERVER_SHOP);
                return true;
            }
            if (slot == navRowStart + 7) {
                HubUi.open(viewer);
                return true;
            }
            if (slot == navRowStart + 8) {
                TextInputUi.openSearch(viewer, "Search Server Shop", (p, q) -> ServerShopUi.openSearch(p, eco, q));
                return true;
            }
            return false;
        }
    }

    private static class SubcategoryMenu extends CompatMenu {
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
            super(MenuUiSupport.getMenuType(MenuUiSupport.requiredRows(eco.getPrices().buySubcategories(topCategory).size())), id);
            this.eco = eco;
            this.viewer = viewer;
            this.topCategory = topCategory;
            this.prices = eco.getPrices();
            refresh();
            this.rows = MenuUiSupport.requiredRows(subcategories.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.container = new SimpleContainer(rows * 9);
            setupSlots(inv);
            updatePage();
        }

        private void refresh() {
            subcategories = new ArrayList<>();
            for (String sub : prices.buySubcategories(topCategory)) {
                if (ShopDisplay.hasItems(prices, topCategory + "." + sub, true)) {
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
            int totalPages = MenuUiSupport.totalPages(subcategories.size(), itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= subcategories.size()) break;

                String sub = subcategories.get(idx);
                String full = topCategory + "." + sub;
                ItemStack icon = ShopDisplay.createCategoryIcon(sub, full, prices, viewer, true);
                if (icon.isEmpty()) continue;

                icon.set(DataComponents.CUSTOM_NAME, Component.literal(ShopDisplay.formatCategoryTitle(sub))
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.WHITE).withBold(true)));
                icon.set(DataComponents.LORE, new ItemLore(List.of(MenuUiSupport.hint("Click to view items"))));
                container.setItem(i, icon);
            }

            if (page > 0) container.setItem(navRowStart + 3, MenuUiSupport.prevPageButton());
            if (start + itemsPerPage < subcategories.size()) container.setItem(navRowStart + 5, MenuUiSupport.nextPageButton());

            container.setItem(navRowStart + 8, MenuUiSupport.backButton());
            container.setItem(navRowStart, MenuUiSupport.createBalanceItem(viewer));
            container.setItem(navRowStart + 4, MenuUiSupport.pageIndicator(page, totalPages));

            MenuUiSupport.fillBackground(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return false;

            if (slot >= 0 && slot < navRowStart) {
                int index = page * itemsPerPage + slot;
                if (index < subcategories.size()) {
                    String sub = subcategories.get(index);
                    openItems(viewer, eco, topCategory + "." + sub, sub);
                    return true;
                }
            }
            if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return true; }
            if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < subcategories.size()) { page++; updatePage(); return true; }
            if (slot == navRowStart + 8) { openRoot(viewer, eco); return true; }
            return false;
        }
    }

    private static class ItemMenu extends CompatMenu {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayer viewer;
        @Nullable private final String category;
        @Nullable private final String displayTitle;
        @Nullable private final String searchQuery;
        private SortMode sort;
        private List<PriceRegistry.PriceEntry> entries;
        private final SimpleContainer container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private int page;

        ItemMenu(int id, Inventory inv, EconomyManager eco, @Nullable String category, @Nullable String displayTitle,
                 @Nullable String searchQuery, int page, ServerPlayer viewer, SortMode sort) {
            this(id, inv, eco, category, displayTitle, searchQuery, page, viewer, sort,
                    applySort(resolveEntries(eco, category, searchQuery), sort));
        }

        private ItemMenu(int id, Inventory inv, EconomyManager eco, @Nullable String category, @Nullable String displayTitle,
                         @Nullable String searchQuery, int page, ServerPlayer viewer, SortMode sort,
                         List<PriceRegistry.PriceEntry> resolved) {
            super(MenuUiSupport.getMenuType(MenuUiSupport.requiredRows(resolved.size())), id);
            this.eco = eco;
            this.viewer = viewer;
            this.category = category;
            this.displayTitle = displayTitle;
            this.searchQuery = searchQuery;
            this.sort = sort;
            this.prices = eco.getPrices();

            this.entries = resolved;
            this.rows = MenuUiSupport.requiredRows(resolved.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.page = page;
            this.container = new SimpleContainer(rows * 9);
            setupSlots(inv);
            updatePage();
        }

        private static List<PriceRegistry.PriceEntry> resolveEntries(EconomyManager eco, @Nullable String category,
                                                                    @Nullable String searchQuery) {
            return searchQuery != null ? eco.getPrices().search(searchQuery, category) : eco.getPrices().buyableByCategory(category);
        }

        private static List<PriceRegistry.PriceEntry> applySort(List<PriceRegistry.PriceEntry> list, SortMode sort) {
            if (sort == SortMode.DEFAULT) return list;
            List<PriceRegistry.PriceEntry> copy = new ArrayList<>(list);
            Comparator<PriceRegistry.PriceEntry> cmp = Comparator.comparingLong(PriceRegistry.PriceEntry::unitBuy);
            copy.sort(sort == SortMode.PRICE_DESC ? cmp.reversed() : cmp);
            return copy;
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
            int totalPages = MenuUiSupport.totalPages(entries.size(), itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= entries.size()) break;

                PriceRegistry.PriceEntry entry = entries.get(idx);
                ItemStack display = ShopDisplay.createDisplayStack(entry, viewer);
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
                    Long buyStack = ShopDisplay.safeMultiply(entry.unitBuy(), stackSize);
                    Long sellStack = ShopDisplay.safeMultiply(entry.unitSell(), stackSize);
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

            if (page > 0) container.setItem(navRowStart + 3, MenuUiSupport.prevPageButton());
            if (start + itemsPerPage < entries.size()) container.setItem(navRowStart + 5, MenuUiSupport.nextPageButton());

            container.setItem(navRowStart + 8, MenuUiSupport.backButton());

            if (!searching()) {
                container.setItem(navRowStart + 7, MenuUiSupport.searchButton());
            }

            container.setItem(navRowStart, MenuUiSupport.createBalanceItem(viewer));

            container.setItem(navRowStart + 1, MenuUiSupport.button(Items.HOPPER, "Sort",
                    MenuUiSupport.LABEL_PRIMARY_COLOR,
                    Component.literal("Click to cycle").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
                    MenuUiSupport.toggleOption("Default", sort == SortMode.DEFAULT),
                    MenuUiSupport.toggleOption("Lowest Price", sort == SortMode.PRICE_ASC),
                    MenuUiSupport.toggleOption("Highest Price", sort == SortMode.PRICE_DESC)));

            container.setItem(navRowStart + 4, MenuUiSupport.pageIndicator(page, totalPages));

            MenuUiSupport.fillFooter(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (kind == ClickKind.THROW && slot >= 0 && slot < navRowStart) {
                int index = page * itemsPerPage + slot;
                if (index < entries.size()) {
                    ItemStack display = ShopDisplay.createDisplayStack(entries.get(index), viewer);
                    if (MenuUiSupport.hasContainerContents(display)) {
                        ContainerPreviewUi.open(viewer, display, () -> {
                            if (searchQuery != null) {
                                openSearchResults(viewer, eco, category, searchQuery, page, sort);
                            } else {
                                openItems(viewer, eco, category, displayTitle, page, sort);
                            }
                        });
                    }
                }
                return true;
            }
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return false;

            if (slot >= 0 && slot < navRowStart) {
                int index = page * itemsPerPage + slot;
                if (index < entries.size()) {
                    PriceRegistry.PriceEntry entry = entries.get(index);
                    int amount = kind == ClickKind.QUICK_MOVE ? Math.max(1, entry.stack()) : 1;
                    if (dragType == 1) {
                        handleSell(entry, amount);
                    } else {
                        handlePurchase(entry, amount);
                    }
                    return true;
                }
            }
            if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return true; }
            if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < entries.size()) { page++; updatePage(); return true; }
            if (slot == navRowStart + 7 && !searching()) {
                TextInputUi.openSearch(viewer, "Search Server Shop", (p, q) -> ServerShopUi.openSearchResults(p, eco, category, q, 0, sort));
                return true;
            }
            if (slot == navRowStart + 1) {
                sort = sort.next();
                entries = applySort(resolveEntries(eco, category, searchQuery), sort);
                page = 0;
                updatePage();
                return true;
            }
            if (slot == navRowStart + 8) {
                if (category != null && category.contains(".")) {
                    openSubcategories(viewer, eco, category.substring(0, category.indexOf('.')));
                } else {
                    openRoot(viewer, eco);
                }
                return true;
            }
            return false;
        }

        private boolean searching() {
            return searchQuery != null && !searchQuery.isBlank();
        }

        private void handlePurchase(PriceRegistry.PriceEntry entry, int amount) {
            if (entry.unitBuy() <= 0) {
                viewer.sendSystemMessage(Component.literal("This item cannot be purchased.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            ItemStack base = ShopDisplay.createDisplayStack(entry, viewer);
            if (base.isEmpty()) {
                viewer.sendSystemMessage(Component.literal("Item unavailable.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            Long total = ShopDisplay.safeMultiply(entry.unitBuy(), amount);
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

            viewer.sendSystemMessage(Component.literal(
                            "Purchased " + amount + "x " + base.getHoverName().getString() +
                                    " for " + EconomyCraft.formatMoney(total))
                    .withStyle(ChatFormatting.GREEN));

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

            boolean excludeEnchanted = entry.customItem() == null;
            int have = SellService.countMatching(viewer, prices, entry, excludeEnchanted);
            if (have <= 0) {
                viewer.sendSystemMessage(Component.literal("You have none to sell.").withStyle(ChatFormatting.RED));
                return;
            }

            int toSell = Math.min(amount, have);
            Long total = ShopDisplay.safeMultiply(entry.unitSell(), toSell);
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

            ItemStack disp = ShopDisplay.createDisplayStack(entry, viewer);
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
    }
}
