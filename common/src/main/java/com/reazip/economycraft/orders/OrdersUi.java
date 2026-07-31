package com.reazip.economycraft.orders;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.HubUi;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ContainerPreviewUi;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ItemPickerUi;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.NumberInputUi;
import com.reazip.economycraft.util.SortMode;
import com.reazip.economycraft.util.TextInputUi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
import java.util.UUID;

public final class OrdersUi {
    private OrdersUi() {}

    public static void open(ServerPlayer player, EconomyManager eco) {
        open(player, eco, 0, null, SortMode.DEFAULT, false);
    }

    public static void openSearch(ServerPlayer player, EconomyManager eco, String query) {
        open(player, eco, 0, query, SortMode.DEFAULT, false);
    }

    private static void open(ServerPlayer player, EconomyManager eco, int page, @Nullable String query,
                             SortMode sort, boolean mineOnly) {
        MenuUiSupport.openMenu(player, "Orders", (id, inv) ->
                new RequestMenu(id, inv, eco.getOrders(), eco, player, page, query, sort, mineOnly));
    }

    public static void openClaims(ServerPlayer player, EconomyManager eco) {
        openClaims(player, eco, 0);
    }

    private static void openClaims(ServerPlayer player, EconomyManager eco, int page) {
        MenuUiSupport.openMenu(player, "Deliveries", (id, inv) -> new ClaimMenu(id, inv, eco, player.getUUID(), page));
    }

    private static Component createRewardLore(long reward, long tax) {
        StringBuilder value = new StringBuilder(EconomyCraft.formatMoney(reward));
        if (tax > 0) {
            value.append(" (-").append(EconomyCraft.formatMoney(tax)).append(" tax)");
        }
        return MenuUiSupport.labeledValue("Reward", value.toString(), MenuUiSupport.LABEL_PRIMARY_COLOR);
    }

    public static void startRequest(ServerPlayer player, EconomyManager eco) {
        ItemPickerUi.open(player, "What do you want?", ItemPickerUi.Source.INVENTORY_AND_ALL, null,
                (picker, choice) -> chooseAmount(picker, eco, choice.prototype()),
                p -> open(p, eco));
    }

    private static void chooseAmount(ServerPlayer player, EconomyManager eco, ItemStack prototype) {
        int max = SellService.MAIN_INVENTORY_SLOTS * prototype.getMaxStackSize();
        NumberInputUi.openCount(player, "How many?", prototype, "Amount", prototype.getMaxStackSize(), 1, max,
                (p, amount) -> choosePrice(p, eco, prototype, amount.intValue()),
                p -> startRequest(p, eco));
    }

    private static void choosePrice(ServerPlayer player, EconomyManager eco, ItemStack prototype, int amount) {
        NumberInputUi.openMoney(player, "What will you pay?",
                prototype.copyWithCount(Math.min(amount, prototype.getMaxStackSize())),
                "Total reward", 100L * amount, 1, EconomyManager.MAX,
                "Confirm and post", price -> requestLore(player, eco, amount, price),
                (p, price) -> createRequest(p, eco, prototype, amount, price),
                p -> chooseAmount(p, eco, prototype));
    }

    private static List<Component> requestLore(ServerPlayer player, EconomyManager eco, int amount, long price) {
        long tax = Math.round(price * EconomyConfig.get().taxRate);
        long balance = eco.getBalance(player.getUUID(), true);

        List<Component> lore = new ArrayList<>();
        lore.add(MenuUiSupport.labeledValue("Amount", String.valueOf(amount), MenuUiSupport.LABEL_PRIMARY_COLOR));
        lore.add(MenuUiSupport.labeledValue("You pay", EconomyCraft.formatMoney(price), MenuUiSupport.LABEL_PRIMARY_COLOR));
        lore.add(MenuUiSupport.labeledValue("Per item", EconomyCraft.formatMoney(price / Math.max(1, amount)),
                MenuUiSupport.LABEL_PRIMARY_COLOR));
        lore.add(createRewardLore(price, tax));
        lore.add(MenuUiSupport.hint("You are charged as the order is filled."));
        if (balance < price) {
            lore.add(MenuUiSupport.line("Your balance (" + EconomyCraft.formatMoney(balance) + ") is lower than this.",
                    ChatFormatting.RED));
        }
        return lore;
    }

