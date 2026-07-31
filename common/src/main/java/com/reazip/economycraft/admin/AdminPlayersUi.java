package com.reazip.economycraft.admin;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ClickKind;
import com.reazip.economycraft.util.CompatMenu;
import com.reazip.economycraft.util.ConfirmUi;
import com.reazip.economycraft.util.ItemsCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.NumberInputUi;
import com.reazip.economycraft.util.PlayerPickerUi;
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

import java.util.List;

public final class AdminPlayersUi {
    private AdminPlayersUi() {}

    private static final int TARGET = 4;
    private static final int GIVE = 10;
    private static final int TAKE = 12;
    private static final int SET = 14;
    private static final int WIPE = 16;
    private static final int BACK = 18;

    public static void open(ServerPlayer player, EconomyManager eco) {
        PlayerPickerUi.open(player, "Pick a player", true,
                (picker, target) -> openTarget(picker, eco, target),
                p -> AdminUi.open(p, eco));
    }

    private static void openTarget(ServerPlayer player, EconomyManager eco, PlayerPickerUi.Target target) {
        MenuUiSupport.openMenu(player, target.name(), (id, inv) -> new TargetMenu(id, inv, player, eco, target));
    }

    private static PlayerPickerUi.Target refresh(EconomyManager eco, PlayerPickerUi.Target target) {
        return new PlayerPickerUi.Target(target.id(), target.name(), eco.getBalance(target.id(), true));
    }

    private static class TargetMenu extends CompatMenu {
        private final ServerPlayer viewer;
        private final EconomyManager eco;
        private final PlayerPickerUi.Target target;
        private final SimpleContainer container = new SimpleContainer(27);

        TargetMenu(int id, Inventory inv, ServerPlayer viewer, EconomyManager eco, PlayerPickerUi.Target target) {
            super(MenuType.GENERIC_9x3, id);
            this.viewer = viewer;
            this.eco = eco;
            this.target = refresh(eco, target);

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, 27)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 3 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private void render() {
            container.clearContent();
            long balance = eco.getBalance(target.id(), true);

            ServerPlayer online = viewer.level().getServer().getPlayerList().getPlayer(target.id());
            ItemStack head = MenuUiSupport.createBalanceItem(eco, target.id(), online, target.name());
            head.set(DataComponents.LORE, new ItemLore(List.of(
                    MenuUiSupport.balanceLore(balance),
                    MenuUiSupport.labeledValue("Status", online != null ? "Online" : "Offline",
                            MenuUiSupport.LABEL_PRIMARY_COLOR))));
            container.setItem(TARGET, head);

            container.setItem(GIVE, MenuUiSupport.button(Items.EMERALD, "Give money", ChatFormatting.GREEN,
                    MenuUiSupport.hint("Add to this player's balance.")));
            container.setItem(TAKE, MenuUiSupport.button(Items.REDSTONE, "Take money", ChatFormatting.RED,
                    MenuUiSupport.hint("Remove from this player's balance.")));
            container.setItem(SET, MenuUiSupport.button(Items.GOLD_INGOT, "Set balance", ChatFormatting.GOLD,
                    MenuUiSupport.hint("Overwrite the balance with an exact amount.")));
            container.setItem(WIPE, MenuUiSupport.button(Items.BARRIER, "Remove from economy",
                    ChatFormatting.DARK_RED,
                    MenuUiSupport.hint("Deletes their account entirely."),
                    MenuUiSupport.hint("They start fresh next time they join.")));

            container.setItem(BACK, MenuUiSupport.button(ItemsCompat.redStainedGlassPane(), "Back",
                    ChatFormatting.DARK_RED, MenuUiSupport.hint("Pick a different player")));
            MenuUiSupport.fillBackground(container);
        }

        private ItemStack subject() {
            return MenuUiSupport.createBalanceItem(eco, target.id(), null, target.name());
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (slot < 0 || slot >= 27) return false;
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) return true;

            long balance = eco.getBalance(target.id(), true);
            switch (slot) {
                case GIVE -> NumberInputUi.openMoney(viewer, "Give to " + target.name(), subject(), "Amount",
                        100, 1, EconomyManager.MAX,
                        (p, amount) -> {
                            eco.addMoney(target.id(), amount);
                            announce(p, "Gave " + EconomyCraft.formatMoney(amount) + " to " + target.name());
                            openTarget(p, eco, target);
                        },
                        p -> openTarget(p, eco, target));
                case TAKE -> NumberInputUi.openMoney(viewer, "Take from " + target.name(), subject(), "Amount",
                        Math.min(100, Math.max(1, balance)), 1, Math.max(1, balance),
                        (p, amount) -> {
                            if (eco.removeMoney(target.id(), amount)) {
                                announce(p, "Took " + EconomyCraft.formatMoney(amount) + " from " + target.name());
                            } else {
                                p.sendSystemMessage(MenuUiSupport.line(target.name() + " does not have that much.",
                                        ChatFormatting.RED));
                            }
                            openTarget(p, eco, target);
                        },
                        p -> openTarget(p, eco, target));
                case SET -> NumberInputUi.openMoney(viewer, "Set " + target.name() + "'s balance", subject(), "Balance",
                        balance, 0, EconomyManager.MAX,
                        (p, amount) -> {
                            eco.setMoney(target.id(), amount);
                            announce(p, "Set " + target.name() + "'s balance to " + EconomyCraft.formatMoney(amount));
                            openTarget(p, eco, target);
                        },
                        p -> openTarget(p, eco, target));
                case WIPE -> ConfirmUi.open(viewer, "Remove " + target.name() + "?", subject(), "Remove them",
                        List.of(MenuUiSupport.balanceLore(balance),
                                MenuUiSupport.line("Their balance is deleted.", ChatFormatting.RED),
                                MenuUiSupport.hint("They get a fresh starting balance next join.")),
                        p -> {
                            eco.removePlayer(target.id());
                            announce(p, "Removed " + target.name() + " from the economy");
                            AdminPlayersUi.open(p, eco);
                        },
                        p -> openTarget(p, eco, target));
                case BACK -> AdminPlayersUi.open(viewer, eco);
                default -> {
                }
            }
            return true;
        }

        private void announce(ServerPlayer admin, String message) {
            admin.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
        }
    }
}
