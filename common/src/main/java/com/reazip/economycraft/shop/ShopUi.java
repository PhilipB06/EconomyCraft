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
                return new ShopMenu(id, inv, shop);
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
                return new ConfirmMenu(id, inv, shop, listing);
            }
        });
    }

    private static class ShopMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final List<ShopListing> listings;
        private final SimpleContainer container;
        private final int rows;

        ShopMenu(int id, Inventory inv, ShopManager shop) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.listings = new ArrayList<>(shop.getListings());
            this.rows = Math.min(6, (listings.size() + 8) / 9);
            this.container = new SimpleContainer(rows * 9);
            for (int i = 0; i < listings.size() && i < container.getContainerSize(); i++) {
                ShopListing l = listings.get(i);
                ItemStack display = l.item.copy();
                container.setItem(i, display);
            }
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < 9; c++) {
                    int slot = r * 9 + c;
                    this.addSlot(new Slot(container, slot, 8 + c * 18, 18 + r * 18) {
                        @Override
                        public boolean mayPickup(Player player) { return false; }
                    });
                }
            }
            int y = 18 + rows * 18 + 14;
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
            if (slot >= 0 && slot < listings.size() && type == ClickType.PICKUP) {
                ShopListing listing = listings.get(slot);
                ShopUi.openConfirm((ServerPlayer) player, shop, listing);
                return;
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
        private final SimpleContainer container = new SimpleContainer(9);

        ConfirmMenu(int id, Inventory inv, ShopManager shop, ShopListing listing) {
            super(MenuType.GENERIC_9x1, id);
            this.shop = shop;
            this.listing = listing;

            ItemStack confirm = new ItemStack(Items.EMERALD_BLOCK);
            container.setItem(2, confirm);

            ItemStack item = listing.item.copy();
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.BARRIER);
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
                        if (!player.getInventory().add(stack)) {
                            shop.addDelivery(player.getUUID(), stack);
                            ((ServerPlayer) player).sendSystemMessage(Component.literal("Item stored, use /eco market claim"));
                        } else {
                            ((ServerPlayer) player).sendSystemMessage(Component.literal("Purchased for " + EconomyCraft.formatMoney(listing.price)));
                        }
                    }
                    player.closeContainer();
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
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
