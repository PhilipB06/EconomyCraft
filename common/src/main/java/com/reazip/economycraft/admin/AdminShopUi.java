package com.reazip.economycraft.admin;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.shop.ShopDisplay;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ConfirmUi;
import com.reazip.economycraft.shop.ServerShopUi;
import com.reazip.economycraft.util.IdentifierCompat;
import com.reazip.economycraft.util.ItemPickerUi;
import com.reazip.economycraft.util.ItemsCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.NumberInputUi;
import com.reazip.economycraft.util.TextInputUi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class AdminShopUi {
    private AdminShopUi() {}

    private static final int GRID_SLOTS = 45;
    private static final int NAV = GRID_SLOTS;
    private static final long DEFAULT_BUY = 100;
    private static final long DEFAULT_SELL = 25;

    public enum Origin {
        ADMIN,
        SERVER_SHOP
    }

    public record Draft(String key, ItemStack display, @Nullable ItemStack customItem, String category,
                        int stack, long unitBuy, long unitSell) {

        Draft withCategory(String value) {
            return new Draft(key, display, customItem, value, stack, unitBuy, unitSell);
        }

        Draft withStack(int value) {
            return new Draft(key, display, customItem, category, value, unitBuy, unitSell);
        }

        Draft withBuy(long value) {
            return new Draft(key, display, customItem, category, stack, value, unitSell);
        }

        Draft withSell(long value) {
            return new Draft(key, display, customItem, category, stack, unitBuy, value);
        }
    }

    public static void open(ServerPlayer player, EconomyManager eco, Origin origin) {
        openRoot(player, eco, origin, 0);
    }

    private static void openRoot(ServerPlayer player, EconomyManager eco, Origin origin, int page) {
        MenuUiSupport.openMenu(player, "Shop Editor", (id, inv) -> new CategoryMenu(id, inv, player, eco, origin, page));
    }

    private static void exit(ServerPlayer player, EconomyManager eco, Origin origin) {
        if (origin == Origin.SERVER_SHOP) {
            ServerShopUi.open(player, eco);
        } else {
            AdminUi.open(player, eco);
        }
    }

    private static void openList(ServerPlayer player, EconomyManager eco, Origin origin, @Nullable String category,
                                 @Nullable String query, int page) {
        String title;
        if (query != null && category != null) {
            title = ShopDisplay.formatCategoryTitle(category) + ": " + query;
        } else if (query != null) {
            title = "Search: " + query;
        } else if (category != null) {
            title = ShopDisplay.formatCategoryTitle(category);
        } else {
            title = "All Items";
        }
        MenuUiSupport.openMenu(player, title, (id, inv) -> new ListMenu(id, inv, player, eco, origin, category, query, page));
    }

    private static void openEditor(ServerPlayer player, EconomyManager eco, Origin origin, Draft draft,
                                   @Nullable String returnCategory) {
        MenuUiSupport.openMenu(player, "Edit Item",
                (id, inv) -> new EditorMenu(id, inv, player, eco, origin, draft, returnCategory));
    }

    private static void startAdd(ServerPlayer player, EconomyManager eco, Origin origin, @Nullable String category) {
        PriceRegistry prices = eco.getPrices();
        ItemPickerUi.open(player, "Pick an item to add", ItemPickerUi.Source.INVENTORY_AND_ALL, null,
                (picker, choice) -> {
                    ItemStack prototype = choice.prototype().copyWithCount(1);
                    IdentifierCompat.Id id = IdentifierCompat.wrap(BuiltInRegistries.ITEM.getKey(prototype.getItem()));
                    if (id == null) {
                        picker.sendSystemMessage(MenuUiSupport.line("That item has no id and cannot be priced.",
                                ChatFormatting.RED));
                        openList(picker, eco, origin, category, null, 0);
                        return;
                    }

                    boolean custom = !prototype.getComponentsPatch().isEmpty();
                    if (!custom) {
                        PriceRegistry.PriceEntry existing = prices.findByKey(id.asString());
                        if (existing != null) {
                            picker.sendSystemMessage(MenuUiSupport.line(
                                    prototype.getHoverName().getString() + " is already in the shop - opening it.",
                                    ChatFormatting.YELLOW));
                            openEditor(picker, eco, origin, toDraft(existing, picker), category);
                            return;
                        }
                    }

                    String key = custom ? prices.uniqueKeyFor(id, labelFor(prototype)) : id.asString();
                    Draft draft = new Draft(key, prototype, custom ? prototype : null,
                            category != null ? category : "misc",
                            prototype.getMaxStackSize(), DEFAULT_BUY, DEFAULT_SELL);

                    if (!store(picker, eco, draft)) {
                        openList(picker, eco, origin, category, null, 0);
                        return;
                    }
                    picker.sendSystemMessage(Component.literal("Added " + prototype.getHoverName().getString()
                            + " to the server shop.").withStyle(ChatFormatting.GREEN));
                    openEditor(picker, eco, origin, draft, category);
                },
                p -> openList(p, eco, origin, category, null, 0));
    }

    private static boolean store(ServerPlayer player, EconomyManager eco, Draft draft) {
        boolean ok = eco.getPrices().upsert(draft.key(), draft.category(), draft.stack(),
                draft.unitBuy(), draft.unitSell(), draft.customItem());
        if (!ok) {
            player.sendSystemMessage(MenuUiSupport.line("Could not write prices.json. Check the server log.",
                    ChatFormatting.RED));
        }
        return ok;
    }

    private static String labelFor(ItemStack stack) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null && !name.getString().isBlank()) return name.getString();
        return "custom";
    }

    private static Draft toDraft(PriceRegistry.PriceEntry entry, ServerPlayer viewer) {
        ItemStack display = ShopDisplay.createDisplayStack(entry, viewer);
        if (display.isEmpty()) display = new ItemStack(Items.BARRIER);
        return new Draft(entry.key(), display, entry.customItem(), entry.category(),
                Math.max(1, entry.stack()), entry.unitBuy(), entry.unitSell());
    }

    private static List<Component> draftLore(Draft draft) {
        List<Component> lore = new ArrayList<>();
        lore.add(MenuUiSupport.labeledValue("Category", draft.category(), MenuUiSupport.LABEL_PRIMARY_COLOR));
        lore.add(MenuUiSupport.labeledValue("Buy", draft.unitBuy() > 0
                ? EconomyCraft.formatMoney(draft.unitBuy()) : "not for sale", MenuUiSupport.LABEL_PRIMARY_COLOR));
        lore.add(MenuUiSupport.labeledValue("Sell", draft.unitSell() > 0
                ? EconomyCraft.formatMoney(draft.unitSell()) : "not sellable", MenuUiSupport.LABEL_PRIMARY_COLOR));
        lore.add(MenuUiSupport.labeledValue("Bulk amount", String.valueOf(draft.stack()),
                MenuUiSupport.LABEL_PRIMARY_COLOR));
        if (draft.customItem() != null) {
            lore.add(MenuUiSupport.hint("Custom data kept"));
        }
        return lore;
    }

    private static ItemStack categoryIcon(String category, PriceRegistry prices, ServerPlayer viewer) {
        int dot = category.indexOf('.');
        String displayKey = dot > 0 && dot < category.length() - 1 ? category.substring(dot + 1) : category;
        return ShopDisplay.createCategoryIcon(displayKey, category, prices, viewer, false);
    }

    private static class CategoryMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final Origin origin;
        private final SimpleContainer container = new SimpleContainer(54);
        private final List<String> categories;
        private final int[] slotToIndex = new int[54];
        private int page;

        CategoryMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, Origin origin, int page) {
            super(MenuType.GENERIC_9x6, id);
            this.viewer = viewer;
            this.eco = eco;
            this.origin = origin;
            this.categories = collect(eco.getPrices());
            this.page = Math.clamp(page, 0, Math.max(0, MenuUiSupport.totalPages(categories.size(), GRID_SLOTS) - 1));

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 54)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 6 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private static List<String> collect(PriceRegistry prices) {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String top : prices.allTopCategories()) {
                List<String> subs = prices.allSubcategories(top);
                if (subs.isEmpty()) {
                    out.add(top);
                } else {
                    if (!prices.allByCategory(top).isEmpty()) out.add(top);
                    for (String sub : subs) out.add(top + "." + sub);
                }
            }
            List<String> list = new ArrayList<>(out);
            list.sort(String.CASE_INSENSITIVE_ORDER);
            return list;
        }

        private void render() {
            container.clearContent();
            java.util.Arrays.fill(slotToIndex, -1);
            int start = page * GRID_SLOTS;
            int totalPages = MenuUiSupport.totalPages(categories.size(), GRID_SLOTS);
            PriceRegistry prices = eco.getPrices();

            for (int i = 0; i < GRID_SLOTS; i++) {
                int index = start + i;
                if (index >= categories.size()) break;

                String category = categories.get(index);
                ItemStack icon = categoryIcon(category, prices, viewer);
                icon.set(DataComponents.CUSTOM_NAME, Component.literal(ShopDisplay.formatCategoryTitle(category))
                        .withStyle(s -> s.withItalic(false).withBold(true).withColor(ShopDisplay.getCategoryColor(category))));
                icon.set(DataComponents.LORE, new ItemLore(List.of(
                        MenuUiSupport.labeledValue("Id", category, MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Items", String.valueOf(prices.allByCategory(category).size()),
                                MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Click", "Edit these items", MenuUiSupport.LABEL_SECONDARY_COLOR))));
                int slot = ShopDisplay.STAR_SLOT_ORDER.get(i);
                container.setItem(slot, icon);
                slotToIndex[slot] = index;
            }

            if (categories.isEmpty()) {
                container.setItem(22, MenuUiSupport.button(Items.BOOK, "The shop is empty", ChatFormatting.YELLOW,
                        MenuUiSupport.hint("Click \"Add item\" to start.")));
            }

            container.setItem(NAV, MenuUiSupport.backButton());
            container.setItem(NAV + 1, MenuUiSupport.button(Items.WRITABLE_BOOK, "Add item", ChatFormatting.GREEN,
                    MenuUiSupport.hint("Pick any item, then set its price."),
                    MenuUiSupport.hint("Named or enchanted items keep"),
                    MenuUiSupport.hint("their name, enchants and contents.")));

            if (page > 0) container.setItem(NAV + 3, MenuUiSupport.prevPageButton());
            container.setItem(NAV + 4, MenuUiSupport.pageIndicator(page, totalPages));
            if (start + GRID_SLOTS < categories.size()) container.setItem(NAV + 5, MenuUiSupport.nextPageButton());

            container.setItem(NAV + 7, MenuUiSupport.button(Items.CHEST, "All items", ChatFormatting.GOLD,
                    MenuUiSupport.labeledValue("Total", String.valueOf(prices.allEntries().size()),
                            MenuUiSupport.LABEL_PRIMARY_COLOR)));
            container.setItem(NAV + 8, MenuUiSupport.searchButton());

            MenuUiSupport.fillBackground(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 54) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot < GRID_SLOTS) {
                int index = slotToIndex[slot];
                if (index >= 0 && index < categories.size()) {
                    openList(viewer, eco, origin, categories.get(index), null, 0);
                }
                return true;
            }
            if (slot == NAV) {
                exit(viewer, eco, origin);
                return true;
            }
            if (slot == NAV + 1) {
                viewer.closeContainer();
                startAdd(viewer, eco, origin, null);
                return true;
            }
            if (slot == NAV + 3 && page > 0) { page--; render(); return true; }
            if (slot == NAV + 5 && (page + 1) * GRID_SLOTS < categories.size()) { page++; render(); return true; }
            if (slot == NAV + 7) {
                openList(viewer, eco, origin, null, null, 0);
                return true;
            }
            if (slot == NAV + 8) {
                TextInputUi.openSearch(viewer, "Search shop items", (p, q) -> openList(p, eco, origin, null, q, 0));
                return true;
            }
            return true;
        }
    }

    private static class ListMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final Origin origin;
        @Nullable private final String category;
        @Nullable private final String query;
        private final SimpleContainer container = new SimpleContainer(54);
        private final List<PriceRegistry.PriceEntry> entries;
        private int page;

        ListMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, Origin origin,
                 @Nullable String category, @Nullable String query, int page) {
            super(MenuType.GENERIC_9x6, id);
            this.viewer = viewer;
            this.eco = eco;
            this.origin = origin;
            this.category = category;
            this.query = query;
            this.entries = resolve(eco.getPrices(), category, query);
            this.page = Math.clamp(page, 0, Math.max(0, MenuUiSupport.totalPages(entries.size(), GRID_SLOTS) - 1));

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 54)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 6 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private static List<PriceRegistry.PriceEntry> resolve(PriceRegistry prices, @Nullable String category,
                                                              @Nullable String query) {
            if (query != null) return prices.searchAll(query, category);
            if (category != null) return prices.allByCategory(category);
            List<PriceRegistry.PriceEntry> all = new ArrayList<>(prices.allEntries());
            all.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.key(), b.key()));
            return all;
        }

        private void render() {
            container.clearContent();
            int start = page * GRID_SLOTS;
            int totalPages = MenuUiSupport.totalPages(entries.size(), GRID_SLOTS);

            for (int i = 0; i < GRID_SLOTS; i++) {
                int index = start + i;
                if (index >= entries.size()) break;

                PriceRegistry.PriceEntry entry = entries.get(index);
                ItemStack display = ShopDisplay.createDisplayStack(entry, viewer);
                if (display.isEmpty()) display = new ItemStack(Items.BARRIER);
                display.setCount(1);

                List<Component> lore = new ArrayList<>(draftLore(toDraft(entry, viewer)));
                lore.add(MenuUiSupport.labeledValue("Click", "Edit", MenuUiSupport.LABEL_SECONDARY_COLOR));
                display.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(i, display);
            }

            if (entries.isEmpty()) {
                container.setItem(22, MenuUiSupport.button(Items.BOOK, "Nothing here yet", ChatFormatting.YELLOW,
                        MenuUiSupport.hint("Click \"Add item\" below.")));
            }

            container.setItem(NAV, MenuUiSupport.backButton());
            container.setItem(NAV + 1, MenuUiSupport.button(Items.WRITABLE_BOOK, "Add item", ChatFormatting.GREEN,
                    MenuUiSupport.hint(category != null
                            ? "New items land in \"" + category + "\"."
                            : "Pick any item, then set its price.")));

            if (page > 0) container.setItem(NAV + 3, MenuUiSupport.prevPageButton());
            container.setItem(NAV + 4, MenuUiSupport.pageIndicator(page, totalPages));
            if (start + GRID_SLOTS < entries.size()) container.setItem(NAV + 5, MenuUiSupport.nextPageButton());

            container.setItem(NAV + 8, query != null
                    ? MenuUiSupport.clearSearchButton(query)
                    : MenuUiSupport.searchButton());

            MenuUiSupport.fillFooter(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 54) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot < GRID_SLOTS) {
                int index = page * GRID_SLOTS + slot;
                if (index < entries.size()) {
                    openEditor(viewer, eco, origin, toDraft(entries.get(index), viewer), category);
                }
                return true;
            }
            if (slot == NAV) {
                openRoot(viewer, eco, origin, 0);
                return true;
            }
            if (slot == NAV + 1) {
                viewer.closeContainer();
                startAdd(viewer, eco, origin, category);
                return true;
            }
            if (slot == NAV + 3 && page > 0) { page--; render(); return true; }
            if (slot == NAV + 5 && (page + 1) * GRID_SLOTS < entries.size()) { page++; render(); return true; }
            if (slot == NAV + 8) {
                if (query != null) {
                    openList(viewer, eco, origin, category, null, 0);
                } else {
                    String title = category == null
                            ? "Search shop items"
                            : "Search " + ShopDisplay.formatCategoryTitle(category);
                    TextInputUi.openSearch(viewer, title, (p, q) -> openList(p, eco, origin, category, q, 0));
                }
                return true;
            }
            return true;
        }
    }

    private static class EditorMenu extends CompatMenu {
        private static final int ITEM = 4;
        private static final int DELETE = 8;
        private static final int BUY = 10;
        private static final int SELL = 12;
        private static final int STACK = 14;
        private static final int CATEGORY = 16;
        private static final int BACK = 18;

        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final Origin origin;
        private final Draft draft;
        @Nullable private final String returnCategory;
        private final SimpleContainer container = new SimpleContainer(27);

        EditorMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, Origin origin, Draft draft,
                   @Nullable String returnCategory) {
            super(MenuType.GENERIC_9x3, id);
            this.viewer = viewer;
            this.eco = eco;
            this.origin = origin;
            this.draft = draft;
            this.returnCategory = returnCategory;

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 27)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 3 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private void render() {
            container.clearContent();

            ItemStack display = draft.display().copy();
            display.setCount(1);
            List<Component> lore = new ArrayList<>(draftLore(draft));
            lore.add(MenuUiSupport.italicHint(draft.key()));
            display.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(ITEM, display);

            container.setItem(BUY, MenuUiSupport.button(Items.GOLD_INGOT, "Buy Price", ChatFormatting.GOLD,
                    MenuUiSupport.hint("What players pay for one."),
                    MenuUiSupport.labeledValue("Now", draft.unitBuy() > 0
                            ? EconomyCraft.formatMoney(draft.unitBuy()) : "not for sale", MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.hint("Set to 0 to hide it from the shop.")));

            container.setItem(SELL, MenuUiSupport.button(Items.EMERALD, "Sell Price", ChatFormatting.GREEN,
                    MenuUiSupport.hint("What players get for one."),
                    MenuUiSupport.labeledValue("Now", draft.unitSell() > 0
                            ? EconomyCraft.formatMoney(draft.unitSell()) : "not sellable", MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.hint("Set to 0 to stop players selling it.")));

            container.setItem(STACK, MenuUiSupport.button(Items.CHEST, "Bulk Amount", ChatFormatting.AQUA,
                    MenuUiSupport.hint("How many a shift-click buys or sells."),
                    MenuUiSupport.labeledValue("Now", String.valueOf(draft.stack()), MenuUiSupport.LABEL_PRIMARY_COLOR)));

            container.setItem(CATEGORY, MenuUiSupport.button(Items.BOOK, "Category", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Which page of the shop it lives on."),
                    MenuUiSupport.labeledValue("Now", draft.category(), MenuUiSupport.LABEL_PRIMARY_COLOR)));

            container.setItem(DELETE, MenuUiSupport.button(Items.BARRIER, "Delete", ChatFormatting.DARK_RED,
                    MenuUiSupport.hint("Removes it from the shop for good.")));

            container.setItem(BACK, MenuUiSupport.button(ItemsCompat.redStainedGlassPane(), "Back",
                    ChatFormatting.DARK_RED, MenuUiSupport.hint("Changes are already saved.")));

            MenuUiSupport.fillBackground(container);
        }

        private void back(ServerPlayer player) {
            openList(player, eco, origin, returnCategory, null, 0);
        }

        private void update(Draft next) {
            if (store(viewer, eco, next)) {
                openEditor(viewer, eco, origin, next, returnCategory);
            } else {
                back(viewer);
            }
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 27) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            switch (slot) {
                case BUY -> NumberInputUi.openMoney(viewer, "Buy price", draft.display(), "Buy price",
                        draft.unitBuy(), 0, EconomyManager.MAX,
                        (p, value) -> update(draft.withBuy(value)),
                        p -> openEditor(p, eco, origin, draft, returnCategory));
                case SELL -> NumberInputUi.openMoney(viewer, "Sell price", draft.display(), "Sell price",
                        draft.unitSell(), 0, EconomyManager.MAX,
                        (p, value) -> update(draft.withSell(value)),
                        p -> openEditor(p, eco, origin, draft, returnCategory));
                case STACK -> NumberInputUi.openCount(viewer, "Bulk amount", draft.display(), "Bulk amount",
                        draft.stack(), 1, 64,
                        (p, value) -> update(draft.withStack(value.intValue())),
                        p -> openEditor(p, eco, origin, draft, returnCategory));
                case CATEGORY -> MenuUiSupport.openMenu(viewer, "Pick a category",
                        (id, inv) -> new CategoryPickerMenu(id, inv, viewer, eco, origin, draft, returnCategory));
                case DELETE -> confirmDelete(viewer, eco, origin, draft, returnCategory);
                case BACK -> back(viewer);
                default -> {
                }
            }
            return true;
        }
    }

    private static void confirmDelete(ServerPlayer player, EconomyManager eco, Origin origin, Draft draft,
                                      @Nullable String returnCategory) {
        List<Component> lore = new ArrayList<>(draftLore(draft));
        lore.add(MenuUiSupport.line("Players will no longer see this item.", ChatFormatting.RED));

        ConfirmUi.open(player, "Delete this item?", draft.display(), "Delete it", lore,
                p -> {
                    boolean ok = eco.getPrices().delete(draft.key());
                    p.sendSystemMessage(ok
                            ? Component.literal("Removed " + draft.display().getHoverName().getString()
                                    + " from the server shop.").withStyle(ChatFormatting.GREEN)
                            : MenuUiSupport.line("Could not write prices.json. Check the server log.", ChatFormatting.RED));
                    openList(p, eco, origin, returnCategory, null, 0);
                },
                p -> openEditor(p, eco, origin, draft, returnCategory));
    }

    private static class CategoryPickerMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final Origin origin;
        private final Draft draft;
        @Nullable private final String returnCategory;
        private final SimpleContainer container = new SimpleContainer(54);
        private final List<String> categories;
        private int page;

        CategoryPickerMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, Origin origin, Draft draft,
                           @Nullable String returnCategory) {
            super(MenuType.GENERIC_9x6, id);
            this.viewer = viewer;
            this.eco = eco;
            this.origin = origin;
            this.draft = draft;
            this.returnCategory = returnCategory;
            this.categories = CategoryMenu.collect(eco.getPrices());
            this.page = 0;

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 54)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 6 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private void render() {
            container.clearContent();
            int start = page * GRID_SLOTS;
            int totalPages = MenuUiSupport.totalPages(categories.size(), GRID_SLOTS);

            for (int i = 0; i < GRID_SLOTS; i++) {
                int index = start + i;
                if (index >= categories.size()) break;

                String category = categories.get(index);
                boolean current = category.equalsIgnoreCase(draft.category());
                ItemStack icon = categoryIcon(category, eco.getPrices(), viewer);
                icon.set(DataComponents.CUSTOM_NAME, Component.literal(ShopDisplay.formatCategoryTitle(category))
                        .withStyle(s -> s.withItalic(false).withBold(true)
                                .withColor(current ? ChatFormatting.GREEN : ShopDisplay.getCategoryColor(category))));
                icon.set(DataComponents.LORE, new ItemLore(List.of(
                        MenuUiSupport.labeledValue("Id", category, MenuUiSupport.LABEL_PRIMARY_COLOR),
                        current ? MenuUiSupport.line("Currently selected", ChatFormatting.GREEN)
                                : MenuUiSupport.labeledValue("Click", "Move item here", MenuUiSupport.LABEL_SECONDARY_COLOR))));
                container.setItem(i, icon);
            }

            container.setItem(NAV, MenuUiSupport.backButton());
            if (page > 0) container.setItem(NAV + 3, MenuUiSupport.prevPageButton());
            container.setItem(NAV + 4, MenuUiSupport.pageIndicator(page, totalPages));
            if (start + GRID_SLOTS < categories.size()) container.setItem(NAV + 5, MenuUiSupport.nextPageButton());

            container.setItem(NAV + 8, MenuUiSupport.button(Items.NAME_TAG, "New category", ChatFormatting.AQUA,
                    MenuUiSupport.hint("Type a name to create a new page."),
                    MenuUiSupport.hint("Use blocks.wood for a sub-page.")));

            MenuUiSupport.fillBackground(container);
        }

        private void choose(ServerPlayer player, String category) {
            Draft next = draft.withCategory(category);
            if (store(player, eco, next)) {
                openEditor(player, eco, origin, next, returnCategory);
            } else {
                openList(player, eco, origin, returnCategory, null, 0);
            }
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 54) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot < GRID_SLOTS) {
                int index = page * GRID_SLOTS + slot;
                if (index < categories.size()) {
                    choose(viewer, categories.get(index));
                }
                return true;
            }
            if (slot == NAV) {
                openEditor(viewer, eco, origin, draft, returnCategory);
                return true;
            }
            if (slot == NAV + 3 && page > 0) { page--; render(); return true; }
            if (slot == NAV + 5 && (page + 1) * GRID_SLOTS < categories.size()) { page++; render(); return true; }
            if (slot == NAV + 8) {
                TextInputUi.open(viewer, "New category", draft.category(), Items.NAME_TAG, "Use: ", "Type a name",
                        (p, text) -> choose(p, sanitize(text)));
                return true;
            }
            return true;
        }

        private static String sanitize(String raw) {
            String cleaned = raw.trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9._ ]", "")
                    .replace(' ', '_')
                    .replaceAll("_+", "_")
                    .replaceAll("^[._]+|[._]+$", "");
            return cleaned.isBlank() ? "misc" : cleaned;
        }
    }
}