    private static void createRequest(ServerPlayer player, EconomyManager eco, ItemStack prototype, int amount, long price) {
        OrderRequest request = new OrderRequest();
        request.requester = player.getUUID();
        request.price = price;
        request.item = prototype.copyWithCount(1);
        request.amount = amount;
        eco.getOrders().addRequest(request);

        long tax = Math.round(price * EconomyConfig.get().taxRate);
        player.sendSystemMessage(Component.literal("Requested " + amount + "x "
                        + prototype.getHoverName().getString() + " for " + EconomyCraft.formatMoney(price)
                        + (tax > 0 ? " (fulfiller receives " + EconomyCraft.formatMoney(price - tax) + ")" : ""))
                .withStyle(ChatFormatting.GREEN));
        open(player, eco);
    }

    private static class RequestMenu extends CompatMenu {
        private final OrderManager orders;
        private final EconomyManager eco;
        private final ServerPlayer viewer;
        @Nullable private final String query;
        private SortMode sort;
        private boolean mineOnly;
        private List<OrderRequest> requests;
        private final SimpleContainer container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private int page;
        private final Runnable listener = this::updatePage;

        RequestMenu(int id, Inventory inv, OrderManager orders, EconomyManager eco, ServerPlayer viewer, int page,
                    @Nullable String query, SortMode sort, boolean mineOnly) {
            this(id, inv, orders, eco, viewer, page, query, sort, mineOnly,
                    resolveRequests(orders, query, sort, mineOnly, viewer));
        }

        private RequestMenu(int id, Inventory inv, OrderManager orders, EconomyManager eco, ServerPlayer viewer, int page,
                            @Nullable String query, SortMode sort, boolean mineOnly, List<OrderRequest> resolved) {
            super(MenuUiSupport.getMenuType(MenuUiSupport.requiredRows(resolved.size())), id);
            this.orders = orders;
            this.eco = eco;
            this.viewer = viewer;
            this.page = page;
            this.query = query;
            this.sort = sort;
            this.mineOnly = mineOnly;
            this.rows = MenuUiSupport.requiredRows(resolved.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.container = new SimpleContainer(rows * 9);
            this.requests = resolved;
            renderPage();
            orders.addListener(listener);
            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, rows * 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + rows * 18 + 14)) {
                this.addSlot(slot);
            }
        }

        private static List<OrderRequest> resolveRequests(OrderManager orders, @Nullable String query, SortMode sort,
                                                         boolean mineOnly, ServerPlayer viewer) {
            List<OrderRequest> list = new ArrayList<>(orders.getRequests());
            if (query != null && !query.isBlank()) {
                list.removeIf(r -> !MenuUiSupport.matchesSearch(r.item, query));
            }
            if (mineOnly) {
                list.removeIf(r -> !viewer.getUUID().equals(r.requester));
            }
            if (sort == SortMode.PRICE_ASC) {
                list.sort(Comparator.comparingLong(r -> r.price));
            } else if (sort == SortMode.PRICE_DESC) {
                list.sort((a, b) -> Long.compare(b.price, a.price));
            }
            return list;
        }

