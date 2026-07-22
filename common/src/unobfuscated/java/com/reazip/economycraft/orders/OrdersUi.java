package com.reazip.economycraft.orders;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ContainerPreviewUi;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ItemsCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
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

    public static void open(ServerPlayer player, EconomyManager eco) {
        open(player, eco, 0);
    }

    private static void open(ServerPlayer player, EconomyManager eco, int page) {
        Component title = Component.literal("Orders");
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new RequestMenu(id, inv, eco.getOrders(), eco, player, page);
            }
        });
    }

    private static Component createRewardLore(long reward, long tax) {
        StringBuilder value = new StringBuilder(EconomyCraft.formatMoney(reward));
        if (tax > 0) {
            value.append(" (-").append(EconomyCraft.formatMoney(tax)).append(" tax)");
        }
        return MenuUiSupport.labeledValue("Reward", value.toString(), MenuUiSupport.LABEL_PRIMARY_COLOR);
    }

    public static void openClaims(ServerPlayer player, EconomyManager eco) {
        openClaims(player, eco, 0);
    }

    private static void openClaims(ServerPlayer player, EconomyManager eco, int page) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.literal("Deliveries"); }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ClaimMenu(id, inv, eco, player.getUUID(), page);
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

        RequestMenu(int id, Inventory inv, OrderManager orders, EconomyManager eco, ServerPlayer viewer, int page) {
            super(MenuType.GENERIC_9x6, id);
            this.orders = orders;
            this.eco = eco;
            this.viewer = viewer;
            this.page = page;
            updatePage();
            orders.addListener(listener);
            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 54)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 6 * 18 + 14)) {
                this.addSlot(slot);
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

                String reqName = MenuUiSupport.resolvePlayerName(server, r.requester);

                long tax = Math.round(r.price * EconomyConfig.get().taxRate);
                List<Component> lore = new ArrayList<>(List.of(
                        createRewardLore(r.price, tax),
                        MenuUiSupport.labeledValue("Amount", String.valueOf(r.amount), MenuUiSupport.LABEL_PRIMARY_COLOR),
                        MenuUiSupport.labeledValue("Requester", reqName, MenuUiSupport.LABEL_SECONDARY_COLOR)
                ));
                if (MenuUiSupport.hasContainerContents(r.item)) {
                    lore.add(MenuUiSupport.labeledValue("Ctrl+Q", "Preview contents", MenuUiSupport.LABEL_SECONDARY_COLOR));
                }
                display.set(DataComponents.LORE, new ItemLore(lore));
                display.setCount(1);
                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 2, prev);
            }

            if (start + 45 < requests.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 6, next);
            }

            ItemStack balance = MenuUiSupport.createBalanceItem(eco, viewer.getUUID(), viewer, IdentityCompat.of(viewer).name());
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ContainerInput type, Player player) {
            if (type == ContainerInput.THROW && slot >= 0 && slot < 45) {
                int index = page * 45 + slot;
                if (index < requests.size() && MenuUiSupport.hasContainerContents(requests.get(index).item)) {
                    ContainerPreviewUi.open((ServerPlayer) player, requests.get(index).item, () -> OrdersUi.open((ServerPlayer) player, eco, page));
                }
                return;
            }
            if (type == ContainerInput.PICKUP) {
                if (slot >= 0 && slot < 45) {
                    int index = page * 45 + slot;
                    if (index < requests.size()) {
                        OrderRequest req = requests.get(index);
                        ServerPlayer sp = (ServerPlayer) player;
                        if (req.requester.equals(sp.getUUID())) {
                            openRemove(sp, req);
                        } else if (OrderFulfillment.countHeld(sp, req.item) <= 0) {
                            sp.sendSystemMessage(Component.literal("You have no " + req.item.getHoverName().getString() +
                                    " to fulfill this.").withStyle(ChatFormatting.RED));
                        } else {
                            openConfirm(sp, req);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 2 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 6 && (page + 1) * 45 < requests.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
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

            int give = Math.min(OrderFulfillment.countHeld(parent.viewer, req.item), req.amount);
            boolean complete = give >= req.amount;
            long payout = OrderFulfillment.payoutFor(req, give);

            ItemStack confirm = new ItemStack(ItemsCompat.limeStainedGlassPane());
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal(complete ? "Fulfill completely" : "Fulfill partially")
                            .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            confirm.set(DataComponents.LORE, new ItemLore(List.of(
                    MenuUiSupport.labeledValue("Give", give + " of " + req.amount, MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Earn", EconomyCraft.formatMoney(payout), MenuUiSupport.LABEL_PRIMARY_COLOR))));
            container.setItem(2, confirm);

            ItemStack item = req.item.copy();
            var server = parent.viewer.level().getServer();
            String requesterName = MenuUiSupport.resolvePlayerName(server, req.requester);
            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.setCount(1);
            List<Component> itemLore = new ArrayList<>(List.of(
                    createRewardLore(req.price, tax),
                    MenuUiSupport.labeledValue("Amount", String.valueOf(req.amount), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Requester", requesterName, MenuUiSupport.LABEL_SECONDARY_COLOR)));
            if (MenuUiSupport.hasContainerContents(req.item)) {
                itemLore.add(MenuUiSupport.labeledValue("Ctrl+Q", "Preview contents", MenuUiSupport.LABEL_SECONDARY_COLOR));
            }
            item.set(DataComponents.LORE, new ItemLore(itemLore));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(ItemsCompat.redStainedGlassPane());
            cancel.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Cancel").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (Slot slot : MenuUiSupport.confirmRowSlots(container)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 40)) {
                this.addSlot(slot);
            }
        }

        @Override
        public void clicked(int slot, int drag, ContainerInput type, Player player) {
            if (type == ContainerInput.THROW && slot == 4 && MenuUiSupport.hasContainerContents(request.item)) {
                ContainerPreviewUi.open((ServerPlayer) player, request.item, () -> parent.openConfirm((ServerPlayer) player, request));
                return;
            }
            if (type == ContainerInput.PICKUP) {
                if (slot == 2) {
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

            ItemStack confirm = new ItemStack(ItemsCompat.limeStainedGlassPane());
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Confirm").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            ItemStack item = req.item.copy();
            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.set(DataComponents.LORE, new ItemLore(List.of(
                    createRewardLore(req.price, tax),
                    MenuUiSupport.labeledValue("Amount", String.valueOf(req.amount), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    Component.literal("This will remove the request").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED)))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(ItemsCompat.redStainedGlassPane());
            cancel.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Cancel").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (Slot slot : MenuUiSupport.confirmRowSlots(container)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 40)) {
                this.addSlot(slot);
            }
        }

        @Override
        public void clicked(int slot, int drag, ContainerInput type, Player player) {
            if (type == ContainerInput.PICKUP) {
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
            ServerPlayer viewer = getViewer();
            String name = MenuUiSupport.resolvePlayerName(eco.getServer(), owner);
            ItemStack balance = MenuUiSupport.createBalanceItem(eco, owner, viewer, name);
            container.setItem(navRowStart, balance);
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        private ServerPlayer getViewer() {
            return eco.getServer().getPlayerList().getPlayer(owner);
        }

        private void removeStack(ItemStack stack) {
            eco.getDeliveries().removeDelivery(owner, stack);
        }

        @Override public boolean stillValid(Player player) { return true; }

        @Override
        public void clicked(int slot, int dragType, ContainerInput type, Player player) {
            if (slot >= 0 && slot < 54) {
                if (type == ContainerInput.THROW && slot < 45) {
                    Slot s = this.slots.get(slot);
                    if (s.hasItem() && MenuUiSupport.hasContainerContents(s.getItem())) {
                        ServerPlayer sp = (ServerPlayer) player;
                        ContainerPreviewUi.open(sp, s.getItem(), () -> OrdersUi.openClaims(sp, eco, page));
                    }
                    return;
                }
                if (type == ContainerInput.PICKUP) {
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
                    return;
                }
                if (type == ContainerInput.QUICK_MOVE) {
                    super.clicked(slot, dragType, type, player);
                }
                return;
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
