package com.reazip.economycraft.orders;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class OrdersUi {
    private OrdersUi() {}

    private static final ChatFormatting LABEL_PRIMARY_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting LABEL_SECONDARY_COLOR = ChatFormatting.AQUA;
    private static final ChatFormatting VALUE_COLOR = ChatFormatting.DARK_PURPLE;
    private static final ChatFormatting BALANCE_NAME_COLOR = ChatFormatting.YELLOW;
    private static final ChatFormatting BALANCE_LABEL_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting BALANCE_VALUE_COLOR = ChatFormatting.DARK_PURPLE;

    public static void open(ServerPlayer player, EconomyManager eco) {
        Component title = Component.literal("Orders");

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new RequestMenu(id, inv, eco.getOrders(), eco, player);
            }
        });
    }

    private static Component createRewardLore(long reward, long tax) {
        StringBuilder value = new StringBuilder(EconomyCraft.formatMoney(reward));
        if (tax > 0) {
            value.append(" (-").append(EconomyCraft.formatMoney(tax)).append(" tax)");
        }
        return labeledValue("Reward", value.toString(), LABEL_PRIMARY_COLOR);
    }

    private static ItemStack createBalanceItem(EconomyManager eco, UUID playerId, @Nullable ServerPlayer player, @Nullable String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        var profile = player != null
                ? net.minecraft.world.item.component.ResolvableProfile.createResolved(player.getGameProfile())
                : net.minecraft.world.item.component.ResolvableProfile.createUnresolved(name != null && !name.isBlank() ? name : playerId.toString());
        head.set(DataComponents.PROFILE, profile);
        long balance = eco.getBalance(playerId, true);
        String displayName = name != null ? name : playerId.toString();
        head.set(DataComponents.CUSTOM_NAME, Component.literal(displayName).withStyle(s -> s.withItalic(false).withColor(BALANCE_NAME_COLOR)));
        head.set(DataComponents.LORE, new ItemLore(List.of(balanceLore(balance))));
        return head;
    }

    private static Component balanceLore(long balance) {
        return Component.literal("Balance: ")
                .withStyle(s -> s.withItalic(false).withColor(BALANCE_LABEL_COLOR))
                .append(Component.literal(EconomyCraft.formatMoney(balance))
                        .withStyle(s -> s.withItalic(false).withColor(BALANCE_VALUE_COLOR)));
    }

    private static Component labeledValue(String label, String value, ChatFormatting labelColor) {
        return Component.literal(label + ": ")
                .withStyle(s -> s.withItalic(false).withColor(labelColor))
                .append(Component.literal(value)
                        .withStyle(s -> s.withItalic(false).withColor(VALUE_COLOR)));
    }

    public static void openClaims(ServerPlayer player, EconomyManager eco) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.literal("Deliveries"); }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ClaimMenu(id, inv, eco, player.getUUID());
            }
        });
    }

    private static class RequestMenu extends AbstractContainerMenu {
        private final OrderManager orders;
        private final EconomyManager eco;
        private final ServerPlayer viewer;
        private List<OrderRequest> requests = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final int navRowStart = 45;
        private final Runnable listener = this::updatePage;

        RequestMenu(int id, Inventory inv, OrderManager orders, EconomyManager eco, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.orders = orders;
            this.eco = eco;
            this.viewer = viewer;
            updatePage();
            orders.addListener(listener);
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
            requests = new ArrayList<>(orders.getRequests());
            container.clearContent();
            int start = page * 45;
            int totalPages = (int) Math.ceil(requests.size() / 45.0);

            var server = viewer.level().getServer();

            for (int i = 0; i < 45; i++) {
                int index = start + i;
                if (index >= requests.size()) break;

                OrderRequest r = requests.get(index);
                ItemStack display = r.item.copy();

                String reqName;
                ServerPlayer requesterPlayer = server.getPlayerList().getPlayer(r.requester);
                if (requesterPlayer != null) {
                    reqName = IdentityCompat.of(requesterPlayer).name();
                } else {
                    reqName = EconomyCraft.getManager(server).getBestName(r.requester);
                }

                long tax = Math.round(r.price * EconomyConfig.get().taxRate);
                display.set(net.minecraft.core.component.DataComponents.LORE,
                        new net.minecraft.world.item.component.ItemLore(List.of(
                                createRewardLore(r.price, tax),
                                labeledValue("Amount", String.valueOf(r.amount), LABEL_PRIMARY_COLOR),
                                labeledValue("Requester", reqName, LABEL_SECONDARY_COLOR)
                        )));
                display.setCount(1);
                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 2, prev);
            }

            if (start + 45 < requests.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 6, next);
            }

            ItemStack balance = createBalanceItem(eco, viewer.getUUID(), viewer, IdentityCompat.of(viewer).name());
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < requests.size()) {
                        OrderRequest req = requests.get(index);
                        if (req.requester.equals(player.getUUID())) {
                            openRemove((ServerPlayer) player, req);
                        } else {
                            openConfirm((ServerPlayer) player, req);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 2 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 6 && (page + 1) * 45 < requests.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
        }

        private boolean hasItems(ServerPlayer player, ItemStack proto, int amount) {
            int total = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(proto.getItem())) total += s.getCount();
            }
            return total >= amount;
        }

        private void removeItems(ServerPlayer player, ItemStack proto, int amount) {
            int remaining = amount;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(proto.getItem())) {
                    int take = Math.min(s.getCount(), remaining);
                    s.shrink(take);
                    remaining -= take;
                    if (remaining <= 0) return;
                }
            }
        }

        private void openConfirm(ServerPlayer player, OrderRequest req) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() { return Component.literal("Confirm"); }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new ConfirmMenu(id, inv, req, RequestMenu.this);
                }
            });
        }

        private void openRemove(ServerPlayer player, OrderRequest req) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() { return Component.literal("Remove"); }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new RemoveMenu(id, inv, req, RequestMenu.this);
                }
            });
        }

        @Override public boolean stillValid(Player player) { return true; }

        @Override
        public void removed(Player player) {
            super.removed(player);
            orders.removeListener(listener);
        }

        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }

    private static class ConfirmMenu extends AbstractContainerMenu {
        private final OrderRequest request;
        private final RequestMenu parent;
        private final SimpleContainer container = new SimpleContainer(9);

        ConfirmMenu(int id, Inventory inv, OrderRequest req, RequestMenu parent) {
            super(MenuType.GENERIC_9x1, id);
            this.request = req;
            this.parent = parent;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Confirm").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            ItemStack item = req.item.copy();
            var server = parent.viewer.level().getServer();

            String requesterName;
            ServerPlayer requesterPlayer = server.getPlayerList().getPlayer(req.requester);
            if (requesterPlayer != null) {
                requesterName = IdentityCompat.of(requesterPlayer).name();
            } else {
                requesterName = EconomyCraft.getManager(server).getBestName(req.requester);
            }

            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.core.component.DataComponents.LORE,
                    new net.minecraft.world.item.component.ItemLore(List.of(
                            createRewardLore(req.price, tax),
                            labeledValue("Amount", String.valueOf(req.amount), LABEL_PRIMARY_COLOR),
                            labeledValue("Requester", requesterName, LABEL_SECONDARY_COLOR)
                    )));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Cancel").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override public boolean mayPickup(Player p) { return false; }
                });
            }

            int y = 40;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        @Override
        public void clicked(int slot, int drag, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
                    OrderRequest current = parent.orders.getRequest(request.id);
                    ServerPlayer serverPlayer = (ServerPlayer) player;
                    var server = serverPlayer.level().getServer();

                    if (current == null) {
                        serverPlayer.sendSystemMessage(Component.literal("Request no longer available").withStyle(ChatFormatting.RED));
                    } else if (!parent.hasItems(serverPlayer, current.item, current.amount)) {
                        serverPlayer.sendSystemMessage(Component.literal("Not enough items").withStyle(ChatFormatting.RED));
                    } else {
                        long cost = current.price;
                        long bal = parent.eco.getBalance(current.requester, true);
                        if (bal < cost) {
                            serverPlayer.sendSystemMessage(Component.literal("Requester can't pay").withStyle(ChatFormatting.RED));
                        } else {
                            parent.removeItems(serverPlayer, current.item.copy(), current.amount);
                            long tax = Math.round(cost * EconomyConfig.get().taxRate);
                            parent.eco.removeMoney(current.requester, cost);
                            parent.eco.addMoney(player.getUUID(), cost - tax);
                            parent.orders.removeRequest(current.id);

                            int remaining = current.amount;
                            while (remaining > 0) {
                                int c = Math.min(current.item.getMaxStackSize(), remaining);
                                parent.orders.addDelivery(current.requester, new ItemStack(current.item.getItem(), c));
                                remaining -= c;
                            }

                            String requesterName;
                            ServerPlayer requesterPlayer = server.getPlayerList().getPlayer(current.requester);
                            if (requesterPlayer != null) {
                                requesterName = IdentityCompat.of(requesterPlayer).name();
                            } else {
                                requesterName = parent.eco.getBestName(current.requester);
                            }

                            serverPlayer.sendSystemMessage(
                                    Component.literal("Fulfilled request for " + current.amount + "x " +
                                                    current.item.getHoverName().getString() + " (" + requesterName + ")" +
                                                    " and earned " + EconomyCraft.formatMoney(cost - tax))
                                            .withStyle(ChatFormatting.GREEN)
                            );

                            if (requesterPlayer != null) {
                                ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                                if (ev != null) {
                                    Component msg = Component.literal("Your request for " + current.amount + "x " +
                                                    current.item.getHoverName().getString() +
                                                    " has been fulfilled: ")
                                            .withStyle(ChatFormatting.YELLOW)
                                            .append(Component.literal("[Claim]")
                                                    .withStyle(s -> s.withUnderlined(true)
                                                            .withColor(ChatFormatting.GREEN)
                                                            .withClickEvent(ev)));
                                    requesterPlayer.sendSystemMessage(msg);
                                } else {
                                    ChatCompat.sendRunCommandTellraw(
                                            requesterPlayer,
                                            "Your request for " + current.amount + "x " + current.item.getHoverName().getString() + " has been fulfilled: ",
                                            "[Claim]",
                                            "/eco orders claim"
                                    );
                                }
                            }

                            parent.requests.removeIf(r -> r.id == current.id);
                            parent.updatePage();
                        }
                    }
                    player.closeContainer();
                    OrdersUi.open(serverPlayer, parent.eco);
                    return;
                }

                if (slot == 6) {
                    player.closeContainer();
                    OrdersUi.open((ServerPlayer) player, parent.eco);
                    return;
                }
            }
            super.clicked(slot, drag, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }

    private static class RemoveMenu extends AbstractContainerMenu {
        private final OrderRequest request;
        private final RequestMenu parent;
        private final SimpleContainer container = new SimpleContainer(9);

        RemoveMenu(int id, Inventory inv, OrderRequest req, RequestMenu parent) {
            super(MenuType.GENERIC_9x1, id);
            this.request = req;
            this.parent = parent;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Confirm").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            ItemStack item = req.item.copy();
            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                    createRewardLore(req.price, tax),
                    labeledValue("Amount", String.valueOf(req.amount), LABEL_PRIMARY_COLOR),
                    Component.literal("This will remove the request").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED)))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Cancel").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) { @Override public boolean mayPickup(Player p) { return false; }});
            }

            int y = 40;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        @Override
        public void clicked(int slot, int drag, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
                    OrderRequest removed = parent.orders.removeRequest(request.id);
                    if (removed != null) {
                        ((ServerPlayer) player).sendSystemMessage(Component.literal("Request removed").withStyle(ChatFormatting.GREEN));
                    } else {
                        ((ServerPlayer) player).sendSystemMessage(Component.literal("Request no longer available").withStyle(ChatFormatting.RED));
                    }
                    player.closeContainer();
                    OrdersUi.open((ServerPlayer) player, parent.eco);
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
                    OrdersUi.open((ServerPlayer) player, parent.eco);
                    return;
                }
            }
            super.clicked(slot, drag, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }

    private static class ClaimMenu extends AbstractContainerMenu {
        private final EconomyManager eco;
        private final UUID owner;
        private final List<ItemStack> orderItems;
        private final List<ItemStack> shopItems;
        private final SimpleContainer container = new SimpleContainer(54);
        private final List<ItemStack> items = new ArrayList<>();
        private int page;
        private final int navRowStart = 45;

        ClaimMenu(int id, Inventory inv, EconomyManager eco, UUID owner) {
            super(MenuType.GENERIC_9x6, id);
            this.eco = eco;
            this.owner = owner;
            this.orderItems = eco.getOrders().getDeliveries(owner);
            this.shopItems = eco.getShop().getDeliveries(owner);
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
            items.clear();
            items.addAll(orderItems);
            items.addAll(shopItems);
            container.clearContent();
            int start = page * 45;
            int totalPages = (int)Math.ceil(items.size() / 45.0);
            for (int i = 0; i < 45; i++) {
                int index = start + i;
                if (index >= items.size()) break;
                container.setItem(i, items.get(index));
            }
            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 2, prev);
            }
            if (start + 45 < items.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 6, next);
            }
            String name = null;
            ServerPlayer viewer = getViewer();
            if (viewer != null) {
                name = IdentityCompat.of(viewer).name();
            } else {
                name = eco.getBestName(owner);
            }
            ItemStack balance = createBalanceItem(eco, owner, viewer, name);
            container.setItem(navRowStart, balance);
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        private ServerPlayer getViewer() {
            return eco.getServer().getPlayerList().getPlayer(owner);
        }

        private void removeStack(ItemStack stack) {
            eco.getOrders().removeDelivery(owner, stack);
            eco.getShop().removeDelivery(owner, stack);
        }

        @Override public boolean stillValid(Player player) { return true; }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
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
                    return;
                }
                if (slot == navRowStart + 2 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 6 && (page + 1) * 45 < items.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
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
