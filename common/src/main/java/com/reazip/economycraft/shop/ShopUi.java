package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.HubUi;
import com.reazip.economycraft.orders.OrdersUi;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ContainerPreviewUi;
import com.reazip.economycraft.util.ItemPickerUi;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.NumberInputUi;
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

public final class ShopUi {
    private ShopUi() {}

    public static void open(ServerPlayer player, ShopManager shop) {
        open(player, shop, 0, null, SortMode.DEFAULT, false);
    }

    public static void openSearch(ServerPlayer player, ShopManager shop, String query) {
        open(player, shop, 0, query, SortMode.DEFAULT, false);
    }

    static void open(ServerPlayer player, ShopManager shop, int page, @Nullable String query, SortMode sort, boolean mineOnly) {
        MenuUiSupport.openMenu(player, "Shop", (id, inv) -> new ShopMenu(id, inv, shop, player, page, query, sort, mineOnly));
    }

    private static void openConfirm(ServerPlayer player, ShopManager shop, ShopListing listing,
                                    @Nullable String query, SortMode sort, boolean mineOnly) {
        MenuUiSupport.openMenu(player, "Confirm", (id, inv) ->
                new ConfirmMenu(id, inv, shop, listing, player, query, sort, mineOnly));
    }

    private static void openRemove(ServerPlayer player, ShopManager shop, ShopListing listing,
                                   @Nullable String query, SortMode sort, boolean mineOnly) {
        MenuUiSupport.openMenu(player, "Remove", (id, inv) ->
                new RemoveMenu(id, inv, shop, listing, player, query, sort, mineOnly));
    }

    private static boolean canAfford(ServerPlayer player, long price) {
        long total = price + Math.round(price * EconomyConfig.get().taxRate);
        return EconomyCraft.getManager(player.level().getServer()).getBalance(player.getUUID(), true) >= total;
    }

    private static Component createPriceLore(long price, long tax) {
        StringBuilder value = new StringBuilder(EconomyCraft.formatMoney(price));
        if (tax > 0) {
            value.append(" (+").append(EconomyCraft.formatMoney(tax)).append(" tax)");
        }
        return MenuUiSupport.labeledValue("Price", value.toString(), MenuUiSupport.LABEL_PRIMARY_COLOR);
    }

    private static void startListing(ServerPlayer player, ShopManager shop) {
        ItemPickerUi.open(player, "Pick an item to sell", ItemPickerUi.Source.INVENTORY, null,
                (picker, choice) -> chooseAmount(picker, shop, choice),
                p -> open(p, shop));
    }

    private static void chooseAmount(ServerPlayer player, ShopManager shop, ItemPickerUi.Choice choice) {
        ItemStack prototype = choice.prototype();
        int max = Math.min(choice.heldCount(), prototype.getMaxStackSize());
        if (max <= 0) {
            player.sendSystemMessage(MenuUiSupport.line("You no longer have that item.", ChatFormatting.RED));
            open(player, shop);
            return;
        }
        if (max == 1) {
            choosePrice(player, shop, prototype, 1);
            return;
        }

        NumberInputUi.openCount(player, "How many?", prototype, "Amount", max, 1, max,
                (p, amount) -> choosePrice(p, shop, prototype, amount.intValue()),
                p -> startListing(p, shop));
    }

    private static void choosePrice(ServerPlayer player, ShopManager shop, ItemStack prototype, int amount) {
        NumberInputUi.openMoney(player, "Set your price", prototype.copyWithCount(amount), "Price",
                100, 1, EconomyManager.MAX, "Confirm and list", price -> listingLore(amount, price),
                (p, price) -> createListing(p, shop, prototype, amount, price),
                p -> backFromPrice(player, shop, prototype));
    }

    private static void backFromPrice(ServerPlayer player, ShopManager shop, ItemStack prototype) {
        int held = countHeld(player, prototype);
        if (Math.min(held, prototype.getMaxStackSize()) <= 1) {
            startListing(player, shop);
        } else {
            chooseAmount(player, shop, new ItemPickerUi.Choice(prototype, held));
        }
    }