        private void updatePage() {
            requests = resolveRequests(orders, query, sort, mineOnly, viewer);
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
            int totalPages = MenuUiSupport.totalPages(requests.size(), itemsPerPage);

            var server = viewer.level().getServer();

            for (int i = 0; i < itemsPerPage; i++) {
                int index = start + i;
                if (index >= requests.size()) break;

                OrderRequest r = requests.get(index);
                ItemStack display = r.item.copy();

                boolean mine = viewer.getUUID().equals(r.requester);
                String reqName = MenuUiSupport.resolvePlayerName(server, r.requester);

                long tax = Math.round(r.price * EconomyConfig.get().taxRate);
                List<Component> lore = new ArrayList<>(List.of(
                        createRewardLore(r.price, tax),
                        MenuUiSupport.labeledValue("Amount", String.valueOf(r.amount), MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Requester", mine ? "you" : reqName, MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Click", mine ? "Cancel this request" : "Fulfill it",
                                MenuUiSupport.LABEL_SECONDARY_COLOR)));
                if (MenuUiSupport.hasContainerContents(r.item)) {
                    lore.add(MenuUiSupport.labeledValue("Ctrl+Q", "Preview contents", MenuUiSupport.LABEL_SECONDARY_COLOR));
                }
                display.set(DataComponents.LORE, new ItemLore(lore));
                display.setCount(1);
                container.setItem(i, display);
            }

            if (requests.isEmpty()) {
                container.setItem(Math.min(4, itemsPerPage - 1), MenuUiSupport.button(Items.BOOK, "No open requests",
                        ChatFormatting.YELLOW, MenuUiSupport.hint("Click \"New request\" below to post one")));
            }

            if (page > 0) container.setItem(navRowStart + 3, MenuUiSupport.prevPageButton());
            if (start + itemsPerPage < requests.size()) container.setItem(navRowStart + 5, MenuUiSupport.nextPageButton());

            container.setItem(navRowStart, MenuUiSupport.createBalanceItem(eco, viewer.getUUID(), viewer,
                    IdentityCompat.of(viewer).name()));

            container.setItem(navRowStart + 1, MenuUiSupport.button(Items.HOPPER, "Sort",
                    MenuUiSupport.LABEL_PRIMARY_COLOR,
                    Component.literal("Click to cycle").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
                    MenuUiSupport.toggleOption("Recently Listed", !mineOnly && sort == SortMode.DEFAULT),
                    MenuUiSupport.toggleOption("Lowest Reward", !mineOnly && sort == SortMode.PRICE_ASC),
                    MenuUiSupport.toggleOption("Highest Reward", !mineOnly && sort == SortMode.PRICE_DESC),
                    MenuUiSupport.toggleOption("Mine Only", mineOnly)));

            container.setItem(navRowStart + 2, MenuUiSupport.button(Items.WRITABLE_BOOK, "New request",
                    ChatFormatting.GREEN, MenuUiSupport.hint("Pick any item and name your price.")));

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
                if (index < requests.size() && MenuUiSupport.hasContainerContents(requests.get(index).item)) {
                    ContainerPreviewUi.open(viewer, requests.get(index).item,
                            () -> OrdersUi.open(viewer, eco, page, query, sort, mineOnly));
                }
                return true;
            }
            if (kind != ClickKind.PICKUP) return false;

