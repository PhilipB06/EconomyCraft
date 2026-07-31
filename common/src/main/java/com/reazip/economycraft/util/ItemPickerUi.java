package com.reazip.economycraft.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ItemPickerUi {
    private ItemPickerUi() {}

    public static final int MAIN_INVENTORY_SLOTS = 36;
    private static final int MAX_SEARCH_RESULTS = 450;

    public enum Source {
        INVENTORY,
        INVENTORY_AND_ALL
    }

    public record Choice(ItemStack prototype, int heldCount) {}

    public static void open(ServerPlayer player, String title, Source source, @Nullable Predicate<ItemStack> filter,
                            BiConsumer<ServerPlayer, Choice> onPick, Consumer<ServerPlayer> onCancel) {
        open(player, title, source, filter, null, 0, onPick, onCancel);
    }

    private static void open(ServerPlayer player, String title, Source source, @Nullable Predicate<ItemStack> filter,
                             @Nullable String query, int page,
                             BiConsumer<ServerPlayer, Choice> onPick, Consumer<ServerPlayer> onCancel) {
        MenuUiSupport.openMenu(player, title, (id, inv) ->
                new PickerMenu(id, inv, player, title, source, filter, query, page, onPick, onCancel));
    }

    private static class PickerMenu extends CompatMenu {
        private final String title;
        private final Source source;
        @Nullable private final Predicate<ItemStack> filter;
        @Nullable private final String query;
        private final BiConsumer<ServerPlayer, Choice> onPick;
        private final Consumer<ServerPlayer> onCancel;
        private final ServerPlayer viewer;
        private final SimpleContainer container;
        private final List<Choice> choices;
        private final int rows;
        private final int gridSlots;
        private final int nav;
        private int page;

        PickerMenu(int id, Inventory inv, ServerPlayer viewer, String title, Source source,
                   @Nullable Predicate<ItemStack> filter, @Nullable String query, int page,
                   BiConsumer<ServerPlayer, Choice> onPick, Consumer<ServerPlayer> onCancel) {
            this(id, inv, viewer, title, source, filter, query, page, onPick, onCancel,
                    collect(viewer, source, filter, query));
        }

        private PickerMenu(int id, Inventory inv, ServerPlayer viewer, String title, Source source,
                           @Nullable Predicate<ItemStack> filter, @Nullable String query, int page,
                           BiConsumer<ServerPlayer, Choice> onPick, Consumer<ServerPlayer> onCancel,
                           List<Choice> resolved) {
            super(MenuUiSupport.getMenuType(MenuUiSupport.requiredRows(resolved.size())), id);
            this.title = title;
            this.source = source;
            this.filter = filter;
            this.query = query;
            this.onPick = onPick;
            this.onCancel = onCancel;
            this.viewer = viewer;
            this.choices = resolved;
            this.rows = MenuUiSupport.requiredRows(resolved.size());
            this.gridSlots = (rows - 1) * 9;
            this.nav = gridSlots;
            this.container = new SimpleContainer(rows * 9);
            this.page = Math.clamp(page, 0, Math.max(0, MenuUiSupport.totalPages(choices.size(), gridSlots) - 1));

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, rows * 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + rows * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private static List<Choice> collect(ServerPlayer player, Source source, @Nullable Predicate<ItemStack> filter,
                                            @Nullable String query) {
            List<Choice> out = new ArrayList<>();
            Inventory inv = player.getInventory();
            for (int i = 0; i < MAIN_INVENTORY_SLOTS; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                if (filter != null && !filter.test(stack)) continue;
                if (!MenuUiSupport.matchesSearch(stack, query)) continue;

                ItemStack prototype = stack.copyWithCount(1);
                int existing = indexOf(out, prototype);
                if (existing >= 0) {
                    Choice previous = out.get(existing);
                    out.set(existing, new Choice(previous.prototype(), previous.heldCount() + stack.getCount()));
                } else {
                    out.add(new Choice(prototype, stack.getCount()));
                }
            }

            if (source == Source.INVENTORY_AND_ALL && query != null && !query.isBlank()) {
                String needle = query.trim().toLowerCase(Locale.ROOT);
                for (Item item : ItemsCompat.allItems()) {
                    if (out.size() >= MAX_SEARCH_RESULTS) break;
                    ItemStack stack = new ItemStack(item);
                    if (filter != null && !filter.test(stack)) continue;
                    if (!stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) continue;
                    if (indexOf(out, stack) >= 0) continue;
                    out.add(new Choice(stack, 0));
                }
            }
            return out;
        }

        private static int indexOf(List<Choice> list, ItemStack stack) {
            for (int i = 0; i < list.size(); i++) {
                if (ItemStack.isSameItemSameComponents(list.get(i).prototype(), stack)) return i;
            }
            return -1;
        }

        private boolean searching() {
            return query != null && !query.isBlank();
        }

        private void render() {
            container.clearContent();
            int start = page * gridSlots;
            int totalPages = MenuUiSupport.totalPages(choices.size(), gridSlots);

            for (int i = 0; i < gridSlots; i++) {
                int index = start + i;
                if (index >= choices.size()) break;

                Choice choice = choices.get(index);
                ItemStack display = choice.prototype().copy();
                List<Component> lore = new ArrayList<>();
                if (choice.heldCount() > 0) {
                    lore.add(MenuUiSupport.labeledValue("You have", String.valueOf(choice.heldCount()),
                            MenuUiSupport.LABEL_PRIMARY_COLOR));
                }
                lore.add(MenuUiSupport.labeledValue("Click", "Choose this item", MenuUiSupport.LABEL_SECONDARY_COLOR));
                display.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(i, display);
            }

            if (choices.isEmpty()) {
                container.setItem(Math.min(4, gridSlots - 1), MenuUiSupport.button(Items.BOOK,
                        searching() ? "No matches" : "Nothing to pick", ChatFormatting.YELLOW,
                        MenuUiSupport.hint(searching()
                                ? "Nothing matched \"" + query + "\""
                                : "Put items in your inventory first")));
            }

            container.setItem(nav, MenuUiSupport.backButton());

            if (page > 0) container.setItem(nav + 3, MenuUiSupport.prevPageButton());
            container.setItem(nav + 4, MenuUiSupport.pageIndicator(page, totalPages));
            if (start + gridSlots < choices.size()) container.setItem(nav + 5, MenuUiSupport.nextPageButton());

            container.setItem(nav + 1, MenuUiSupport.button(Items.BOOK, "How this works", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Click any item above to choose it."),
                    MenuUiSupport.hint(source == Source.INVENTORY_AND_ALL
                            ? "Search to find items you don't own."
                            : "Only items you are carrying are shown.")));

            container.setItem(nav + 8, searching()
                    ? MenuUiSupport.clearSearchButton(query)
                    : MenuUiSupport.button(Items.COMPASS, "Search", ChatFormatting.GREEN,
                            MenuUiSupport.hint(source == Source.INVENTORY_AND_ALL
                                    ? "Search every item in the game"
                                    : "Search your inventory")));

            MenuUiSupport.fillFooter(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= rows * 9) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot < gridSlots) {
                int index = page * gridSlots + slot;
                if (index < choices.size()) {
                    viewer.closeContainer();
                    onPick.accept(viewer, choices.get(index));
                }
                return true;
            }

            if (slot == nav) {
                viewer.closeContainer();
                if (onCancel != null) onCancel.accept(viewer);
                return true;
            }
            if (slot == nav + 3 && page > 0) {
                page--;
                render();
                return true;
            }
            if (slot == nav + 5 && (page + 1) * gridSlots < choices.size()) {
                page++;
                render();
                return true;
            }
            if (slot == nav + 8) {
                if (searching()) {
                    open(viewer, title, source, filter, null, 0, onPick, onCancel);
                } else {
                    TextInputUi.openSearch(viewer, "Search items",
                            (p, q) -> open(p, title, source, filter, q, 0, onPick, onCancel));
                }
                return true;
            }
            return true;
        }
    }
}