    private static List<Component> listingLore(int amount, long price) {
        long tax = Math.round(price * EconomyConfig.get().taxRate);
        List<Component> lore = new ArrayList<>();
        lore.add(MenuUiSupport.labeledValue("Amount", String.valueOf(amount), MenuUiSupport.LABEL_PRIMARY_COLOR));
        lore.add(MenuUiSupport.labeledValue("You receive", EconomyCraft.formatMoney(price),
                MenuUiSupport.LABEL_PRIMARY_COLOR));
        if (tax > 0) {
            lore.add(MenuUiSupport.hint("The buyer pays " + EconomyCraft.formatMoney(price + tax)));
        }
        return lore;
    }

    private static void createListing(ServerPlayer player, ShopManager shop, ItemStack prototype, int amount, long price) {
        if (!takeFromInventory(player, prototype, amount)) {
            player.sendSystemMessage(MenuUiSupport.line("You no longer have " + amount + "x "
                    + prototype.getHoverName().getString() + ".", ChatFormatting.RED));
            open(player, shop);
            return;
        }

        ShopListing listing = new ShopListing();
        listing.seller = player.getUUID();
        listing.price = price;
        listing.item = prototype.copyWithCount(amount);
        shop.addListing(listing);

        long tax = Math.round(price * EconomyConfig.get().taxRate);
        player.sendSystemMessage(Component.literal("Listed " + amount + "x " + prototype.getHoverName().getString()
                        + " for " + EconomyCraft.formatMoney(price)
                        + (tax > 0 ? " (buyers pay " + EconomyCraft.formatMoney(price + tax) + ")" : ""))
                .withStyle(ChatFormatting.GREEN));
        open(player, shop);
    }

