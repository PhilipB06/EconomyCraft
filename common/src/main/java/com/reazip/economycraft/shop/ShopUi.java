package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
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

public final class ShopUi {
    private ShopUi() {}

    public static void open(ServerPlayer player, ShopManager shop) {
        Component title = EconomyCraft.createBalanceTitle("Shop", player);

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ShopMenu(id, inv, shop, player);
            }
        });
    }

    static void openConfirm(ServerPlayer player, ShopManager shop, ShopListing listing) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Confirm");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ConfirmMenu(id, inv, shop, listing, player);
            }
        });
    }

    private static void openRemove(ServerPlayer player, ShopManager shop, ShopListing listing) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Remove");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new RemoveMenu(id, inv, shop, listing, player);
            }
        });
    }

    private static class ShopMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private List<ShopListing> listings = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final Runnable listener = this::updatePage;

        ShopMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.viewer = viewer;
            updatePage();
            shop.addListener(listener);
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
            listings = new ArrayList<>(shop.getListings());
            container.clearContent();
            int start = page * 45;
            int totalPages = (int) Math.ceil(listings.size() / 45.0);

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= listings.size()) break;

                ShopListing l = listings.get(idx);
                ItemStack display = l.item.copy();

                String sellerName;
                ServerPlayer sellerPlayer = viewer.level().getServer().getPlayerList().getPlayer(l.seller);
                if (sellerPlayer != null) {
                    sellerName = IdentityCompat.of(sellerPlayer).name();
                } else {
                    sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(l.seller);
                }

                long tax = Math.round(l.price * EconomyConfig.get().taxRate);
                display.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                        Component.literal("Price: " + EconomyCraft.formatMoney(l.price) + " (+" + EconomyCraft.formatMoney(tax) + " tax)"),
                        Component.literal("Seller: " + sellerName))));
                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Previous page"));
                container.setItem(45, prev);
            }

            if (start + 45 < listings.size()) {
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
                    if (index < listings.size()) {
                        ShopListing listing = listings.get(index);
                        if (listing.seller.equals(player.getUUID())) {
                            openRemove((ServerPlayer) player, shop, listing);
                        } else {
                            ShopUi.openConfirm((ServerPlayer) player, shop, listing);
                        }
                        return;
                    }
                }
                if (slot == 45 && page > 0) { page--; updatePage(); return; }
                if (slot == 53 && (page + 1) * 45 < listings.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public void removed(Player player) {
            super.removed(player);
            shop.removeListener(listener);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static class ConfirmMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ShopListing listing;
        private final ServerPlayer viewer;
        private final SimpleContainer container = new SimpleContainer(9);

        ConfirmMenu(int id, Inventory inv, ShopManager shop, ShopListing listing, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x1, id);
            this.shop = shop;
            this.listing = listing;
            this.viewer = viewer;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Confirm"));
            container.setItem(2, confirm);

            String sellerName;
            ServerPlayer sellerPlayer = viewer.level().getServer().getPlayerList().getPlayer(listing.seller);
            if (sellerPlayer != null) {
                sellerName = IdentityCompat.of(sellerPlayer).name();
            } else {
                sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(listing.seller);
            }

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                    Component.literal("Price: " + EconomyCraft.formatMoney(listing.price) + " (+" + EconomyCraft.formatMoney(tax) + " tax)"),
                    Component.literal("Seller: " + sellerName))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Cancel"));
            container.setItem(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override
                    public boolean mayPickup(Player player) { return false; }
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
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
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
                        long bal = eco.getBalance(player.getUUID(), true);

                        if (bal < total) {
                            sp.sendSystemMessage(Component.literal("Not enough balance").withStyle(ChatFormatting.RED));
                        } else {
                            eco.removeMoney(player.getUUID(), total);
                            eco.addMoney(current.seller, cost);
                            ShopListing sold = shop.removeListing(current.id);
                            if (sold != null) {
                                shop.notifySellerSale(sold, sp);
                            }
                            ItemStack stack = current.item.copy();
                            int count = stack.getCount();
                            Component name = stack.getHoverName();

                            String sellerName;
                            ServerPlayer sellerPlayer = server.getPlayerList().getPlayer(current.seller);
                            if (sellerPlayer != null) {
                                sellerName = IdentityCompat.of(sellerPlayer).name();
                            } else {
                                sellerName = eco.getBestName(current.seller);
                            }

                            if (!player.getInventory().add(stack)) {
                                shop.addDelivery(player.getUUID(), stack);

                                ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                                if (ev != null) {
                                    Component msg = Component.literal("Item stored: ")
                                            .withStyle(ChatFormatting.YELLOW)
                                            .append(Component.literal("[Claim]")
                                                    .withStyle(s -> s.withUnderlined(true)
                                                            .withColor(ChatFormatting.GREEN)
                                                            .withClickEvent(ev)));
                                    sp.sendSystemMessage(msg);
                                } else {
                                    ChatCompat.sendRunCommandTellraw(sp, "Item stored: ", "[Claim]", "/eco orders claim");
                                }
                            } else {
                                sp.sendSystemMessage(
                                        Component.literal("Purchased " + count + "x " + name.getString() + " from " + sellerName +
                                                        " for " + EconomyCraft.formatMoney(total))
                                                .withStyle(ChatFormatting.GREEN)
                                );
                            }
                        }
                    }
                    player.closeContainer();
                    ShopUi.open(sp, shop);
                    return;
                }

                if (slot == 6) {
                    player.closeContainer();
                    ShopUi.open((ServerPlayer) player, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static class RemoveMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ShopListing listing;
        private final ServerPlayer viewer;
        private final SimpleContainer container = new SimpleContainer(9);

        RemoveMenu(int id, Inventory inv, ShopManager shop, ShopListing listing, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x1, id);
            this.shop = shop;
            this.listing = listing;
            this.viewer = viewer;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Confirm"));
            container.setItem(2, confirm);

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                    Component.literal("Price: " + EconomyCraft.formatMoney(listing.price) + " (+" + EconomyCraft.formatMoney(tax) + " tax)"),
                    Component.literal("Seller: you"),
                    Component.literal("This will remove the listing"))));
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
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
                    ShopListing removed = shop.removeListing(listing.id);
                    if (removed != null) {
                        ItemStack stack = removed.item.copy();
                        if (!player.getInventory().add(stack)) {
                            shop.addDelivery(player.getUUID(), stack);

                            ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                            if (ev != null) {
                                Component msg = Component.literal("Item stored: ")
                                        .withStyle(ChatFormatting.YELLOW)
                                        .append(Component.literal("[Claim]")
                                                .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                                ((ServerPlayer) player).sendSystemMessage(msg);
                            } else {
                                // Guaranteed clickable fallback
                                ChatCompat.sendRunCommandTellraw(
                                        (ServerPlayer) player,
                                        "Item stored: ",
                                        "[Claim]",
                                        "/eco orders claim"
                                );
                            }
                        } else {
                            viewer.sendSystemMessage(Component.literal("Listing removed"));
                        }
                    } else {
                        viewer.sendSystemMessage(Component.literal("Listing no longer available"));
                    }
                    player.closeContainer();
                    ShopUi.open((ServerPlayer) player, shop);
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
                    ShopUi.open((ServerPlayer) player, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }
}
