package com.reazip.economycraft.util;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PlayerPickerUi {
    private PlayerPickerUi() {}

    public record Target(UUID id, String name, long balance) {}

    public static void open(ServerPlayer player, String title, boolean includeSelf,
                            BiConsumer<ServerPlayer, Target> onPick, Consumer<ServerPlayer> onCancel) {
        open(player, title, includeSelf, null, 0, onPick, onCancel);
    }

    private static void open(ServerPlayer player, String title, boolean includeSelf, @Nullable String query, int page,
                             BiConsumer<ServerPlayer, Target> onPick, Consumer<ServerPlayer> onCancel) {
        MenuUiSupport.openMenu(player, title, (id, inv) ->
                new PickerMenu(id, inv, player, title, includeSelf, query, page, onPick, onCancel));
    }

    private static class PickerMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final String title;
        private final boolean includeSelf;
        @Nullable private final String query;
        private final BiConsumer<ServerPlayer, Target> onPick;
        private final Consumer<ServerPlayer> onCancel;
        private final SimpleContainer container;
        private final List<Target> targets;
        private final int rows;
        private final int gridSlots;
        private final int nav;
        private int page;

        PickerMenu(int id, Inventory inv, ServerPlayer viewer, String title, boolean includeSelf,
                   @Nullable String query, int page,
                   BiConsumer<ServerPlayer, Target> onPick, Consumer<ServerPlayer> onCancel) {
            this(id, inv, viewer, title, includeSelf, query, page, onPick, onCancel,
                    collect(viewer, includeSelf, query));
        }

        private PickerMenu(int id, Inventory inv, ServerPlayer viewer, String title, boolean includeSelf,
                           @Nullable String query, int page,
                           BiConsumer<ServerPlayer, Target> onPick, Consumer<ServerPlayer> onCancel,
                           List<Target> resolved) {
            super(MenuUiSupport.getMenuType(MenuUiSupport.requiredRows(resolved.size())), id);
            this.viewer = viewer;
            this.title = title;
            this.includeSelf = includeSelf;
            this.query = query;
            this.onPick = onPick;
            this.onCancel = onCancel;
            this.targets = resolved;
            this.rows = MenuUiSupport.requiredRows(resolved.size());
            this.gridSlots = (rows - 1) * 9;
            this.nav = gridSlots;
            this.container = new SimpleContainer(rows * 9);
            this.page = Math.clamp(page, 0, Math.max(0, MenuUiSupport.totalPages(targets.size(), gridSlots) - 1));

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, rows * 9)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + rows * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private static List<Target> collect(ServerPlayer viewer, boolean includeSelf, @Nullable String query) {
            var server = viewer.level().getServer();
            EconomyManager eco = EconomyCraft.getManager(server);
            Map<UUID, String> known = new LinkedHashMap<>();

            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                known.put(online.getUUID(), IdentityCompat.of(online).name());
            }
            for (UUID id : eco.getBalances().keySet()) {
                known.computeIfAbsent(id, key -> eco.getBestName(key));
            }

            String needle = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
            List<Target> out = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : known.entrySet()) {
                if (!includeSelf && entry.getKey().equals(viewer.getUUID())) continue;
                String name = entry.getValue() == null ? entry.getKey().toString() : entry.getValue();
                if (needle != null && !name.toLowerCase(Locale.ROOT).contains(needle)) continue;
                out.add(new Target(entry.getKey(), name, eco.getBalance(entry.getKey(), true)));
            }
            out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
            return out;
        }

        private boolean searching() {
            return query != null && !query.isBlank();
        }

        private void render() {
            container.clearContent();
            var server = viewer.level().getServer();
            EconomyManager eco = EconomyCraft.getManager(server);
            int start = page * gridSlots;
            int totalPages = MenuUiSupport.totalPages(targets.size(), gridSlots);

            for (int i = 0; i < gridSlots; i++) {
                int index = start + i;
                if (index >= targets.size()) break;

                Target target = targets.get(index);
                ServerPlayer online = server.getPlayerList().getPlayer(target.id());
                ItemStack head = MenuUiSupport.createBalanceItem(eco, target.id(), online, target.name());
                List<Component> lore = new ArrayList<>();
                lore.add(MenuUiSupport.balanceLore(target.balance()));
                lore.add(MenuUiSupport.labeledValue("Status", online != null ? "Online" : "Offline",
                        MenuUiSupport.LABEL_PRIMARY_COLOR));
                lore.add(MenuUiSupport.labeledValue("Click", "Choose this player", MenuUiSupport.LABEL_SECONDARY_COLOR));
                head.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(i, head);
            }

            if (targets.isEmpty()) {
                container.setItem(Math.min(4, gridSlots - 1), MenuUiSupport.button(Items.BOOK, "No players found",
                        ChatFormatting.YELLOW,
                        MenuUiSupport.hint(searching() ? "Nothing matched \"" + query + "\"" : "Nobody has an account yet")));
            }

            container.setItem(nav, MenuUiSupport.backButton());
            if (page > 0) container.setItem(nav + 3, MenuUiSupport.prevPageButton());
            container.setItem(nav + 4, MenuUiSupport.pageIndicator(page, totalPages));
            if (start + gridSlots < targets.size()) container.setItem(nav + 5, MenuUiSupport.nextPageButton());

            container.setItem(nav + 8, searching()
                    ? MenuUiSupport.clearSearchButton(query)
                    : MenuUiSupport.button(Items.COMPASS, "Search", ChatFormatting.GREEN,
                            MenuUiSupport.hint("Find a player by name")));

            MenuUiSupport.fillFooter(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= rows * 9) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            if (slot < gridSlots) {
                int index = page * gridSlots + slot;
                if (index < targets.size()) {
                    viewer.closeContainer();
                    onPick.accept(viewer, targets.get(index));
                }
                return true;
            }
            if (slot == nav) {
                viewer.closeContainer();
                if (onCancel != null) onCancel.accept(viewer);
                return true;
            }
            if (slot == nav + 3 && page > 0) {
                page--;
                render();
                return true;
            }
            if (slot == nav + 5 && (page + 1) * gridSlots < targets.size()) {
                page++;
                render();
                return true;
            }
            if (slot == nav + 8) {
                if (searching()) {
                    open(viewer, title, includeSelf, null, 0, onPick, onCancel);
                } else {
                    TextInputUi.openSearch(viewer, "Search players",
                            (p, q) -> open(p, title, includeSelf, q, 0, onPick, onCancel));
                }
                return true;
            }
            return true;
        }
    }
}
