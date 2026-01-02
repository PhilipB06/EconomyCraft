package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.util.ChatCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ServerShopUi {
    private static final Component STORED_MSG = Component.literal("Item stored: ")
            .withStyle(ChatFormatting.YELLOW);

    private ServerShopUi() {}

    public static void open(ServerPlayer player, EconomyManager eco) {
        open(player, eco, null);
    }

    public static void open(ServerPlayer player, EconomyManager eco, @Nullable String category) {
        if (category == null || category.isBlank()) {
            openCategories(player, eco);
            return;
        }

        if (eco.getPrices().buyableByCategory(category).isEmpty()) {
            player.sendSystemMessage(Component.literal("No items available in that category.")
                    .withStyle(ChatFormatting.RED));
            openCategories(player, eco);
            return;
        }

        openCategory(player, eco, category);
    }

    private static void openCategories(ServerPlayer player, EconomyManager eco) {
        Component title = EconomyCraft.createBalanceTitle("Server Shop", player);

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new CategoryMenu(id, inv, eco, player);
            }
        });
    }

    private static void openCategory(ServerPlayer player, EconomyManager eco, String category) {
        String titleText = formatCategoryTitle(category);
        Component title = EconomyCraft.createBalanceTitle(titleText, player);

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ItemMenu(id, inv, eco, category, player);
            }
        });
    }

    private static String formatCategoryTitle(String category) {
        if (category == null || category.isBlank()) return "Server Shop";
        return Character.toUpperCase(category.charAt(0)) + category.substring(1);
    }

    private static class CategoryMenu extends AbstractContainerMenu {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayer viewer;
        private List<PriceRegistry.PriceEntry> categories = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;

        CategoryMenu(int id, Inventory inv, EconomyManager eco, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.eco = eco;
            this.viewer = viewer;
            this.prices = eco.getPrices();

            refreshCategories();
            setupSlots(inv);
            updatePage();
        }

        private void refreshCategories() {
            categories = new ArrayList<>();
            for (String cat : prices.buyCategories()) {
                List<PriceRegistry.PriceEntry> entries = prices.buyableByCategory(cat);
                if (entries.isEmpty()) continue;
                PriceRegistry.PriceEntry entry = entries.get(0);
                ItemStack icon = createDisplayStack(entry, viewer);
                if (icon.isEmpty()) continue;
                categories.add(entry);
            }
        }

        private void setupSlots(Inventory inv) {
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
            int totalPages = (int) Math.ceil(categories.size() / 45.0);

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= categories.size()) break;

                PriceRegistry.PriceEntry entry = categories.get(idx);
                ItemStack icon = createDisplayStack(entry, viewer);
                if (icon.isEmpty()) continue;

                icon.set(DataComponents.CUSTOM_NAME, Component.literal(formatCategoryTitle(entry.category())));
                icon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Click to view items"))));
                container.setItem(i, icon);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page"));
                container.setItem(45, prev);
            }

            if (start + 45 < categories.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Next page"));
                container.setItem(53, next);
            }

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)));
            container.setItem(49, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < categories.size()) {
                        PriceRegistry.PriceEntry entry = categories.get(index);
                        openCategory(viewer, eco, entry.category());
                        return;
                    }
                }
                if (slot == 45 && page > 0) { page--; updatePage(); return; }
                if (slot == 53 && (page + 1) * 45 < categories.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static class ItemMenu extends AbstractContainerMenu {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayer viewer;
        private final String category;
        private List<PriceRegistry.PriceEntry> entries = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;

        ItemMenu(int id, Inventory inv, EconomyManager eco, String category, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.eco = eco;
            this.viewer = viewer;
            this.category = category;
            this.prices = eco.getPrices();

            refreshEntries();
            setupSlots(inv);
            updatePage();
        }

        private void refreshEntries() {
            entries = new ArrayList<>(prices.buyableByCategory(category));
        }

        private void setupSlots(Inventory inv) {
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
            int totalPages = (int) Math.ceil(entries.size() / 45.0);

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= entries.size()) break;

                PriceRegistry.PriceEntry entry = entries.get(idx);
                ItemStack display = createDisplayStack(entry, viewer);
                if (display.isEmpty()) continue;

                int stackSize = Math.max(1, entry.stack());
                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal("Buy: " + EconomyCraft.formatMoney(entry.unitBuy())));

                Long stackPrice = safeMultiply(entry.unitBuy(), stackSize);
                if (stackSize > 1 && stackPrice != null) {
                    lore.add(Component.literal("Stack (" + stackSize + "): " + EconomyCraft.formatMoney(stackPrice)));
                }

                lore.add(Component.literal("Left click: Buy 1"));
                if (stackSize > 1) {
                    lore.add(Component.literal("Shift-click: Buy " + stackSize));
                }

                display.set(DataComponents.LORE, new ItemLore(lore));
                display.setCount(Math.min(stackSize, display.getMaxStackSize()));
                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page"));
                container.setItem(45, prev);
            }

            if (start + 45 < entries.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Next page"));
                container.setItem(53, next);
            }

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)));
            container.setItem(49, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < entries.size()) {
                        handlePurchase(entries.get(index), type);
                        return;
                    }
                }
                if (slot == 45 && page > 0) { page--; updatePage(); return; }
                if (slot == 53 && (page + 1) * 45 < entries.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
        }

        private void handlePurchase(PriceRegistry.PriceEntry entry, ClickType clickType) {
            if (entry.unitBuy() <= 0) {
                viewer.sendSystemMessage(Component.literal("This item cannot be purchased.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            ItemStack base = createDisplayStack(entry, viewer);
            if (base.isEmpty()) {
                viewer.sendSystemMessage(Component.literal("Item unavailable.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            int stackSize = Math.max(1, entry.stack());
            int amount = clickType == ClickType.QUICK_MOVE ? stackSize : 1;

            Long total = safeMultiply(entry.unitBuy(), amount);
            if (total == null) {
                viewer.sendSystemMessage(Component.literal("Price too large.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            long balance = eco.getBalance(viewer.getUUID(), true);
            if (balance < total) {
                viewer.sendSystemMessage(Component.literal("Not enough balance.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            if (!eco.removeMoney(viewer.getUUID(), total)) {
                viewer.sendSystemMessage(Component.literal("Not enough balance.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            boolean stored = giveToPlayer(base, amount);

            Component success = Component.literal(
                    "Purchased " + amount + "x " + base.getHoverName().getString() +
                            " for " + EconomyCraft.formatMoney(total))
                    .withStyle(ChatFormatting.GREEN);
            viewer.sendSystemMessage(success);

            if (stored) {
                sendStoredMessage(viewer);
            }
        }

        private boolean giveToPlayer(ItemStack base, int amount) {
            int remaining = amount;
            boolean stored = false;
            while (remaining > 0) {
                int give = Math.min(base.getMaxStackSize(), remaining);
                ItemStack stack = base.copyWithCount(give);
                if (!viewer.getInventory().add(stack)) {
                    eco.getShop().addDelivery(viewer.getUUID(), stack);
                    stored = true;
                }
                remaining -= give;
            }
            return stored;
        }

        private void sendStoredMessage(ServerPlayer player) {
            ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
            if (ev != null) {
                player.sendSystemMessage(STORED_MSG.copy()
                        .append(Component.literal("[Claim]")
                                .withStyle(s -> s.withUnderlined(true)
                                        .withColor(ChatFormatting.GREEN)
                                        .withClickEvent(ev))));
            } else {
                ChatCompat.sendRunCommandTellraw(player, "Item stored: ", "[Claim]", "/eco orders claim");
            }
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static ItemStack createDisplayStack(PriceRegistry.PriceEntry entry, ServerPlayer viewer) {
        ResourceLocation id = entry.id();
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty() || item.get() == Items.AIR) return ItemStack.EMPTY;
            return new ItemStack(item.get());
        }

        String path = id.getPath();
        if (path.startsWith("enchanted_book_")) {
            return createEnchantedBookStack(id, viewer);
        }

        return createPotionStack(id);
    }

    private static ItemStack createEnchantedBookStack(ResourceLocation key, ServerPlayer viewer) {
        String path = key.getPath();
        String suffix = path.substring("enchanted_book_".length());
        int lastUnderscore = suffix.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= suffix.length() - 1) return ItemStack.EMPTY;

        String enchantPath = suffix.substring(0, lastUnderscore);
        String levelStr = suffix.substring(lastUnderscore + 1);

        int level;
        try {
            level = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            return ItemStack.EMPTY;
        }

        ResourceLocation enchantId = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), enchantPath);
        HolderLookup.RegistryLookup<Enchantment> lookup = viewer.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> holder = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, enchantId));
        if (holder.isEmpty()) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder.get(), level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    private static ItemStack createPotionStack(ResourceLocation key) {
        String path = key.getPath();
        Item baseItem = Items.POTION;
        String working = path;

        if (path.startsWith("splash_")) {
            baseItem = Items.SPLASH_POTION;
            working = path.substring("splash_".length());
        } else if (path.startsWith("lingering_")) {
            baseItem = Items.LINGERING_POTION;
            working = path.substring("lingering_".length());
        } else if (path.startsWith("arrow_of_")) {
            baseItem = Items.TIPPED_ARROW;
            working = path.substring("arrow_of_".length());
        } else if (path.startsWith("potion_of_")) {
            working = path.substring("potion_of_".length());
        }

        if (working.endsWith("_splash_potion")) {
            baseItem = Items.SPLASH_POTION;
            working = working.substring(0, working.length() - "_splash_potion".length());
        } else if (working.endsWith("_lingering_potion")) {
            baseItem = Items.LINGERING_POTION;
            working = working.substring(0, working.length() - "_lingering_potion".length());
        } else if (working.endsWith("_potion")) {
            baseItem = Items.POTION;
            working = working.substring(0, working.length() - "_potion".length());
        }

        String potionPath;
        if (working.equals("water_bottle") || working.equals("water")) {
            potionPath = "water";
        } else {
            String effect = working;
            if (effect.endsWith("_extended")) {
                effect = effect.substring(0, effect.length() - "_extended".length());
                potionPath = "long_" + effect;
            } else if (effect.endsWith("_2")) {
                effect = effect.substring(0, effect.length() - 2);
                potionPath = "strong_" + effect;
            } else if (effect.endsWith("_1")) {
                effect = effect.substring(0, effect.length() - 2);
                potionPath = effect;
            } else {
                potionPath = effect;
            }
        }

        if ("the_turtle_master".equals(potionPath)) {
            potionPath = "turtle_master";
        }

        ResourceLocation potionId = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), potionPath);
        Optional<Potion> potion = BuiltInRegistries.POTION.getOptional(potionId);
        if (potion.isEmpty()) return ItemStack.EMPTY;

        Holder<Potion> holder = BuiltInRegistries.POTION.wrapAsHolder(potion.get());
        return PotionContents.createItemStack(baseItem, holder);
    }

    private static Long safeMultiply(long a, int b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return null;
        }
    }
}
