package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyCraft;
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

public final class ShopUi {
    private ShopUi() {}

    public static void open(ServerPlayer player, ShopManager shop) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Shop");
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

    private static class ShopMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private final List<ShopListing> listings;
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;

        ShopMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.viewer = viewer;
            this.listings = new ArrayList<>(shop.getListings());
            updatePage();
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
            container.clearContent();
            int start = page * 45;
            int totalPages = (int)Math.ceil(listings.size() / 45.0);
            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= listings.size()) break;
                ShopListing l = listings.get(idx);
                ItemStack display = l.item.copy();
                String sellerName = viewer.getServer().getProfileCache().get(l.seller).map(p -> p.getName()).orElse(l.seller.toString());
                display.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        Component.literal("Price: " + EconomyCraft.formatMoney(l.price)),
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
                        ShopUi.openConfirm((ServerPlayer) player, shop, listings.get(index));
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

            ItemStack confirm = new ItemStack(Items.EMERALD_BLOCK);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Confirm purchase"));
            container.setItem(2, confirm);

            ItemStack item = listing.item.copy();
            String sellerName = viewer.getServer().getProfileCache().get(listing.seller).map(p -> p.getName()).orElse(listing.seller.toString());
            item.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                    Component.literal("Price: " + EconomyCraft.formatMoney(listing.price)),
                    Component.literal("Seller: " + sellerName))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.BARRIER);
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
                    EconomyManager eco = EconomyCraft.getManager(((ServerPlayer) player).getServer());
                    if (!eco.pay(player.getUUID(), listing.seller, listing.price)) {
                        ((ServerPlayer) player).sendSystemMessage(Component.literal("Not enough balance"));
                    } else {
                        shop.removeListing(listing.id);
                        ItemStack stack = listing.item.copy();
                        int count = stack.getCount();
                        Component name = stack.getHoverName();
                        if (!player.getInventory().add(stack)) {
                            shop.addDelivery(player.getUUID(), stack);
                            ((ServerPlayer) player).sendSystemMessage(Component.literal("Item stored, use /eco market claim"));
                        } else {
                            ((ServerPlayer) player).sendSystemMessage(Component.literal("Purchased " + count + "x " + name.getString() + " for " + EconomyCraft.formatMoney(listing.price)));
                        }
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

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }
}
