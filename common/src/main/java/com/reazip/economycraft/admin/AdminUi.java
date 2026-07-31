package com.reazip.economycraft.admin;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.HubUi;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.PermissionCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public final class AdminUi {
    private AdminUi() {}

    private static final int SIZE = 27;
    private static final int SHOP = 10;
    private static final int SETTINGS = 12;
    private static final int PLAYERS = 14;
    private static final int RELOAD = 16;
    private static final int BACK = 18;

    public static void open(ServerPlayer player, EconomyManager eco) {
        if (!PermissionCompat.isAdmin(player)) {
            player.sendSystemMessage(MenuUiSupport.line("You need to be an operator for that.", ChatFormatting.RED));
            return;
        }
        MenuUiSupport.openMenu(player, "Admin", (id, inv) -> new AdminMenu(id, inv, player, eco));
    }

    private static class AdminMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final SimpleContainer container = new SimpleContainer(SIZE);

        AdminMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco) {
            super(MenuType.GENERIC_9x3, id);
            this.viewer = viewer;
            this.eco = eco;

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, SIZE)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 3 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private void render() {
            container.clearContent();

            container.setItem(SHOP, MenuUiSupport.button(Items.EMERALD, "Server Shop", ChatFormatting.GREEN,
                    MenuUiSupport.hint("Add, price and remove items."),
                    MenuUiSupport.labeledValue("Items", String.valueOf(eco.getPrices().allEntries().size()),
                            MenuUiSupport.LABEL_PRIMARY_COLOR)));

            container.setItem(SETTINGS, MenuUiSupport.button(Items.COMPARATOR, "Settings", ChatFormatting.AQUA,
                    MenuUiSupport.hint("Starting money, daily reward, tax,"),
                    MenuUiSupport.hint("and which features are switched on.")));

            container.setItem(PLAYERS, MenuUiSupport.button(Items.PLAYER_HEAD, "Players", ChatFormatting.GOLD,
                    MenuUiSupport.hint("Give, take or set anyone's balance.")));

            container.setItem(RELOAD, MenuUiSupport.button(Items.CLOCK, "Reload from disk", ChatFormatting.YELLOW,
                    MenuUiSupport.hint("Re-reads config.json and prices.json."),
                    MenuUiSupport.hint("Only needed if you edited them by hand.")));

            container.setItem(BACK, MenuUiSupport.button(Items.NETHER_STAR, "Main menu", ChatFormatting.YELLOW));

            MenuUiSupport.fillBackground(container);
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= SIZE) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            switch (slot) {
                case SHOP -> AdminShopUi.open(viewer, eco, AdminShopUi.Origin.ADMIN);
                case SETTINGS -> AdminSettingsUi.open(viewer, eco);
                case PLAYERS -> AdminPlayersUi.open(viewer, eco);
                case RELOAD -> {
                    EconomyConfig.load(viewer.level().getServer());
                    eco.getPrices().reload();
                    AdminSettingsUi.applyRuntimeSettings(viewer.level().getServer());
                    viewer.sendSystemMessage(Component.literal("Reloaded config.json and prices.json.")
                            .withStyle(ChatFormatting.GREEN));
                    render();
                }
                case BACK -> HubUi.open(viewer);
                default -> {
                }
            }
            return true;
        }
    }
}
