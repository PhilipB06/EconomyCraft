package com.reazip.economycraft.admin;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.shop.ShopDisplay;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ConfirmUi;
import com.reazip.economycraft.util.EconomyPermissions;
import com.reazip.economycraft.util.EconomyPermissions.Nodes;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.shop.ShopUi;
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
import net.minecraft.world.item.Item;
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
        SHOP
    }

    public record Draft(String key, ItemStack display, @Nullable ItemStack customItem, String category,
                        int stack, long unitBuy, long unitSell, boolean dynamicPriceEnabled) {

        Draft withCategory(String value) {
            return new Draft(key, display, customItem, value, stack, unitBuy, unitSell, dynamicPriceEnabled);
        }

        Draft withStack(int value) {
            return new Draft(key, display, customItem, category, value, unitBuy, unitSell, dynamicPriceEnabled);
        }

        Draft withBuy(long value) {
            return new Draft(key, display, customItem, category, stack, value, unitSell, dynamicPriceEnabled);
        }

        Draft withSell(long value) {
            return new Draft(key, display, customItem, category, stack, unitBuy, value, dynamicPriceEnabled);
        }

        Draft withDynamicPriceEnabled(boolean value) {
            return new Draft(key, display, customItem, category, stack, unitBuy, unitSell, value);
        }
    }

    private record CategoryDraft(String key, @Nullable String name, @Nullable ChatFormatting color,
                                 @Nullable String icon, boolean enabled, boolean dynamicPriceEnabled) {
        CategoryDraft withName(String value) {
            return new CategoryDraft(key, value, color, icon, enabled, dynamicPriceEnabled);
        }

        CategoryDraft withColor(@Nullable ChatFormatting value) {
            return new CategoryDraft(key, name, value, icon, enabled, dynamicPriceEnabled);
        }

        CategoryDraft withIcon(@Nullable String value) {
            return new CategoryDraft(key, name, color, value, enabled, dynamicPriceEnabled);
        }

        CategoryDraft withEnabled(boolean value) {
            return new CategoryDraft(key, name, color, icon, value, dynamicPriceEnabled);
        }

        CategoryDraft withDynamicPriceEnabled(boolean value) {
            return new CategoryDraft(key, name, color, icon, enabled, value);
        }
    }

    public static void open(ServerPlayer player, EconomyManager eco, Origin origin) {
        if (!MenuUiSupport.checkOrDeny(player, EconomyPermissions.checkAdmin(player, Nodes.ADMIN_SHOP))) return;
        openRoot(player, eco, origin, 0);
    }

    private static void openRoot(ServerPlayer player, EconomyManager eco, Origin origin, int page) {
        MenuUiSupport.openMenu(player, "Shop Editor", (id, inv) -> new CategoryMenu(id, inv, player, eco, origin, page));
    }

    private static void exit(ServerPlayer player, EconomyManager eco, Origin origin) {
        if (origin == Origin.SHOP) {
            ShopUi.open(player, eco);
        } else {
            AdminUi.open(player, eco);
        }
    }

    private static void openList(ServerPlayer player, EconomyManager eco, Origin origin, @Nullable String category,
                                 @Nullable String query, int page) {
        String title;
        if (query != null && category != null) {
            title = ShopDisplay.getCategoryName(eco.getPrices(), category, category) + ": " + query;
        } else if (query != null) {
            title = "Search: " + query;
        } else if (category != null) {
            title = ShopDisplay.getCategoryName(eco.getPrices(), category, category);
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

    private static void openCategoryEditor(ServerPlayer player, EconomyManager eco, Origin origin,
                                           String category, int returnPage) {
        MenuUiSupport.openMenu(player, "Edit Category", (id, inv) -> new CategoryEditorMenu(
                id, inv, player, eco, origin, toCategoryDraft(eco.getPrices(), category), returnPage));
    }

    private static void startAdd(ServerPlayer player, EconomyManager eco, Origin origin, @Nullable String category) {
        PriceRegistry prices = eco.getPrices();
        ItemPickerUi.open(player, "Pick an item to add", ItemPickerUi.Source.INVENTORY_AND_ALL, null,
                (picker, choice) -> {
                    ItemStack prototype = choice.prototype().copyWithCount(1);
                    IdentifierCompat.Id id = IdentifierCompat.wrap(BuiltInRegistries.ITEM.getKey(prototype.getItem()));
                    if (id == null) {
                        EconomySounds.failure(picker);
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
                            prototype.getMaxStackSize(), DEFAULT_BUY, DEFAULT_SELL, true);

                    if (!store(picker, eco, draft)) {
                        openList(picker, eco, origin, category, null, 0);
                        return;
                    }
                    picker.sendSystemMessage(Component.literal("Added " + prototype.getHoverName().getString()
                            + " to the shop.").withStyle(ChatFormatting.GREEN));
                    openEditor(picker, eco, origin, draft, category);
                },
                p -> openList(p, eco, origin, category, null, 0));
    }

    private static boolean store(ServerPlayer player, EconomyManager eco, Draft draft) {
        boolean ok = eco.getPrices().upsert(draft.key(), draft.category(), draft.stack(),
                draft.unitBuy(), draft.unitSell(), draft.customItem(), draft.dynamicPriceEnabled());
        if (!ok) {
            EconomySounds.failure(player);
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
                Math.max(1, entry.stack()), entry.unitBuy(), entry.unitSell(), entry.dynamicPriceEnabled());
    }

    private static List<Component> dynamicPriceLines(EconomyManager eco, Draft draft) {
        boolean active = draft.unitBuy() > 0 && eco.isDynamicPricingActive(draft.category(), draft.dynamicPriceEnabled());
        if (!active) {
            return List.of();
        }
        long current = eco.getEffectiveBuyPrice(draft.unitBuy(), active);
        return List.of(
                MenuUiSupport.labeledValue("Base Buy Price", EconomyCraft.formatMoney(draft.unitBuy()), MenuUiSupport.LABEL_PRIMARY_COLOR),
                MenuUiSupport.labeledValue("Current Buy Price", EconomyCraft.formatMoney(current), MenuUiSupport.LABEL_PRIMARY_COLOR),
                MenuUiSupport.labeledValue("Current Multiplier", EconomyCraft.formatMultiplier(eco.getDynamicPriceMultiplier()), MenuUiSupport.LABEL_PRIMARY_COLOR)
        );
    }

    private static ItemStack dynamicPriceToggleButton(boolean enabled, String hint) {
        return MenuUiSupport.button(
                enabled ? ItemsCompat.limeStainedGlassPane() : ItemsCompat.redStainedGlassPane(),
                "Dynamic Pricing", enabled ? ChatFormatting.GREEN : ChatFormatting.RED,
                MenuUiSupport.hint(hint),
                MenuUiSupport.labeledValue("Now", enabled ? "On" : "Off", MenuUiSupport.LABEL_PRIMARY_COLOR),
                MenuUiSupport.labeledValue("Click", enabled ? "Turn off" : "Turn on", MenuUiSupport.LABEL_SECONDARY_COLOR));
    }

    private static List<Component> draftLore(Draft draft, List<Component> dynamicLines) {
        List<Component> lore = new ArrayList<>();
        lore.add(MenuUiSupport.labeledValue("Category", draft.category(), MenuUiSupport.LABEL_PRIMARY_COLOR));
        if (!dynamicLines.isEmpty()) {
            lore.addAll(dynamicLines);
        } else {
            lore.add(MenuUiSupport.labeledValue("Buy", draft.unitBuy() > 0
                    ? EconomyCraft.formatMoney(draft.unitBuy()) : "not for sale", MenuUiSupport.LABEL_PRIMARY_COLOR));
        }
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

    private static CategoryDraft toCategoryDraft(PriceRegistry prices, String category) {
        PriceRegistry.CategorySettings settings = prices.categorySettings(category);
        String name = settings != null ? settings.name() : null;
        ChatFormatting color = null;
        if (settings != null && settings.color() != null) {
            for (ChatFormatting candidate : ShopDisplay.CATEGORY_COLORS) {
                if (candidate.name().equalsIgnoreCase(settings.color())) {
                    color = candidate;
                    break;
                }
            }
        }
        String icon = settings != null && settings.icon() != null ? settings.icon().asString() : null;
        return new CategoryDraft(category, name, color, icon, settings == null || settings.enabled(),
                settings == null || settings.dynamicPriceEnabled());
    }

    private static String categoryDraftName(CategoryDraft draft) {
        return draft.name() != null ? draft.name() : ShopDisplay.formatCategoryTitle(draft.key());
    }

    private static boolean storeCategory(ServerPlayer player, EconomyManager eco, CategoryDraft draft) {
        boolean ok = eco.getPrices().upsertCategory(draft.key(), draft.name(),
                draft.color() != null ? draft.color().name().toLowerCase(Locale.ROOT) : null,
                draft.icon(), draft.enabled(), draft.dynamicPriceEnabled());
        if (!ok) {
            EconomySounds.failure(player);
            player.sendSystemMessage(MenuUiSupport.line("Could not write prices.json. Check the server log.",
                    ChatFormatting.RED));
        }
        return ok;
    }

    private static void updateCategory(ServerPlayer player, EconomyManager eco, Origin origin,
                                       CategoryDraft draft, int returnPage) {
        if (storeCategory(player, eco, draft)) {
            openCategoryEditor(player, eco, origin, draft.key(), returnPage);
        } else {
            openRoot(player, eco, origin, returnPage);
        }
    }

    private static Item colorItem(ChatFormatting color) {
        String path = switch (color) {
            case BLACK -> "black_dye";
            case DARK_BLUE, BLUE -> "blue_dye";
            case DARK_AQUA -> "cyan_dye";
            case AQUA -> "light_blue_dye";
            case DARK_GREEN -> "green_dye";
            case GREEN -> "lime_dye";
            case DARK_RED, RED -> "red_dye";
            case DARK_PURPLE -> "purple_dye";
            case LIGHT_PURPLE -> "magenta_dye";
            case GOLD -> "orange_dye";
            case YELLOW -> "yellow_dye";
            case DARK_GRAY -> "gray_dye";
            case GRAY -> "light_gray_dye";
            case WHITE -> "white_dye";
            default -> "paper";
        };
        return IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM,
                IdentifierCompat.withDefaultNamespace(path)).orElse(Items.PAPER);
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
                icon.set(DataComponents.CUSTOM_NAME, Component.literal(
                                ShopDisplay.getCategoryName(prices, category, category))
                        .withStyle(s -> s.withItalic(false).withBold(true)
                                .withColor(ShopDisplay.getCategoryColor(prices, category, category))));
                icon.set(DataComponents.LORE, new ItemLore(List.of(
                        MenuUiSupport.labeledValue("Id", category, MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Items", String.valueOf(prices.allByCategory(category).size()),
                                MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Status", prices.isCategoryEnabled(category) ? "On" : "Off",
                                MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Left click", "Edit items", MenuUiSupport.LABEL_SECONDARY_COLOR),
                        MenuUiSupport.labeledValue("Right click", "Edit category", MenuUiSupport.LABEL_SECONDARY_COLOR))));
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
                    EconomySounds.click(viewer);
                    String category = categories.get(index);
                    if (dragType == 1) {
                        openCategoryEditor(viewer, eco, origin, category, page);
                    } else {
                        openList(viewer, eco, origin, category, null, 0);
                    }
                }
                return true;
            }
            if (slot == NAV) {
                EconomySounds.click(viewer);
                exit(viewer, eco, origin);
                return true;
            }
            if (slot == NAV + 1) {
                EconomySounds.click(viewer);
                viewer.closeContainer();
                startAdd(viewer, eco, origin, null);
                return true;
            }
            if (slot == NAV + 3 && page > 0) { EconomySounds.page(viewer); page--; render(); return true; }
            if (slot == NAV + 5 && (page + 1) * GRID_SLOTS < categories.size()) { EconomySounds.page(viewer); page++; render(); return true; }
            if (slot == NAV + 7) {
                EconomySounds.click(viewer);
                openList(viewer, eco, origin, null, null, 0);
                return true;
            }
            if (slot == NAV + 8) {
                EconomySounds.click(viewer);
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

                Draft itemDraft = toDraft(entry, viewer);
                List<Component> lore = new ArrayList<>(draftLore(itemDraft, dynamicPriceLines(eco, itemDraft)));
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
                    EconomySounds.click(viewer);
                    openEditor(viewer, eco, origin, toDraft(entries.get(index), viewer), category);
                }
                return true;
            }
            if (slot == NAV) {
                EconomySounds.click(viewer);
                openRoot(viewer, eco, origin, 0);
                return true;
            }
            if (slot == NAV + 1) {
                EconomySounds.click(viewer);
                viewer.closeContainer();
                startAdd(viewer, eco, origin, category);
                return true;
            }
            if (slot == NAV + 3 && page > 0) { EconomySounds.page(viewer); page--; render(); return true; }
            if (slot == NAV + 5 && (page + 1) * GRID_SLOTS < entries.size()) { EconomySounds.page(viewer); page++; render(); return true; }
            if (slot == NAV + 8) {
                EconomySounds.click(viewer);
                if (query != null) {
                    openList(viewer, eco, origin, category, null, 0);
                } else {
                    String title = category == null
                            ? "Search shop items"
                            : "Search " + ShopDisplay.getCategoryName(eco.getPrices(), category, category);
                    TextInputUi.openSearch(viewer, title, (p, q) -> openList(p, eco, origin, category, q, 0));
                }
                return true;
            }
            return true;
        }
    }

    private static class CategoryEditorMenu extends CompatMenu {
        private static final int CATEGORY = 4;
        private static final int DELETE = 8;
        private static final int NAME = 10;
        private static final int COLOR = 12;
        private static final int ICON = 14;
        private static final int ENABLED = 16;
        private static final int DYNAMIC_PRICE = 17;
        private static final int BACK = 18;

        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final Origin origin;
        private final CategoryDraft draft;
        private final int returnPage;
        private final SimpleContainer container = new SimpleContainer(27);

        CategoryEditorMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, Origin origin,
                           CategoryDraft draft, int returnPage) {
            super(MenuType.GENERIC_9x3, id);
            this.viewer = viewer;
            this.eco = eco;
            this.origin = origin;
            this.draft = draft;
            this.returnPage = returnPage;

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 27)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 3 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private ChatFormatting effectiveColor() {
            return draft.color() != null ? draft.color() : ShopDisplay.getCategoryColor(draft.key());
        }

        private void render() {
            container.clearContent();

            ItemStack display = categoryIcon(draft.key(), eco.getPrices(), viewer);
            display.setCount(1);
            display.set(DataComponents.CUSTOM_NAME, Component.literal(categoryDraftName(draft))
                    .withStyle(s -> s.withItalic(false).withBold(true).withColor(effectiveColor())));
            display.set(DataComponents.LORE, new ItemLore(List.of(
                    MenuUiSupport.labeledValue("Id", draft.key(), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Items", String.valueOf(eco.getPrices().categoryItemCount(draft.key())),
                            MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Status", draft.enabled() ? "On" : "Off",
                            MenuUiSupport.LABEL_PRIMARY_COLOR))));
            container.setItem(CATEGORY, display);

            container.setItem(NAME, MenuUiSupport.button(Items.NAME_TAG, "Name", ChatFormatting.AQUA,
                    MenuUiSupport.hint("The name players see."),
                    MenuUiSupport.labeledValue("Now", categoryDraftName(draft), MenuUiSupport.LABEL_PRIMARY_COLOR)));

            container.setItem(COLOR, MenuUiSupport.button(colorItem(effectiveColor()), "Color", effectiveColor(),
                    MenuUiSupport.hint("The category name color."),
                    MenuUiSupport.labeledValue("Now", draft.color() == null
                                    ? "Default" : ShopDisplay.formatCategoryTitle(draft.color().name()),
                            MenuUiSupport.LABEL_PRIMARY_COLOR)));

            ItemStack icon = categoryIcon(draft.key(), eco.getPrices(), viewer);
            icon.setCount(1);
            icon.set(DataComponents.CUSTOM_NAME, Component.literal("Icon")
                    .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.YELLOW)));
            List<Component> iconLore = new ArrayList<>();
            iconLore.add(MenuUiSupport.hint("The item used on the category page."));
            iconLore.add(MenuUiSupport.labeledValue("Now", draft.icon() == null ? "Default" : draft.icon(),
                    MenuUiSupport.LABEL_PRIMARY_COLOR));
            if (draft.icon() != null) {
                iconLore.add(MenuUiSupport.labeledValue("Right click", "Reset to default",
                        MenuUiSupport.LABEL_SECONDARY_COLOR));
            }
            icon.set(DataComponents.LORE, new ItemLore(iconLore));
            container.setItem(ICON, icon);

            container.setItem(ENABLED, MenuUiSupport.button(
                    draft.enabled() ? ItemsCompat.limeStainedGlassPane() : ItemsCompat.redStainedGlassPane(),
                    "Category Status", draft.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED,
                    MenuUiSupport.hint("Controls whether players can see and buy from it."),
                    MenuUiSupport.labeledValue("Now", draft.enabled() ? "On" : "Off",
                            MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Click", draft.enabled() ? "Turn off" : "Turn on",
                            MenuUiSupport.LABEL_SECONDARY_COLOR)));

            container.setItem(DYNAMIC_PRICE, dynamicPriceToggleButton(draft.dynamicPriceEnabled(),
                    "Whether buy prices here scale with the server economy."));

            Component deleteHint = "misc".equalsIgnoreCase(draft.key())
                    ? MenuUiSupport.hint("The fallback category cannot be deleted.")
                    : MenuUiSupport.hint("Moves its items to misc and disables buying them.");
            container.setItem(DELETE, MenuUiSupport.button(Items.BARRIER, "Delete Category", ChatFormatting.DARK_RED,
                    deleteHint));

            container.setItem(BACK, MenuUiSupport.button(ItemsCompat.redStainedGlassPane(), "Back",
                    ChatFormatting.DARK_RED, MenuUiSupport.hint("Changes are already saved.")));
            MenuUiSupport.fillBackground(container);
        }

        private void editIcon() {
            ItemPickerUi.open(viewer, "Pick a category icon", ItemPickerUi.Source.INVENTORY_AND_ALL, null,
                    (picker, choice) -> {
                        IdentifierCompat.Id id = IdentifierCompat.wrap(
                                BuiltInRegistries.ITEM.getKey(choice.prototype().getItem()));
                        if (id == null) {
                            picker.sendSystemMessage(MenuUiSupport.line("That item cannot be used as an icon.",
                                    ChatFormatting.RED));
                            openCategoryEditor(picker, eco, origin, draft.key(), returnPage);
                            return;
                        }
                        updateCategory(picker, eco, origin, draft.withIcon(id.asString()), returnPage);
                    },
                    p -> openCategoryEditor(p, eco, origin, draft.key(), returnPage));
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 27) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            switch (slot) {
                case NAME -> {
                    EconomySounds.click(viewer);
                    TextInputUi.open(viewer, "Category name", categoryDraftName(draft), Items.NAME_TAG,
                            "Use: ", "Type a name",
                            (p, text) -> updateCategory(p, eco, origin, draft.withName(text), returnPage));
                }
                case COLOR -> {
                    EconomySounds.click(viewer);
                    MenuUiSupport.openMenu(viewer, "Category Color",
                            (id, inv) -> new CategoryColorMenu(id, inv, viewer, eco, origin, draft, returnPage));
                }
                case ICON -> {
                    EconomySounds.click(viewer);
                    if (dragType == 1 && draft.icon() != null) {
                        updateCategory(viewer, eco, origin, draft.withIcon(null), returnPage);
                    } else {
                        editIcon();
                    }
                }
                case ENABLED -> {
                    EconomySounds.click(viewer);
                    updateCategory(viewer, eco, origin, draft.withEnabled(!draft.enabled()), returnPage);
                }
                case DYNAMIC_PRICE -> {
                    EconomySounds.click(viewer);
                    updateCategory(viewer, eco, origin, draft.withDynamicPriceEnabled(!draft.dynamicPriceEnabled()), returnPage);
                }
                case DELETE -> {
                    if ("misc".equalsIgnoreCase(draft.key())) {
                        EconomySounds.failure(viewer);
                        viewer.sendSystemMessage(MenuUiSupport.line("The misc category cannot be deleted.",
                                ChatFormatting.RED));
                        render();
                    } else {
                        EconomySounds.click(viewer);
                        confirmCategoryDelete(viewer, eco, origin, draft, returnPage);
                    }
                }
                case BACK -> {
                    EconomySounds.click(viewer);
                    openRoot(viewer, eco, origin, returnPage);
                }
                default -> {
                }
            }
            return true;
        }
    }

    private static class CategoryColorMenu extends CompatMenu {
        private static final int BACK = 18;

        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final Origin origin;
        private final CategoryDraft draft;
        private final int returnPage;
        private final SimpleContainer container = new SimpleContainer(27);

        CategoryColorMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, Origin origin,
                          CategoryDraft draft, int returnPage) {
            super(MenuType.GENERIC_9x3, id);
            this.viewer = viewer;
            this.eco = eco;
            this.origin = origin;
            this.draft = draft;
            this.returnPage = returnPage;

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
            boolean defaultSelected = draft.color() == null;
            container.setItem(0, MenuUiSupport.button(Items.PAPER, "Default", defaultSelected
                            ? ChatFormatting.GREEN : ChatFormatting.WHITE,
                    defaultSelected ? MenuUiSupport.line("Currently selected", ChatFormatting.GREEN)
                            : MenuUiSupport.hint("Use the built-in category color.")));

            for (int i = 0; i < ShopDisplay.CATEGORY_COLORS.size(); i++) {
                ChatFormatting color = ShopDisplay.CATEGORY_COLORS.get(i);
                boolean selected = color == draft.color();
                container.setItem(i + 1, MenuUiSupport.button(colorItem(color),
                        ShopDisplay.formatCategoryTitle(color.name()), selected ? ChatFormatting.GREEN : color,
                        selected ? MenuUiSupport.line("Currently selected", ChatFormatting.GREEN)
                                : MenuUiSupport.hint("Click to use this color.")));
            }

            container.setItem(BACK, MenuUiSupport.backButton());
            MenuUiSupport.fillBackground(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 27) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot == 0) {
                EconomySounds.click(viewer);
                updateCategory(viewer, eco, origin, draft.withColor(null), returnPage);
                return true;
            }
            int colorIndex = slot - 1;
            if (colorIndex >= 0 && colorIndex < ShopDisplay.CATEGORY_COLORS.size()) {
                EconomySounds.click(viewer);
                updateCategory(viewer, eco, origin,
                        draft.withColor(ShopDisplay.CATEGORY_COLORS.get(colorIndex)), returnPage);
                return true;
            }
            if (slot == BACK) {
                EconomySounds.click(viewer);
                openCategoryEditor(viewer, eco, origin, draft.key(), returnPage);
                return true;
            }
            return true;
        }
    }

    private static void confirmCategoryDelete(ServerPlayer player, EconomyManager eco, Origin origin,
                                              CategoryDraft draft, int returnPage) {
        int itemCount = eco.getPrices().categoryItemCount(draft.key());
        ItemStack subject = categoryIcon(draft.key(), eco.getPrices(), player);
        subject.set(DataComponents.CUSTOM_NAME, Component.literal(categoryDraftName(draft))
                .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.RED)));
        List<Component> lore = List.of(
                MenuUiSupport.labeledValue("Items", String.valueOf(itemCount), MenuUiSupport.LABEL_PRIMARY_COLOR),
                MenuUiSupport.line("The category and its subcategories will be deleted.", ChatFormatting.RED),
                MenuUiSupport.hint("Every affected item moves to misc."),
                MenuUiSupport.hint("Their buy prices will be set to 0."));

        ConfirmUi.open(player, "Delete category?", subject, "Delete category", lore,
                p -> {
                    boolean ok = eco.getPrices().deleteCategory(draft.key());
                    if (ok) EconomySounds.click(p); else EconomySounds.failure(p);
                    p.sendSystemMessage(ok
                            ? Component.literal("Deleted " + categoryDraftName(draft) + " and moved " + itemCount
                                    + " item" + (itemCount == 1 ? "" : "s") + " to misc.")
                                    .withStyle(ChatFormatting.GREEN)
                            : MenuUiSupport.line("Could not write prices.json. Check the server log.",
                                    ChatFormatting.RED));
                    openRoot(p, eco, origin, returnPage);
                },
                p -> openCategoryEditor(p, eco, origin, draft.key(), returnPage));
    }

    private static class EditorMenu extends CompatMenu {
        private static final int ITEM = 4;
        private static final int DELETE = 8;
        private static final int BUY = 10;
        private static final int SELL = 12;
        private static final int STACK = 14;
        private static final int CATEGORY = 16;
        private static final int DYNAMIC_PRICE = 17;
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

            List<Component> dynamicLines = dynamicPriceLines(eco, draft);

            ItemStack display = draft.display().copy();
            display.setCount(1);
            List<Component> lore = new ArrayList<>(draftLore(draft, dynamicLines));
            lore.add(MenuUiSupport.italicHint(draft.key()));
            display.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(ITEM, display);

            List<Component> buyLore = new ArrayList<>();
            buyLore.add(MenuUiSupport.hint("What players pay for one."));
            if (!dynamicLines.isEmpty()) {
                buyLore.addAll(dynamicLines);
            } else {
                buyLore.add(MenuUiSupport.labeledValue("Now", draft.unitBuy() > 0
                        ? EconomyCraft.formatMoney(draft.unitBuy()) : "not for sale", MenuUiSupport.LABEL_PRIMARY_COLOR));
            }
            buyLore.add(MenuUiSupport.italicHint("Set to 0 to hide it from the shop."));
            container.setItem(BUY, MenuUiSupport.button(Items.GOLD_INGOT, "Buy Price", ChatFormatting.GOLD,
                    buyLore.toArray(new Component[0])));

            container.setItem(SELL, MenuUiSupport.button(Items.EMERALD, "Sell Price", ChatFormatting.GREEN,
                    MenuUiSupport.hint("What players get for one."),
                    MenuUiSupport.labeledValue("Now", draft.unitSell() > 0
                            ? EconomyCraft.formatMoney(draft.unitSell()) : "not sellable", MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.italicHint("Set to 0 to stop players selling it.")));

            container.setItem(STACK, MenuUiSupport.button(Items.CHEST, "Bulk Amount", ChatFormatting.AQUA,
                    MenuUiSupport.hint("How many a shift-click buys or sells."),
                    MenuUiSupport.labeledValue("Now", String.valueOf(draft.stack()), MenuUiSupport.LABEL_PRIMARY_COLOR)));

            container.setItem(CATEGORY, MenuUiSupport.button(Items.BOOK, "Category", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Which page of the shop it lives on."),
                    MenuUiSupport.labeledValue("Now", draft.category(), MenuUiSupport.LABEL_PRIMARY_COLOR)));

            container.setItem(DYNAMIC_PRICE, dynamicPriceToggleButton(draft.dynamicPriceEnabled(),
                    "Whether this item's buy price scales with the server economy."));

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
                EconomySounds.click(viewer);
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
                case BUY -> {
                    EconomySounds.click(viewer);
                    NumberInputUi.openMoney(viewer, "Buy price", draft.display(), "Buy price",
                            draft.unitBuy(), 0, EconomyManager.MAX,
                            (p, value) -> update(draft.withBuy(value)),
                            p -> openEditor(p, eco, origin, draft, returnCategory));
                }
                case SELL -> {
                    EconomySounds.click(viewer);
                    NumberInputUi.openMoney(viewer, "Sell price", draft.display(), "Sell price",
                            draft.unitSell(), 0, EconomyManager.MAX,
                            (p, value) -> update(draft.withSell(value)),
                            p -> openEditor(p, eco, origin, draft, returnCategory));
                }
                case STACK -> {
                    EconomySounds.click(viewer);
                    NumberInputUi.openCount(viewer, "Bulk amount", draft.display(), "Bulk amount",
                            draft.stack(), 1, 64,
                            (p, value) -> update(draft.withStack(value.intValue())),
                            p -> openEditor(p, eco, origin, draft, returnCategory));
                }
                case CATEGORY -> {
                    EconomySounds.click(viewer);
                    MenuUiSupport.openMenu(viewer, "Pick a category",
                            (id, inv) -> new CategoryPickerMenu(id, inv, viewer, eco, origin, draft, returnCategory));
                }
                case DYNAMIC_PRICE -> {
                    EconomySounds.click(viewer);
                    update(draft.withDynamicPriceEnabled(!draft.dynamicPriceEnabled()));
                }
                case DELETE -> {
                    EconomySounds.click(viewer);
                    confirmDelete(viewer, eco, origin, draft, returnCategory);
                }
                case BACK -> {
                    EconomySounds.click(viewer);
                    back(viewer);
                }
                default -> {
                }
            }
            return true;
        }
    }

    private static void confirmDelete(ServerPlayer player, EconomyManager eco, Origin origin, Draft draft,
                                      @Nullable String returnCategory) {
        List<Component> lore = new ArrayList<>(draftLore(draft, dynamicPriceLines(eco, draft)));
        lore.add(MenuUiSupport.line("Players will no longer see this item.", ChatFormatting.RED));

        ConfirmUi.open(player, "Delete this item?", draft.display(), "Delete it", lore,
                p -> {
                    boolean ok = eco.getPrices().delete(draft.key());
                    if (ok) EconomySounds.click(p); else EconomySounds.failure(p);
                    p.sendSystemMessage(ok
                            ? Component.literal("Removed " + draft.display().getHoverName().getString()
                                    + " from the shop.").withStyle(ChatFormatting.GREEN)
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
                icon.set(DataComponents.CUSTOM_NAME, Component.literal(
                                ShopDisplay.getCategoryName(eco.getPrices(), category, category))
                        .withStyle(s -> s.withItalic(false).withBold(true)
                                .withColor(current ? ChatFormatting.GREEN
                                        : ShopDisplay.getCategoryColor(eco.getPrices(), category, category))));
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
                    EconomySounds.click(viewer);
                    choose(viewer, categories.get(index));
                }
                return true;
            }
            if (slot == NAV) {
                EconomySounds.click(viewer);
                openEditor(viewer, eco, origin, draft, returnCategory);
                return true;
            }
            if (slot == NAV + 3 && page > 0) { EconomySounds.page(viewer); page--; render(); return true; }
            if (slot == NAV + 5 && (page + 1) * GRID_SLOTS < categories.size()) { EconomySounds.page(viewer); page++; render(); return true; }
            if (slot == NAV + 8) {
                EconomySounds.click(viewer);
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
