package com.reazip.economycraft;

import com.mojang.logging.LogUtils;
import com.reazip.economycraft.api.v1.BalanceMutationType;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.EconomyPaths;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.PlayerPickerUi;
import com.reazip.economycraft.util.TransactionEntry;
import com.reazip.economycraft.util.TransactionLogReader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TransactionsUi {
    private TransactionsUi() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ROOT);
    private static final Set<String> LOGGED_UNKNOWN_SOURCES = ConcurrentHashMap.newKeySet();

    record SourceStyle(Item icon, String typeLabel, @Nullable String verb, @Nullable TransactionCategory category) {}

    private static final Map<String, SourceStyle> SOURCE_STYLES = Map.ofEntries(
            Map.entry(EconomySources.SHOP_PURCHASE.asString(), new SourceStyle(Items.EMERALD, "Shop Purchase", "Bought", TransactionCategory.SHOP)),
            Map.entry(EconomySources.SHOP_SALE.asString(), new SourceStyle(Items.EMERALD, "Shop Sale", "Sold", TransactionCategory.SHOP)),
            Map.entry(EconomySources.AUCTION_PURCHASE.asString(), new SourceStyle(Items.CHEST, "Auction Purchase", "Bought", TransactionCategory.AUCTION)),
            Map.entry(EconomySources.ORDER_FULFILLMENT.asString(), new SourceStyle(Items.WRITABLE_BOOK, "Order Fulfillment", "Order fulfilled:", TransactionCategory.ORDERS)),
            Map.entry(EconomySources.ORDER_ESCROW_HOLD.asString(), new SourceStyle(Items.WRITABLE_BOOK, "Order Placed", "Order placed:", TransactionCategory.ORDERS)),
            Map.entry(EconomySources.ORDER_ESCROW_REFUND.asString(), new SourceStyle(Items.WRITABLE_BOOK, "Order Refund", "Order refunded:", TransactionCategory.ORDERS)),
            Map.entry(EconomySources.ADMIN_ADD.asString(), new SourceStyle(Items.COMMAND_BLOCK, "Admin Add", null, TransactionCategory.ADMIN)),
            Map.entry(EconomySources.ADMIN_REMOVE.asString(), new SourceStyle(Items.COMMAND_BLOCK, "Admin Remove", null, TransactionCategory.ADMIN)),
            Map.entry(EconomySources.ADMIN_SET.asString(), new SourceStyle(Items.COMMAND_BLOCK, "Admin Set", null, TransactionCategory.ADMIN)),
            Map.entry(EconomySources.DAILY_REWARD.asString(), new SourceStyle(Items.CLOCK, "Daily Reward", null, TransactionCategory.REWARDS)),
            Map.entry(EconomySources.PVP_REWARD.asString(), new SourceStyle(Items.IRON_SWORD, "PvP Reward", null, TransactionCategory.REWARDS))
    );

    public static void open(ServerPlayer player) {
        openInternal(player, player.getUUID(), null, false, null, 0, TransactionCategory.ALL);
    }

    public static void openAdmin(ServerPlayer admin, PlayerPickerUi.Target target, Consumer<ServerPlayer> onBack) {
        openInternal(admin, target.id(), target.name(), true, onBack, 0, TransactionCategory.ALL);
    }

    private static void openInternal(ServerPlayer viewer, UUID targetId, @Nullable String targetName, boolean adminMode,
                                      @Nullable Consumer<ServerPlayer> onBack, int page, TransactionCategory category) {
        String title = adminMode ? targetName + "'s Transactions" : "Transactions";
        MenuUiSupport.openMenu(viewer, title, (id, inv) ->
                new TransactionsMenu(id, inv, viewer, targetId, targetName, adminMode, onBack, page, category));
    }

    private static void reopenWithEntries(ServerPlayer viewer, UUID targetId, @Nullable String targetName, boolean adminMode,
                                           @Nullable Consumer<ServerPlayer> onBack, int page, TransactionCategory category,
                                           List<TransactionEntry> allEntries) {
        String title = adminMode ? targetName + "'s Transactions" : "Transactions";
        MenuUiSupport.openMenu(viewer, title, (id, inv) ->
                new TransactionsMenu(id, inv, viewer, targetId, targetName, adminMode, onBack, page, category, allEntries));
    }

    private static List<TransactionEntry> filterEntries(List<TransactionEntry> all, TransactionCategory category) {
        if (category == TransactionCategory.ALL) return all;

        List<TransactionEntry> filtered = new ArrayList<>();
        for (TransactionEntry entry : all) {
            if (category.matches(entry)) filtered.add(entry);
        }
        return filtered;
    }

    static SourceStyle styleFor(TransactionEntry entry) {
        SourceStyle known = entry.source() != null ? SOURCE_STYLES.get(entry.source()) : null;
        if (known != null) return known;

        if (entry.type() == BalanceMutationType.PAYMENT_SENT) {
            return new SourceStyle(Items.PAPER, "Payment Sent", "Paid", TransactionCategory.PAYMENTS);
        }
        if (entry.type() == BalanceMutationType.PAYMENT_RECEIVED) {
            return new SourceStyle(Items.PAPER, "Payment Received", "From", TransactionCategory.PAYMENTS);
        }

        String fallbackLabel = entry.source() != null ? entry.source() : entry.type().name();
        if (entry.source() != null && LOGGED_UNKNOWN_SOURCES.add(entry.source())) {
            LOGGER.warn("[EconomyCraft] Transaction source '{}' has no entry in TransactionsUi.SOURCE_STYLES; it will only show under the \"All\" category filter.", entry.source());
        }
        return new SourceStyle(Items.GOLD_INGOT, fallbackLabel, null, null);
    }

    private static String rowSummary(TransactionEntry entry, SourceStyle style) {
        if (style.category() == TransactionCategory.PAYMENTS) {
            return style.verb() + " " + nameOrFallback(entry.counterpartyName());
        }
        if (entry.detail() != null) {
            return style.verb() != null ? style.verb() + " " + entry.detail() : entry.detail();
        }
        return style.typeLabel();
    }

    private static String nameOrFallback(@Nullable String name) {
        return name != null && !name.isBlank() ? name : "someone";
    }

    private static String formatDate(Instant time) {
        return DATE_FORMAT.format(time.atZone(ZoneId.systemDefault()));
    }

    private static class TransactionsMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final UUID targetId;
        @Nullable private final String targetName;
        private final boolean adminMode;
        @Nullable private final Consumer<ServerPlayer> onBack;
        private final List<TransactionEntry> allEntries;
        private final SimpleContainer container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private TransactionCategory category;
        private List<TransactionEntry> entries;
        private int page;

        TransactionsMenu(int id, Inventory inv, ServerPlayer viewer, UUID targetId, @Nullable String targetName, boolean adminMode,
                          @Nullable Consumer<ServerPlayer> onBack, int page, TransactionCategory category) {
            this(id, inv, viewer, targetId, targetName, adminMode, onBack, page, category,
                    TransactionLogReader.readForPlayer(EconomyPaths.logsDir(viewer.level().getServer()), targetId));
        }

        private TransactionsMenu(int id, Inventory inv, ServerPlayer viewer, UUID targetId, @Nullable String targetName, boolean adminMode,
                                  @Nullable Consumer<ServerPlayer> onBack, int page, TransactionCategory category,
                                  List<TransactionEntry> allEntries) {
            super(MenuUiSupport.getMenuType(MenuUiSupport.listMenuRows(filterEntries(allEntries, category).size())), id);
            this.viewer = viewer;
            this.targetId = targetId;
            this.targetName = targetName;
            this.adminMode = adminMode;
            this.onBack = onBack;
            this.allEntries = allEntries;
            this.category = category;
            this.entries = filterEntries(allEntries, category);
            this.page = page;
            this.rows = MenuUiSupport.listMenuRows(entries.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.container = new SimpleContainer(rows * 9);

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, rows * 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + rows * 18 + 14)) {
                this.addSlot(slot);
            }
            renderPage();
        }

        private void renderPage() {
            container.clearContent();
            int totalPages = MenuUiSupport.totalPages(entries.size(), itemsPerPage);
            page = Math.min(page, totalPages - 1);
            int start = page * itemsPerPage;

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= entries.size()) break;
                container.setItem(i, buildRowItem(entries.get(idx)));
            }

            if (entries.isEmpty()) {
                container.setItem(Math.min(4, itemsPerPage - 1), MenuUiSupport.button(Items.BARRIER, "No transactions yet",
                        ChatFormatting.YELLOW, MenuUiSupport.hint("Nothing recorded here so far.")));
            }

            if (page > 0) container.setItem(navRowStart + 3, MenuUiSupport.prevPageButton());
            if (start + itemsPerPage < entries.size()) container.setItem(navRowStart + 5, MenuUiSupport.nextPageButton());
            container.setItem(navRowStart + 4, MenuUiSupport.pageIndicator(page, totalPages));

            container.setItem(navRowStart, headerItem());
            if (adminMode) {
                container.setItem(navRowStart + 1, filterButton());
            }
            container.setItem(navRowStart + 7, backButton());

            MenuUiSupport.fillFooter(container);
        }

        private ItemStack buildRowItem(TransactionEntry entry) {
            SourceStyle style = styleFor(entry);
            boolean positive = entry.amount() >= 0;
            String amountText = EconomyCraft.signedMoney(entry.amount());
            String rowText = amountText + "  " + rowSummary(entry, style);
            ChatFormatting color = positive ? ChatFormatting.GREEN : ChatFormatting.RED;

            return MenuUiSupport.button(style.icon(), rowText, color,
                    MenuUiSupport.labeledValue("Type", style.typeLabel(), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Amount", amountText, MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Balance Before", EconomyCraft.formatMoney(entry.balanceBefore()), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Balance After", EconomyCraft.formatMoney(entry.balanceAfter()), MenuUiSupport.LABEL_PRIMARY_COLOR),
                    MenuUiSupport.labeledValue("Date", formatDate(entry.time()), MenuUiSupport.LABEL_PRIMARY_COLOR));
        }

        private ItemStack headerItem() {
            if (!adminMode) return MenuUiSupport.createBalanceItem(viewer);

            EconomyManager eco = EconomyCraft.getManager(viewer.level().getServer());
            ServerPlayer online = viewer.level().getServer().getPlayerList().getPlayer(targetId);
            return MenuUiSupport.createBalanceItem(eco, targetId, online, targetName);
        }

        private ItemStack filterButton() {
            TransactionCategory[] categories = TransactionCategory.values();
            Component[] lore = new Component[1 + categories.length];
            lore[0] = MenuUiSupport.italicHint("Click to cycle");
            for (int i = 0; i < categories.length; i++) {
                lore[i + 1] = MenuUiSupport.toggleOption(categories[i].label(), category == categories[i]);
            }
            return MenuUiSupport.button(Items.HOPPER, "Filter", MenuUiSupport.LABEL_PRIMARY_COLOR, lore);
        }

        private ItemStack backButton() {
            if (!adminMode) {
                return MenuUiSupport.button(Items.NETHER_STAR, "Main menu", ChatFormatting.YELLOW);
            }
            return MenuUiSupport.button(Items.BARRIER, "Back", ChatFormatting.DARK_RED,
                    MenuUiSupport.hint("Back to " + targetName));
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= rows * 9) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot < navRowStart) return true;

            if (slot == navRowStart + 3 && page > 0) {
                EconomySounds.page(viewer);
                page--;
                renderPage();
                return true;
            }
            if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < entries.size()) {
                EconomySounds.page(viewer);
                page++;
                renderPage();
                return true;
            }
            if (adminMode && slot == navRowStart + 1) {
                EconomySounds.click(viewer);
                TransactionCategory nextCategory = category.next();
                List<TransactionEntry> updated = filterEntries(allEntries, nextCategory);
                if (MenuUiSupport.listMenuRows(updated.size()) != rows) {
                    reopenWithEntries(viewer, targetId, targetName, true, onBack, 0, nextCategory, allEntries);
                    return true;
                }
                category = nextCategory;
                entries = updated;
                page = 0;
                renderPage();
                return true;
            }
            if (slot == navRowStart + 7) {
                EconomySounds.click(viewer);
                viewer.closeContainer();
                if (adminMode && onBack != null) {
                    onBack.accept(viewer);
                } else {
                    HubUi.open(viewer);
                }
                return true;
            }
            return false;
        }
    }
}
