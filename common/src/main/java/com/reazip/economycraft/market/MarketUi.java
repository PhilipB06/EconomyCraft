package com.reazip.economycraft.market;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MarketUi {
    private MarketUi() {}

    public static void openRequests(ServerPlayer player, EconomyManager eco) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Market");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new RequestMenu(id, inv, eco.getMarket(), eco, player);
            }
        });
    }

    public static void openClaims(ServerPlayer player, MarketManager market) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.literal("Deliveries"); }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ClaimMenu(id, inv, market, player.getUUID());
            }
        });
    }

    private static class RequestMenu extends AbstractContainerMenu {
        private final MarketManager market;
        private final EconomyManager eco;
        private final ServerPlayer viewer;
        private List<MarketRequest> requests = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final Runnable listener = this::updatePage;

        RequestMenu(int id, Inventory inv, MarketManager market, EconomyManager eco, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.market = market;
            this.eco = eco;
            this.viewer = viewer;
            updatePage();
            market.addListener(listener);
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
            requests = new ArrayList<>(market.getRequests());
            container.clearContent();
            int start = page * 45;
            int totalPages = (int)Math.ceil(requests.size() / 45.0);
            for (int i = 0; i < 45; i++) {
                int index = start + i;
                if (index >= requests.size()) break;
                MarketRequest r = requests.get(index);
                ItemStack display = r.item.copy();
                String reqName = viewer.getServer().getProfileCache().get(r.requester).map(p -> p.getName()).orElse(r.requester.toString());
                long tax = Math.round(r.price * EconomyConfig.get().taxRate);
                display.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        Component.literal("Reward: " + EconomyCraft.formatMoney(r.price)),
                        Component.literal("Requester: " + reqName),
                        Component.literal("Amount: " + r.item.getCount()),
                        Component.literal("Tax: " + EconomyCraft.formatMoney(tax))
                )));
                display.setCount(Math.min(r.item.getCount(), display.getMaxStackSize()));
                container.setItem(i, display);
            }
            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Previous page"));
                container.setItem(45, prev);
            }
            if (start + 45 < requests.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Next page"));
                container.setItem(53, next);
            }
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)));
            container.setItem(49, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < requests.size()) {
                        MarketRequest req = requests.get(index);
                        if (req.requester.equals(player.getUUID())) {
                            openRemove((ServerPlayer) player, req);
                        } else {
                            openConfirm((ServerPlayer) player, req);
                        }
                        return;
                    }
                }
                if (slot == 45 && page > 0) { page--; updatePage(); return; }
                if (slot == 53 && (page + 1) * 45 < requests.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
        }

        private boolean hasItems(ServerPlayer player, ItemStack wanted) {
            int total = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(wanted.getItem())) total += s.getCount();
            }
            return total >= wanted.getCount();
        }

        private void removeItems(ServerPlayer player, ItemStack wanted) {
            int remaining = wanted.getCount();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(wanted.getItem())) {
                    int take = Math.min(s.getCount(), remaining);
                    s.shrink(take);
                    remaining -= take;
                    if (remaining <= 0) return;
                }
            }
        }

        private void openConfirm(ServerPlayer player, MarketRequest req) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() { return Component.literal("Confirm"); }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new ConfirmMenu(id, inv, req, RequestMenu.this);
                }
            });
        }

        private void openRemove(ServerPlayer player, MarketRequest req) {
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
            market.removeListener(listener);
        }

        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }

    private static class ConfirmMenu extends AbstractContainerMenu {
        private final MarketRequest request;
        private final RequestMenu parent;
        private final SimpleContainer container = new SimpleContainer(9);

        ConfirmMenu(int id, Inventory inv, MarketRequest req, RequestMenu parent) {
            super(MenuType.GENERIC_9x1, id);
            this.request = req;
            this.parent = parent;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Confirm"));
            container.setItem(2, confirm);

            ItemStack item = req.item.copy();
            String name = parent.viewer.getServer().getProfileCache().get(req.requester).map(p -> p.getName()).orElse(req.requester.toString());
            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                    Component.literal("Reward: " + EconomyCraft.formatMoney(req.price)),
                    Component.literal("Requester: " + name),
                    Component.literal("Amount: " + req.item.getCount()),
                    Component.literal("Tax: " + EconomyCraft.formatMoney(tax))
            )));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Cancel"));
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
                    MarketRequest current = parent.market.getRequest(request.id);
                    if (current == null) {
                        ((ServerPlayer) player).sendSystemMessage(Component.literal("Request no longer available"));
                    } else if (!parent.hasItems((ServerPlayer) player, current.item)) {
                        ((ServerPlayer) player).sendSystemMessage(Component.literal("Not enough items"));
                    } else {
                        parent.removeItems((ServerPlayer) player, current.item.copy());
                        long cost = current.price;
                        long tax = Math.round(cost * EconomyConfig.get().taxRate);
                        long bal = parent.eco.getBalance(current.requester);
                        parent.eco.setMoney(current.requester, bal - cost);
                        parent.eco.addMoney(player.getUUID(), cost - tax);
                        parent.market.removeRequest(current.id);
                        parent.market.addDelivery(current.requester, current.item.copy());
                        ((ServerPlayer) player).sendSystemMessage(Component.literal("Fulfilled request"));
                        var requesterPlayer = parent.viewer.getServer().getPlayerList().getPlayer(current.requester);
                        if (requesterPlayer != null) {
                            ItemStack stack = current.item;
                            Component msg = Component.literal("Your request for " + stack.getCount() + "x " + stack.getHoverName().getString() + " has been fulfilled. ")
                                    .append(Component.literal("[Claim]")
                                            .withStyle(s -> s.withUnderlined(true)
                                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/eco market claim"))));
                            requesterPlayer.sendSystemMessage(msg);
                        }
                        parent.requests.removeIf(r -> r.id == current.id);
                        parent.updatePage();
                    }
                    player.closeContainer();
                    MarketUi.openRequests((ServerPlayer) player, parent.eco);
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
                    MarketUi.openRequests((ServerPlayer) player, parent.eco);
                    return;
                }
            }
            super.clicked(slot, drag, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }

    private static class RemoveMenu extends AbstractContainerMenu {
        private final MarketRequest request;
        private final RequestMenu parent;
        private final SimpleContainer container = new SimpleContainer(9);

        RemoveMenu(int id, Inventory inv, MarketRequest req, RequestMenu parent) {
            super(MenuType.GENERIC_9x1, id);
            this.request = req;
            this.parent = parent;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Confirm"));
            container.setItem(2, confirm);

            ItemStack item = req.item.copy();
            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                    Component.literal("Reward: " + EconomyCraft.formatMoney(req.price)),
                    Component.literal("Amount: " + req.item.getCount()),
                    Component.literal("Tax: " + EconomyCraft.formatMoney(tax)),
                    Component.literal("This will remove the request"))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Cancel"));
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
                    MarketRequest removed = parent.market.removeRequest(request.id);
                    if (removed != null) {
                        ((ServerPlayer) player).sendSystemMessage(Component.literal("Request removed"));
                    } else {
                        ((ServerPlayer) player).sendSystemMessage(Component.literal("Request no longer available"));
                    }
                    player.closeContainer();
                    MarketUi.openRequests((ServerPlayer) player, parent.eco);
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
                    MarketUi.openRequests((ServerPlayer) player, parent.eco);
                    return;
                }
            }
            super.clicked(slot, drag, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }

    private static class ClaimMenu extends AbstractContainerMenu {
        private final MarketManager market;
        private final UUID owner;
        private final SimpleContainer container = new SimpleContainer(54);

        ClaimMenu(int id, Inventory inv, MarketManager market, UUID owner) {
            super(MenuType.GENERIC_9x6, id);
            this.market = market;
            this.owner = owner;
            List<ItemStack> items = market.claimDeliveries(owner);
            if (items != null) {
                for (int i = 0; i < items.size() && i < 54; i++) {
                    container.setItem(i, items.get(i));
                }
            }
            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
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

        @Override
        public void removed(Player player) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    if (!player.getInventory().add(s)) {
                        ((ServerPlayer)player).drop(s, false);
                    }
                }
            }
            super.removed(player);
        }

        @Override public boolean stillValid(Player player) { return true; }

        @Override
        public ItemStack quickMoveStack(Player player, int idx) {
            Slot slot = this.slots.get(idx);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();
            if (idx < 54) {
                if (player.getInventory().add(stack)) {
                    slot.set(ItemStack.EMPTY);
                    return copy;
                }
                return ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }
    }
}
