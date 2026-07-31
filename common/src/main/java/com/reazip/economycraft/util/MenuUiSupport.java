package com.reazip.economycraft.util;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

    public static MenuType<?> getMenuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    public static int requiredRows(int itemCount) {
        int contentRows = (int) Math.ceil(Math.max(1, itemCount) / 9.0);
        return Math.clamp(contentRows + 1, 2, 6);
    }

    public static Component labeledValue(String label, String value, ChatFormatting labelColor) {
        return labeledValues(label, labelColor, value);
    }

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

    public static ItemStack createBalanceItem(ServerPlayer player) {
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        return createBalanceItem(eco, player.getUUID(), player, IdentityCompat.of(player).name());
    }

    public static ItemStack createBalanceItem(EconomyManager eco, UUID playerId, @Nullable ServerPlayer player, @Nullable String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        var profile = player != null
                ? ProfileComponentCompat.tryResolvedOrUnresolved(player.getGameProfile())
                : ProfileComponentCompat.tryUnresolved(name != null && !name.isBlank() ? name : playerId.toString());
        profile.ifPresent(resolvable -> head.set(DataComponents.PROFILE, resolvable));
        long balance = eco.getBalance(playerId, true);
        String displayName = name != null ? name : playerId.toString();
        head.set(DataComponents.CUSTOM_NAME, Component.literal(displayName).withStyle(s -> s.withItalic(false).withBold(true).withColor(BALANCE_NAME_COLOR)));
        head.set(DataComponents.LORE, new ItemLore(List.of(balanceLore(balance))));
        return head;
    }

    public static String resolvePlayerName(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return IdentityCompat.of(online).name();
        return EconomyCraft.getManager(server).getBestName(playerId);
    }

    public static Slot lockedSlot(Container container, int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override public boolean mayPickup(@NonNull Player player) { return false; }
            @Override public boolean mayPlace(@NonNull ItemStack stack) { return false; }
        };
    }

    public static List<Slot> readOnlyGridSlots(Container container, int slotCount) {
        List<Slot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            int r = i / 9;
            int c = i % 9;
            slots.add(lockedSlot(container, i, GRID_LEFT + c * SLOT_SIZE, GRID_TOP + r * SLOT_SIZE));
        }
        return slots;
    }

    public static List<Slot> confirmRowSlots(Container container) {
        return lockedRowSlots(container, CONFIRM_ROW_Y);
    }

    public static List<Slot> lockedRowSlots(Container container, int y) {
        List<Slot> slots = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            slots.add(new Slot(container, i, GRID_LEFT + i * SLOT_SIZE, y) {
                @Override public boolean mayPickup(@NonNull Player player) { return false; }
            });
        }
        return slots;
    }

    public static List<Slot> openGridSlots(Container container, int slotCount) {
        return openGridSlots(container, slotCount, stack -> true);
    }

    public static List<Slot> openGridSlots(Container container, int slotCount, java.util.function.Predicate<ItemStack> mayPlace) {
        List<Slot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            int r = i / 9;
            int c = i % 9;
            slots.add(new Slot(container, i, GRID_LEFT + c * SLOT_SIZE, GRID_TOP + r * SLOT_SIZE) {
                @Override public boolean mayPlace(@NonNull ItemStack stack) { return mayPlace.test(stack); }
            });
        }
        return slots;
    }

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

    public static boolean hasContainerContents(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItems().iterator().hasNext();
    }

    public static boolean matchesSearch(ItemStack stack, @Nullable String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (stackMatches(stack, q)) return true;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            contents.copyInto(items);
            for (ItemStack inner : items) {
                if (!inner.isEmpty() && stackMatches(inner, q)) return true;
            }
        }
        return false;
    }

    private static boolean stackMatches(ItemStack stack, String lowerQuery) {
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        return enchantmentsMatch(stack.get(DataComponents.ENCHANTMENTS), lowerQuery)
                || enchantmentsMatch(stack.get(DataComponents.STORED_ENCHANTMENTS), lowerQuery);
    }

    private static boolean enchantmentsMatch(@Nullable ItemEnchantments enchantments, String lowerQuery) {
        if (enchantments == null) return false;
        for (Object2IntMap.Entry<Holder<Enchantment>> e : enchantments.entrySet()) {
            if (e.getKey().value().description().getString().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        }
        return false;
    }

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
