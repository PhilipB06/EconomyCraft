package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.ItemsCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
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

public final class ShopUi {
    private ShopUi() {}

    public static void open(ServerPlayer player, ShopManager shop) {
        Component title = Component.literal("Shop");

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

    /** Whether the player can cover the listing price plus tax (buyers pay price + tax). */
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

    private static class ShopMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private List<ShopListing> listings = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final int navRowStart = 45;
        private final Runnable listener = this::updatePage;

        ShopMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.viewer = viewer;
            updatePage();
            shop.addListener(listener);
            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 54)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 6 * 18 + 14)) {
                this.addSlot(slot);
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

                String sellerName = MenuUiSupport.resolvePlayerName(viewer.level().getServer(), l.seller);

                long tax = Math.round(l.price * EconomyConfig.get().taxRate);
                display.set(DataComponents.LORE, new ItemLore(List.of(
                        createPriceLore(l.price, tax),
                        MenuUiSupport.labeledValue("Seller", sellerName, MenuUiSupport.LABEL_SECONDARY_COLOR))));
                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }

            if (start + 45 < listings.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }

            ItemStack balance = MenuUiSupport.createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ContainerInput type, Player player) {
            if (type == ContainerInput.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < listings.size()) {
                        ShopListing listing = listings.get(index);
                        ServerPlayer sp = (ServerPlayer) player;
                        if (listing.seller.equals(sp.getUUID())) {
                            openRemove(sp, shop, listing);
                        } else if (!canAfford(sp, listing.price)) {
                            sp.sendSystemMessage(Component.literal("Not enough balance").withStyle(ChatFormatting.RED));
                        } else {
                            ShopUi.openConfirm(sp, shop, listing);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * 45 < listings.size()) { page++; updatePage(); return; }
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

            ItemStack confirm = new ItemStack(ItemsCompat.limeStainedGlassPane());
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Confirm").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            String sellerName = MenuUiSupport.resolvePlayerName(viewer.level().getServer(), listing.seller);

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(DataComponents.LORE, new ItemLore(List.of(
                    createPriceLore(listing.price, tax),
                    MenuUiSupport.labeledValue("Seller", sellerName, MenuUiSupport.LABEL_SECONDARY_COLOR))));
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
        public void clicked(int slot, int dragType, ContainerInput type, Player player) {
            if (type == ContainerInput.PICKUP) {
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

                            String sellerName = MenuUiSupport.resolvePlayerName(server, current.seller);

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

            ItemStack confirm = new ItemStack(ItemsCompat.limeStainedGlassPane());
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Confirm").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(DataComponents.LORE, new ItemLore(List.of(
                    createPriceLore(listing.price, tax),
                    MenuUiSupport.labeledValue("Seller", "you", MenuUiSupport.LABEL_SECONDARY_COLOR),
                    Component.literal("This will remove the listing").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED)))));
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
        public void clicked(int slot, int dragType, ContainerInput type, Player player) {
            if (type == ContainerInput.PICKUP) {
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
