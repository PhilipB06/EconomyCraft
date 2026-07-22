package com.reazip.economycraft.util;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared building blocks for the shop/orders/server-shop container menus: label formatting,
 * the balance-head item, player-name resolution, and the standard slot grid.
 */
public final class MenuUiSupport {
    private MenuUiSupport() {}

    public static final ChatFormatting LABEL_PRIMARY_COLOR = ChatFormatting.GOLD;
    public static final ChatFormatting LABEL_SECONDARY_COLOR = ChatFormatting.AQUA;
    public static final ChatFormatting VALUE_COLOR = ChatFormatting.DARK_PURPLE;
    public static final ChatFormatting BALANCE_NAME_COLOR = ChatFormatting.YELLOW;
    public static final ChatFormatting BALANCE_LABEL_COLOR = ChatFormatting.GOLD;
    public static final ChatFormatting BALANCE_VALUE_COLOR = ChatFormatting.DARK_PURPLE;

    private static final int SLOT_SIZE = 18;
    private static final int GRID_LEFT = 8;
    private static final int GRID_TOP = 18;
    private static final int CONFIRM_ROW_Y = 20;

    public static Component labeledValue(String label, String value, ChatFormatting labelColor) {
        return labeledValues(label, labelColor, value);
    }

    /** One label followed by several values joined by a gray " | " (e.g. "Stack (64): $128 | $64"). */
    public static Component labeledValues(String label, ChatFormatting labelColor, String... values) {
        MutableComponent line = Component.literal(label + ": ").withStyle(s -> s.withItalic(false).withColor(labelColor));
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(Component.literal(" | ").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)));
            }
            line.append(Component.literal(values[i]).withStyle(s -> s.withItalic(false).withColor(VALUE_COLOR)));
        }
        return line;
    }

    /** Joins several lore parts onto one line, separated by " | ". */
    public static Component joinLore(Component... parts) {
        MutableComponent joined = parts[0].copy();
        for (int i = 1; i < parts.length; i++) {
            joined.append(Component.literal(" | ").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)))
                    .append(parts[i]);
        }
        return joined;
    }

    public static Component balanceLore(long balance) {
        return Component.literal("Balance: ")
                .withStyle(s -> s.withItalic(false).withColor(BALANCE_LABEL_COLOR))
                .append(Component.literal(EconomyCraft.formatMoney(balance))
                        .withStyle(s -> s.withItalic(false).withColor(BALANCE_VALUE_COLOR)));
    }

    /** Balance head for an online viewer looking at their own balance. */
    public static ItemStack createBalanceItem(ServerPlayer player) {
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        return createBalanceItem(eco, player.getUUID(), player, IdentityCompat.of(player).name());
    }

    /** Balance head that also supports offline owners (name/id only, no live GameProfile). */
    public static ItemStack createBalanceItem(EconomyManager eco, UUID playerId, @Nullable ServerPlayer player, @Nullable String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        var profile = player != null
                ? ProfileComponentCompat.tryResolvedOrUnresolved(player.getGameProfile())
                : ProfileComponentCompat.tryUnresolved(name != null && !name.isBlank() ? name : playerId.toString());
        profile.ifPresent(resolvable -> head.set(DataComponents.PROFILE, resolvable));
        long balance = eco.getBalance(playerId, true);
        String displayName = name != null ? name : playerId.toString();
        head.set(DataComponents.CUSTOM_NAME, Component.literal(displayName).withStyle(s -> s.withItalic(false).withColor(BALANCE_NAME_COLOR)));
        head.set(DataComponents.LORE, new ItemLore(List.of(balanceLore(balance))));
        return head;
    }

    /** Online player's live name, falling back to the last known name on disk. */
    public static String resolvePlayerName(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return IdentityCompat.of(online).name();
        return EconomyCraft.getManager(server).getBestName(playerId);
    }

    /** Read-only display grid (no pickup/placement), {@code slotCount} slots wrapped at 9 per row. */
    public static List<Slot> readOnlyGridSlots(Container container, int slotCount) {
        List<Slot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            int r = i / 9;
            int c = i % 9;
            slots.add(new Slot(container, i, GRID_LEFT + c * SLOT_SIZE, GRID_TOP + r * SLOT_SIZE) {
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        return slots;
    }

    /** Single row of 9 non-pickupable slots, used for the confirm/cancel menus. */
    public static List<Slot> confirmRowSlots(Container container) {
        List<Slot> slots = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            slots.add(new Slot(container, i, GRID_LEFT + i * SLOT_SIZE, CONFIRM_ROW_Y) {
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        return slots;
    }

    /** Standard 3-row player inventory plus hotbar, starting at pixel row {@code y}. */
    public static List<Slot> playerInventorySlots(Inventory inv, int y) {
        List<Slot> slots = new ArrayList<>(36);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slots.add(new Slot(inv, c + r * 9 + 9, GRID_LEFT + c * SLOT_SIZE, y + r * SLOT_SIZE));
            }
        }
        for (int c = 0; c < 9; c++) {
            slots.add(new Slot(inv, c, GRID_LEFT + c * SLOT_SIZE, y + 58));
        }
        return slots;
    }

    /** True if the stack carries a filled container (e.g. a shulker box with items) worth previewing. */
    public static boolean hasContainerContents(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItems().iterator().hasNext();
    }

    /** Copies a stack's container contents (e.g. a shulker box) into the first {@code slots} slots of {@code target}. */
    public static void copyContainerContents(ItemStack stack, Container target, int slots) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;
        NonNullList<ItemStack> list = NonNullList.withSize(slots, ItemStack.EMPTY);
        contents.copyInto(list);
        for (int i = 0; i < slots; i++) {
            target.setItem(i, list.get(i));
        }
    }
}