    private static int countHeld(ServerPlayer player, ItemStack prototype) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < ItemPickerUi.MAIN_INVENTORY_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, prototype)) total += stack.getCount();
        }
        return total;
    }

    private static boolean takeFromInventory(ServerPlayer player, ItemStack prototype, int amount) {
        if (countHeld(player, prototype) < amount) return false;
        Inventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < ItemPickerUi.MAIN_INVENTORY_SLOTS && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!ItemStack.isSameItemSameComponents(stack, prototype)) continue;
            int take = Math.min(stack.getCount(), remaining);
            stack.shrink(take);
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            remaining -= take;
        }
        return remaining == 0;
    }

    private static void sendStoredMessage(ServerPlayer player) {
        ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
        if (ev != null) {
            player.sendSystemMessage(Component.literal("Item stored: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("[Claim]")
                            .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev))));
        } else {
            ChatCompat.sendRunCommandTellraw(player, "Item stored: ", "[Claim]", "/eco orders claim");
        }
    }

    private static class ShopMenu extends CompatMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        @Nullable private final String query;
        private SortMode sort;
        private boolean mineOnly;
        private List<ShopListing> listings;
        private final SimpleContainer container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private int page;
        private final Runnable listener = this::updatePage;

        ShopMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer, int page, @Nullable String query,
                 SortMode sort, boolean mineOnly) {
            this(id, inv, shop, viewer, page, query, sort, mineOnly, resolveListings(shop, query, sort, mineOnly, viewer));
        }

        private ShopMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer, int page, @Nullable String query,
                         SortMode sort, boolean mineOnly, List<ShopListing> resolved) {
            super(MenuUiSupport.getMenuType(MenuUiSupport.requiredRows(resolved.size())), id);
            this.shop = shop;
            this.viewer = viewer;
            this.page = page;
            this.query = query;
            this.sort = sort;
            this.mineOnly = mineOnly;
            this.rows = MenuUiSupport.requiredRows(resolved.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.container = new SimpleContainer(rows * 9);
            this.listings = resolved;
            renderPage();
            shop.addListener(listener);
            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, rows * 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + rows * 18 + 14)) {
                this.addSlot(slot);
            }
        }

        private static List<ShopListing> resolveListings(ShopManager shop, @Nullable String query, SortMode sort,
                                                        boolean mineOnly, ServerPlayer viewer) {
            List<ShopListing> list = new ArrayList<>(shop.getListings());
            if (query != null && !query.isBlank()) {
                list.removeIf(l -> !MenuUiSupport.matchesSearch(l.item, query));
            }
            if (mineOnly) {
                list.removeIf(l -> !viewer.getUUID().equals(l.seller));
            }
            if (sort == SortMode.PRICE_ASC) {
                list.sort(Comparator.comparingLong(l -> l.price));
            } else if (sort == SortMode.PRICE_DESC) {
                list.sort((a, b) -> Long.compare(b.price, a.price));
            }
            return list;
        }

        private void updatePage() {
            listings = resolveListings(shop, query, sort, mineOnly, viewer);
            renderPage();
        }

        private void cycleSort() {
            if (mineOnly) {
                mineOnly = false;
                sort = SortMode.DEFAULT;
            } else if (sort == SortMode.DEFAULT) {
                sort = SortMode.PRICE_ASC;
            } else if (sort == SortMode.PRICE_ASC) {
                sort = SortMode.PRICE_DESC;
            } else {
                sort = SortMode.DEFAULT;
                mineOnly = true;
            }
        }

        private void renderPage() {
            container.clearContent();
            int start = page * itemsPerPage;
            int totalPages = MenuUiSupport.totalPages(listings.size(), itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= listings.size()) break;

                ShopListing l = listings.get(idx);
                ItemStack display = l.item.copy();

                String sellerName = MenuUiSupport.resolvePlayerName(viewer.level().getServer(), l.seller);
                boolean mine = viewer.getUUID().equals(l.seller);

                long tax = Math.round(l.price * EconomyConfig.get().taxRate);
                List<Component> lore = new ArrayList<>();
                lore.add(createPriceLore(l.price, tax));
                lore.add(MenuUiSupport.labeledValue("Seller", mine ? "you" : sellerName, MenuUiSupport.LABEL_PRIMARY_COLOR));
                lore.add(MenuUiSupport.labeledValue("Click", mine ? "Take it back" : "Buy it", MenuUiSupport.LABEL_SECONDARY_COLOR));
                if (MenuUiSupport.hasContainerContents(l.item)) {
                    lore.add(MenuUiSupport.labeledValue("Ctrl+Q", "Preview contents", MenuUiSupport.LABEL_SECONDARY_COLOR));
                }
                display.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(i, display);
            }

            if (listings.isEmpty()) {
                container.setItem(Math.min(4, itemsPerPage - 1), MenuUiSupport.button(Items.BOOK, "Nothing for sale",
                        ChatFormatting.YELLOW, MenuUiSupport.hint("Be the first: click \"Sell an item\" below")));
            }

            if (page > 0) container.setItem(navRowStart + 3, MenuUiSupport.prevPageButton());
            if (start + itemsPerPage < listings.size()) container.setItem(navRowStart + 5, MenuUiSupport.nextPageButton());

            container.setItem(navRowStart, MenuUiSupport.createBalanceItem(viewer));

            container.setItem(navRowStart + 1, MenuUiSupport.button(Items.HOPPER, "Sort",
                    MenuUiSupport.LABEL_PRIMARY_COLOR,
                    Component.literal("Click to cycle").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
                    MenuUiSupport.toggleOption("Recently Listed", !mineOnly && sort == SortMode.DEFAULT),
                    MenuUiSupport.toggleOption("Lowest Price", !mineOnly && sort == SortMode.PRICE_ASC),
                    MenuUiSupport.toggleOption("Highest Price", !mineOnly && sort == SortMode.PRICE_DESC),
                    MenuUiSupport.toggleOption("Mine Only", mineOnly)));

            container.setItem(navRowStart + 2, MenuUiSupport.button(Items.WRITABLE_BOOK, "Sell an item",
                    ChatFormatting.GREEN, MenuUiSupport.hint("Pick an item, set a price, done.")));

            container.setItem(navRowStart + 4, MenuUiSupport.pageIndicator(page, totalPages));

            container.setItem(navRowStart + 6, MenuUiSupport.button(Items.ENDER_CHEST, "Deliveries",
                    ChatFormatting.LIGHT_PURPLE, MenuUiSupport.hint("Items waiting to be collected")));

            container.setItem(navRowStart + 7, MenuUiSupport.button(Items.NETHER_STAR, "Main menu", ChatFormatting.YELLOW));

            boolean searching = query != null && !query.isBlank();
            container.setItem(navRowStart + 8, searching
                    ? MenuUiSupport.clearSearchButton(query)
                    : MenuUiSupport.searchButton());

            MenuUiSupport.fillFooter(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (kind == ClickKind.THROW && slot >= 0 && slot < navRowStart) {
                int index = page * itemsPerPage + slot;
                if (index < listings.size() && MenuUiSupport.hasContainerContents(listings.get(index).item)) {
                    ContainerPreviewUi.open(viewer, listings.get(index).item,
                            () -> ShopUi.open(viewer, shop, page, query, sort, mineOnly));
                }
                return true;
            }
            if (kind != ClickKind.PICKUP) return false;

            if (slot >= 0 && slot < navRowStart) {
                int index = page * itemsPerPage + slot;
                if (index < listings.size()) {
                    ShopListing listing = listings.get(index);
                    if (listing.seller.equals(viewer.getUUID())) {
                        openRemove(viewer, shop, listing, query, sort, mineOnly);
                    } else if (!canAfford(viewer, listing.price)) {
                        viewer.sendSystemMessage(Component.literal("Not enough balance").withStyle(ChatFormatting.RED));
                    } else {
                        openConfirm(viewer, shop, listing, query, sort, mineOnly);
                    }
                    return true;
                }
            }
            if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return true; }
            if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < listings.size()) { page++; updatePage(); return true; }
            if (slot == navRowStart + 1) {
                cycleSort();
                page = 0;
                updatePage();
                return true;
            }
            if (slot == navRowStart + 2) {
                viewer.closeContainer();
                startListing(viewer, shop);
                return true;
            }
            if (slot == navRowStart + 6) {
                viewer.closeContainer();
                OrdersUi.openClaims(viewer, EconomyCraft.getManager(viewer.level().getServer()));
                return true;
            }
            if (slot == navRowStart + 7) {
                viewer.closeContainer();
                HubUi.open(viewer);
                return true;
            }
            if (slot == navRowStart + 8) {
                if (query != null && !query.isBlank()) {
                    ShopUi.open(viewer, shop, 0, null, sort, mineOnly);
                } else {
                    TextInputUi.openSearch(viewer, "Search Shop", (p, q) -> ShopUi.open(p, shop, 0, q, sort, mineOnly));
                }
                return true;
            }
            return false;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            shop.removeListener(listener);
        }
    }

    private static class ConfirmMenu extends CompatMenu {
        private final ShopManager shop;
        private final ShopListing listing;
        @Nullable private final String query;
        private final SortMode sort;
        private final boolean mineOnly;
        private final SimpleContainer container = new SimpleContainer(9);

        ConfirmMenu(int id, Inventory inv, ShopManager shop, ShopListing listing, ServerPlayer viewer,
                    @Nullable String query, SortMode sort, boolean mineOnly) {
            super(MenuType.GENERIC_9x1, id);
            this.shop = shop;
            this.listing = listing;
            this.query = query;
            this.sort = sort;
            this.mineOnly = mineOnly;

            container.setItem(MenuUiSupport.ROW_CONFIRM, MenuUiSupport.confirmButton("Confirm"));

            String sellerName = MenuUiSupport.resolvePlayerName(viewer.level().getServer(), listing.seller);

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            List<Component> lore = new ArrayList<>();
            lore.add(createPriceLore(listing.price, tax));
            lore.add(MenuUiSupport.labeledValue("Seller", sellerName, MenuUiSupport.LABEL_PRIMARY_COLOR));
            if (MenuUiSupport.hasContainerContents(listing.item)) {
                lore.add(MenuUiSupport.labeledValue("Ctrl+Q", "Preview contents", MenuUiSupport.LABEL_SECONDARY_COLOR));
            }
            item.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(MenuUiSupport.ROW_SUBJECT, item);

            container.setItem(MenuUiSupport.ROW_CANCEL, MenuUiSupport.cancelButton());
            MenuUiSupport.fillFooter(container);

            for (Slot slot : MenuUiSupport.confirmRowSlots(container)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 40)) {
                this.addSlot(slot);
            }
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (kind == ClickKind.THROW && slot == MenuUiSupport.ROW_SUBJECT && MenuUiSupport.hasContainerContents(listing.item)) {
                ContainerPreviewUi.open((ServerPlayer) player, listing.item,
                        () -> ShopUi.openConfirm((ServerPlayer) player, shop, listing, query, sort, mineOnly));
                return true;
            }
            if (kind != ClickKind.PICKUP) return false;

            if (slot == MenuUiSupport.ROW_CONFIRM) {
                ShopListing current = shop.getListing(listing.id);
                ServerPlayer sp = (ServerPlayer) player;
                var server = sp.level().getServer();

                if (current == null) {
                    sp.sendSystemMessage(Component.literal("Listing no longer available").withStyle(ChatFormatting.RED));
                } else {
                    EconomyManager eco = EconomyCraft.getManager(server);
                    long cost = current.price;
                    long tax = Math.round(cost * EconomyConfig.get().taxRate);
                    long total = cost + tax;

                    if (!eco.removeMoney(player.getUUID(), total)) {
                        sp.sendSystemMessage(Component.literal("Not enough balance").withStyle(ChatFormatting.RED));
                    } else {
                        eco.addMoney(current.seller, cost);
                        ShopListing sold = shop.removeListing(current.id);
                        if (sold != null) {
                            shop.notifySellerSale(sold, sp);
                        }
                        ItemStack stack = current.item.copy();
                        int count = stack.getCount();
                        Component name = stack.getHoverName();

                        String sellerName = MenuUiSupport.resolvePlayerName(server, current.seller);

                        if (!player.getInventory().add(stack)) {
                            shop.addDelivery(player.getUUID(), stack);
                            sendStoredMessage(sp);
                        } else {
                            sp.sendSystemMessage(
                                    Component.literal("Purchased " + count + "x " + name.getString() + " from " + sellerName +
                                                    " for " + EconomyCraft.formatMoney(total))
                                            .withStyle(ChatFormatting.GREEN));
                        }
                    }
                }
                player.closeContainer();
                ShopUi.open(sp, shop, 0, query, sort, mineOnly);
                return true;
            }

            if (slot == MenuUiSupport.ROW_CANCEL) {
                player.closeContainer();
                ShopUi.open((ServerPlayer) player, shop, 0, query, sort, mineOnly);
                return true;
            }
            return false;
        }
    }

    private static class RemoveMenu extends CompatMenu {
        private final ShopManager shop;
        private final ShopListing listing;
        private final ServerPlayer viewer;
        @Nullable private final String query;
        private final SortMode sort;
        private final boolean mineOnly;
        private final SimpleContainer container = new SimpleContainer(9);

        RemoveMenu(int id, Inventory inv, ShopManager shop, ShopListing listing, ServerPlayer viewer,
                   @Nullable String query, SortMode sort, boolean mineOnly) {
            super(MenuType.GENERIC_9x1, id);
            this.shop = shop;
            this.listing = listing;
            this.viewer = viewer;
            this.query = query;
            this.sort = sort;
            this.mineOnly = mineOnly;

            container.setItem(MenuUiSupport.ROW_CONFIRM, MenuUiSupport.confirmButton("Confirm"));

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(DataComponents.LORE, new ItemLore(List.of(
                    createPriceLore(listing.price, tax),
                    MenuUiSupport.labeledValue("Seller", "you", MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.line("This will remove the listing", ChatFormatting.RED))));
            container.setItem(MenuUiSupport.ROW_SUBJECT, item);

            container.setItem(MenuUiSupport.ROW_CANCEL, MenuUiSupport.cancelButton());
            MenuUiSupport.fillFooter(container);

            for (Slot slot : MenuUiSupport.confirmRowSlots(container)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 40)) {
                this.addSlot(slot);
            }
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (kind != ClickKind.PICKUP) return false;

            if (slot == MenuUiSupport.ROW_CONFIRM) {
                ShopListing removed = shop.removeListing(listing.id);
                if (removed != null) {
                    ItemStack stack = removed.item.copy();
                    if (!player.getInventory().add(stack)) {
                        shop.addDelivery(player.getUUID(), stack);
                        sendStoredMessage((ServerPlayer) player);
                    } else {
                        viewer.sendSystemMessage(Component.literal("Listing removed"));
                    }
                } else {
                    viewer.sendSystemMessage(Component.literal("Listing no longer available"));
                }
                player.closeContainer();
                ShopUi.open((ServerPlayer) player, shop, 0, query, sort, mineOnly);
                return true;
            }
            if (slot == MenuUiSupport.ROW_CANCEL) {
                player.closeContainer();
                ShopUi.open((ServerPlayer) player, shop, 0, query, sort, mineOnly);
                return true;
            }
            return false;
        }
    }
}