            if (slot >= 0 && slot < navRowStart) {
                int index = page * itemsPerPage + slot;
                if (index < requests.size()) {
                    OrderRequest req = requests.get(index);
                    if (req.requester.equals(viewer.getUUID())) {
                        openRemove(viewer, req);
                    } else if (OrderFulfillment.countHeld(viewer, req.item) <= 0) {
                        viewer.sendSystemMessage(Component.literal("You have no " + req.item.getHoverName().getString() +
                                " to fulfill this.").withStyle(ChatFormatting.RED));
                    } else {
                        openConfirm(viewer, req);
                    }
                    return true;
                }
            }
            if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return true; }
            if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < requests.size()) { page++; updatePage(); return true; }
            if (slot == navRowStart + 1) {
                cycleSort();
                page = 0;
                updatePage();
                return true;
            }
            if (slot == navRowStart + 2) {
                viewer.closeContainer();
                startRequest(viewer, eco);
                return true;
            }
            if (slot == navRowStart + 6) {
                viewer.closeContainer();
                openClaims(viewer, eco);
                return true;
            }
            if (slot == navRowStart + 7) {
                viewer.closeContainer();
                HubUi.open(viewer);
                return true;
            }
            if (slot == navRowStart + 8) {
                if (query != null && !query.isBlank()) {
                    OrdersUi.open(viewer, eco, 0, null, sort, mineOnly);
                } else {
                    TextInputUi.openSearch(viewer, "Search Orders", (p, q) -> OrdersUi.open(p, eco, 0, q, sort, mineOnly));
                }
                return true;
            }
            return false;
        }

        private void openConfirm(ServerPlayer player, OrderRequest req) {
            MenuUiSupport.openMenu(player, "Confirm", (id, inv) -> new ConfirmMenu(id, inv, req, RequestMenu.this));
        }

        private void openRemove(ServerPlayer player, OrderRequest req) {
            MenuUiSupport.openMenu(player, "Remove", (id, inv) -> new RemoveMenu(id, inv, req, RequestMenu.this));
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            orders.removeListener(listener);
        }
    }

    private static class ConfirmMenu extends CompatMenu {
        private final OrderRequest request;
        private final RequestMenu parent;
        private final SimpleContainer container = new SimpleContainer(9);

        ConfirmMenu(int id, Inventory inv, OrderRequest req, RequestMenu parent) {
            super(MenuType.GENERIC_9x1, id);
            this.request = req;
            this.parent = parent;

            int give = Math.min(OrderFulfillment.countHeld(parent.viewer, req.item), req.amount);
            boolean complete = give >= req.amount;
            long payout = OrderFulfillment.payoutFor(req, give);

            container.setItem(MenuUiSupport.ROW_CONFIRM, MenuUiSupport.confirmButton(complete ? "Fulfill completely" : "Fulfill partially",
                    MenuUiSupport.labeledValue("Give", give + " of " + req.amount, MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Earn", EconomyCraft.formatMoney(payout), MenuUiSupport.LABEL_PRIMARY_COLOR)));

            ItemStack item = req.item.copy();
            var server = parent.viewer.level().getServer();
            String requesterName = MenuUiSupport.resolvePlayerName(server, req.requester);
            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.setCount(1);
            List<Component> itemLore = new ArrayList<>(List.of(
                    createRewardLore(req.price, tax),
                    MenuUiSupport.labeledValue("Amount", String.valueOf(req.amount), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Requester", requesterName, MenuUiSupport.LABEL_PRIMARY_COLOR)));
            if (MenuUiSupport.hasContainerContents(req.item)) {
                itemLore.add(MenuUiSupport.labeledValue("Ctrl+Q", "Preview contents", MenuUiSupport.LABEL_SECONDARY_COLOR));
            }
            item.set(DataComponents.LORE, new ItemLore(itemLore));
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
            if (kind == ClickKind.THROW && slot == MenuUiSupport.ROW_SUBJECT && MenuUiSupport.hasContainerContents(request.item)) {
                ContainerPreviewUi.open((ServerPlayer) player, request.item,
                        () -> parent.openConfirm((ServerPlayer) player, request));
                return true;
            }
            if (kind != ClickKind.PICKUP) return false;

            if (slot == MenuUiSupport.ROW_CONFIRM) {
                ServerPlayer serverPlayer = (ServerPlayer) player;
                var server = serverPlayer.level().getServer();

                OrderRequest current = parent.orders.getRequest(request.id);
                int give = current == null ? 0 : Math.min(OrderFulfillment.countHeld(serverPlayer, current.item), current.amount);
                if (current == null) {
                    serverPlayer.sendSystemMessage(Component.literal("Request no longer available").withStyle(ChatFormatting.RED));
                } else if (give <= 0) {
                    serverPlayer.sendSystemMessage(Component.literal("You have none to give").withStyle(ChatFormatting.RED));
                } else {
                    OrderFulfillment.Result result = OrderFulfillment.fulfill(parent.eco, serverPlayer, current.id, give);
                    switch (result.status()) {
                        case OK -> {
                            String requesterName = MenuUiSupport.resolvePlayerName(server, result.requester());
                            String extra = result.remaining() > 0 ? " (" + result.remaining() + " still wanted)" : "";
                            serverPlayer.sendSystemMessage(
                                    Component.literal("Fulfilled " + result.given() + "x " +
                                                    result.item().getHoverName().getString() + " (" + requesterName + ") and earned " +
                                                    EconomyCraft.formatMoney(result.payout()) + extra)
                                            .withStyle(ChatFormatting.GREEN));
                        }
                        case REQUESTER_CANT_PAY -> serverPlayer.sendSystemMessage(Component.literal("Requester can't pay").withStyle(ChatFormatting.RED));
                        case OWN_ORDER -> serverPlayer.sendSystemMessage(Component.literal("You cannot fulfill your own request").withStyle(ChatFormatting.RED));
                        default -> serverPlayer.sendSystemMessage(Component.literal("Request no longer available").withStyle(ChatFormatting.RED));
                    }
                }

                parent.updatePage();
                player.closeContainer();
                OrdersUi.open(serverPlayer, parent.eco, 0, parent.query, parent.sort, parent.mineOnly);
                return true;
            }

            if (slot == MenuUiSupport.ROW_CANCEL) {
                player.closeContainer();
                OrdersUi.open((ServerPlayer) player, parent.eco, 0, parent.query, parent.sort, parent.mineOnly);
                return true;
            }
            return false;
        }
    }

    private static class RemoveMenu extends CompatMenu {
        private final OrderRequest request;
        private final RequestMenu parent;
        private final SimpleContainer container = new SimpleContainer(9);

        RemoveMenu(int id, Inventory inv, OrderRequest req, RequestMenu parent) {
            super(MenuType.GENERIC_9x1, id);
            this.request = req;
            this.parent = parent;

            container.setItem(MenuUiSupport.ROW_CONFIRM, MenuUiSupport.confirmButton("Confirm"));

            ItemStack item = req.item.copy();
            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.set(DataComponents.LORE, new ItemLore(List.of(
                    createRewardLore(req.price, tax),
                    MenuUiSupport.labeledValue("Amount", String.valueOf(req.amount), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.line("This will remove the request", ChatFormatting.RED))));
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
                OrderRequest removed = parent.orders.removeRequest(request.id);
                if (removed != null) {
                    ((ServerPlayer) player).sendSystemMessage(Component.literal("Request removed").withStyle(ChatFormatting.GREEN));
                } else {
                    ((ServerPlayer) player).sendSystemMessage(Component.literal("Request no longer available").withStyle(ChatFormatting.RED));
                }
                player.closeContainer();
                OrdersUi.open((ServerPlayer) player, parent.eco, 0, parent.query, parent.sort, parent.mineOnly);
                return true;
            }
            if (slot == MenuUiSupport.ROW_CANCEL) {
                player.closeContainer();
                OrdersUi.open((ServerPlayer) player, parent.eco, 0, parent.query, parent.sort, parent.mineOnly);
                return true;
            }
            return false;
        }
    }

    private static class ClaimMenu extends CompatMenu {
        private final EconomyManager eco;
        private final UUID owner;
        private final SimpleContainer container = new SimpleContainer(54);
        private final List<ItemStack> items = new ArrayList<>();
        private int page;
        private final int navRowStart = 45;

        ClaimMenu(int id, Inventory inv, EconomyManager eco, UUID owner, int page) {
            super(MenuType.GENERIC_9x6, id);
            this.eco = eco;
            this.owner = owner;
            this.page = page;
            updatePage();
            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                int idx = i;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return idx < 45 && super.mayPickup(player); }
                });
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 6 * 18 + 14)) {
                this.addSlot(slot);
            }
        }

        private void updatePage() {
            items.clear();
            items.addAll(eco.getDeliveries().getDeliveries(owner));
            container.clearContent();
            int start = page * 45;
            int totalPages = MenuUiSupport.totalPages(items.size(), 45);
            for (int i = 0; i < 45; i++) {
                int index = start + i;
                if (index >= items.size()) break;
                container.setItem(i, items.get(index));
            }

            if (items.isEmpty()) {
                container.setItem(22, MenuUiSupport.button(Items.BOOK, "Nothing waiting", ChatFormatting.YELLOW,
                        MenuUiSupport.hint("Items you buy while your inventory"),
                        MenuUiSupport.hint("is full end up here.")));
            }

            if (page > 0) container.setItem(navRowStart + 3, MenuUiSupport.prevPageButton());
            if (start + 45 < items.size()) container.setItem(navRowStart + 5, MenuUiSupport.nextPageButton());

            ServerPlayer viewer = getViewer();
            String name = MenuUiSupport.resolvePlayerName(eco.getServer(), owner);
            container.setItem(navRowStart, MenuUiSupport.createBalanceItem(eco, owner, viewer, name));
            container.setItem(navRowStart + 4, MenuUiSupport.pageIndicator(page, totalPages));
            container.setItem(navRowStart + 8, MenuUiSupport.button(Items.NETHER_STAR, "Main menu", ChatFormatting.YELLOW));

            MenuUiSupport.fillFooter(container);
        }

        private ServerPlayer getViewer() {
            return eco.getServer().getPlayerList().getPlayer(owner);
        }

        private void removeStack(ItemStack stack) {
            eco.getDeliveries().removeDelivery(owner, stack);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 54) return false;

            if (kind == ClickKind.THROW && slot < 45) {
                Slot s = this.slots.get(slot);
                if (s.hasItem() && MenuUiSupport.hasContainerContents(s.getItem())) {
                    ServerPlayer sp = (ServerPlayer) player;
                    ContainerPreviewUi.open(sp, s.getItem(), () -> OrdersUi.openClaims(sp, eco, page));
                }
                return true;
            }
            if (kind == ClickKind.PICKUP) {
                if (slot < 45) {
                    Slot s = this.slots.get(slot);
                    if (s.hasItem()) {
                        ItemStack stack = s.getItem();
                        ItemStack copy = stack.copy();
                        if (player.getInventory().add(copy)) {
                            removeStack(stack);
                            updatePage();
                        }
                    }
                    return true;
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return true; }
                if (slot == navRowStart + 5 && (page + 1) * 45 < items.size()) { page++; updatePage(); return true; }
                if (slot == navRowStart + 8) {
                    player.closeContainer();
                    HubUi.open((ServerPlayer) player);
                    return true;
                }
                return true;
            }
            return kind != ClickKind.QUICK_MOVE;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int idx) {
            Slot slot = this.slots.get(idx);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();
            if (idx < 45) {
                if (player.getInventory().add(copy)) {
                    removeStack(stack);
                    updatePage();
                    return copy;
                }
                return ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }
    }
}
