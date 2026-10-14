package com.reazip.economycraft.util;

import com.reazip.economycraft.EconomyCraft;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.regex.Pattern;

public final class NumberInputUi {
    private NumberInputUi() {}

    private static final int[] MONEY_STEPS = {1000, 100, 10, 1};
    private static final int[] COUNT_STEPS = {512, 64, 8, 1};
    private static final int[] DECIMAL_STEPS = {1000, 100, 25, 1};
    private static final Pattern DECIMAL_NUMBER = Pattern.compile("\\d+\\.?\\d*|\\.\\d+");
    private static final Input WHOLE = new Input(String::valueOf, EconomyCraft::parseMoneyShort);

    private static final int VALUE_SLOT = 4;
    private static final int TYPE_SLOT = 13;
    private static final int CANCEL_SLOT = 18;
    private static final int CONFIRM_SLOT = 26;
    private static final int GRID_SLOTS = 27;

    public static void openMoney(ServerPlayer player, String title, ItemStack subject, String label,
                                 long initial, long min, long max,
                                 BiConsumer<ServerPlayer, Long> onConfirm, Consumer<ServerPlayer> onCancel) {
        openMoney(player, title, subject, label, initial, min, max, "Confirm", null, onConfirm, onCancel);
    }

    public static void openMoney(ServerPlayer player, String title, ItemStack subject, String label,
                                 long initial, long min, long max, String confirmLabel,
                                 @Nullable LongFunction<List<Component>> confirmLore,
                                 BiConsumer<ServerPlayer, Long> onConfirm, Consumer<ServerPlayer> onCancel) {
        open(player, title, subject, label, initial, min, max, MONEY_STEPS, EconomyCraft::formatMoney,
                confirmLabel, confirmLore, onConfirm, onCancel);
    }

    public static void openCount(ServerPlayer player, String title, ItemStack subject, String label,
                                 long initial, long min, long max,
                                 BiConsumer<ServerPlayer, Long> onConfirm, Consumer<ServerPlayer> onCancel) {
        openCount(player, title, subject, label, initial, min, max, "Confirm", null, onConfirm, onCancel);
    }

    public static void openCount(ServerPlayer player, String title, ItemStack subject, String label,
                                 long initial, long min, long max, String confirmLabel,
                                 @Nullable LongFunction<List<Component>> confirmLore,
                                 BiConsumer<ServerPlayer, Long> onConfirm, Consumer<ServerPlayer> onCancel) {
        open(player, title, subject, label, initial, min, max, COUNT_STEPS, String::valueOf,
                confirmLabel, confirmLore, onConfirm, onCancel);
    }

    public static void openDecimal(ServerPlayer player, String title, ItemStack subject, String label,
                                   long initialHundredths, long minHundredths, long maxHundredths, String unit,
                                   BiConsumer<ServerPlayer, Long> onConfirm, Consumer<ServerPlayer> onCancel) {
        open(player, title, subject, label, initialHundredths, minHundredths, maxHundredths, DECIMAL_STEPS, v -> formatHundredths(v) + unit,
                "Confirm", null, new Input(NumberInputUi::formatHundredths, text -> parseHundredths(text, unit)),
                onConfirm, onCancel);
    }

    public static void open(ServerPlayer player, String title, ItemStack subject, String label,
                            long initial, long min, long max, int[] steps, LongFunction<String> format,
                            String confirmLabel, @Nullable LongFunction<List<Component>> confirmLore,
                            BiConsumer<ServerPlayer, Long> onConfirm, Consumer<ServerPlayer> onCancel) {
        open(player, title, subject, label, initial, min, max, steps, format, confirmLabel, confirmLore,
                WHOLE, onConfirm, onCancel);
    }

    private static void open(ServerPlayer player, String title, ItemStack subject, String label,
                             long initial, long min, long max, int[] steps, LongFunction<String> format,
                             String confirmLabel, @Nullable LongFunction<List<Component>> confirmLore,
                             Input input,
                             BiConsumer<ServerPlayer, Long> onConfirm, Consumer<ServerPlayer> onCancel) {
        MenuUiSupport.openMenu(player, title, (id, inv) ->
                new NumberMenu(id, inv, subject, label, initial, min, max, steps, format,
                        confirmLabel, confirmLore, input, onConfirm, onCancel));
    }

    public static String formatHundredths(long hundredths) {
        return BigDecimal.valueOf(hundredths, 2).stripTrailingZeros().toPlainString();
    }

    private static Long parseHundredths(String text, String unit) {
        String s = text.trim();
        if (s.toLowerCase(Locale.ROOT).endsWith(unit)) s = s.substring(0, s.length() - unit.length()).trim();
        if (!DECIMAL_NUMBER.matcher(s).matches()) return null;
        return new BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(Long.MAX_VALUE)).longValue();
    }

    private record Input(LongFunction<String> show, Function<String, Long> parse) {}

    private static class NumberMenu extends CompatMenu {
        private final ItemStack subject;
        private final String label;
        private final long min;
        private final long max;
        private final int[] steps;
        private final LongFunction<String> format;
        private final String confirmLabel;
        @Nullable private final LongFunction<List<Component>> confirmLore;
        private final Input input;
        private final BiConsumer<ServerPlayer, Long> onConfirm;
        private final Consumer<ServerPlayer> onCancel;
        private final SimpleContainer container = new SimpleContainer(GRID_SLOTS);
        private long value;

        NumberMenu(int id, Inventory inv, ItemStack subject, String label, long initial, long min, long max,
                   int[] steps, LongFunction<String> format, String confirmLabel,
                   @Nullable LongFunction<List<Component>> confirmLore, Input input,
                   BiConsumer<ServerPlayer, Long> onConfirm, Consumer<ServerPlayer> onCancel) {
            super(MenuType.GENERIC_9x3, id);
            this.subject = subject == null || subject.isEmpty() ? new ItemStack(Items.PAPER) : subject.copy();
            this.label = label;
            this.min = min;
            this.max = max;
            this.steps = steps;
            this.format = format;
            this.confirmLabel = confirmLabel;
            this.confirmLore = confirmLore;
            this.input = input;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
            this.value = Math.clamp(initial, min, max);

            for (Slot slot : MenuUiSupport.readOnlyGridSlots(container, GRID_SLOTS)) {
                this.addSlot(slot);
            }
            for (Slot slot : MenuUiSupport.playerInventorySlots(inv, 18 + 3 * 18 + 14)) {
                this.addSlot(slot);
            }
            render();
        }

        private void render() {
            container.clearContent();

            for (int i = 0; i < steps.length && i < 4; i++) {
                container.setItem(i, MenuUiSupport.button(ItemsCompat.redStainedGlassPane(),
                        "-" + input.show().apply(steps[i]), ChatFormatting.RED, MenuUiSupport.italicHint("Shift-click for x10")));
                container.setItem(8 - i, MenuUiSupport.button(ItemsCompat.limeStainedGlassPane(),
                        "+" + input.show().apply(steps[i]), ChatFormatting.GREEN, MenuUiSupport.italicHint("Shift-click for x10")));
            }

            ItemStack display = subject.copy();
            display.setCount(1);
            List<Component> lore = new ArrayList<>();
            lore.add(MenuUiSupport.labeledValue(label, format.apply(value), MenuUiSupport.LABEL_PRIMARY_COLOR));
            lore.add(MenuUiSupport.hint("Allowed: " + format.apply(min) + " - " + format.apply(max)));
            display.set(DataComponents.CUSTOM_NAME, Component.literal(format.apply(value))
                    .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.YELLOW)));
            display.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(VALUE_SLOT, display);

            container.setItem(TYPE_SLOT, MenuUiSupport.button(Items.NAME_TAG, "Type a value",
                    ChatFormatting.AQUA, MenuUiSupport.hint("Enter the number by hand")));

            container.setItem(CANCEL_SLOT, MenuUiSupport.cancelButton());

            List<Component> confirmLines = confirmLore != null
                    ? confirmLore.apply(value)
                    : List.of(MenuUiSupport.labeledValue(label, format.apply(value), MenuUiSupport.LABEL_PRIMARY_COLOR));
            container.setItem(CONFIRM_SLOT, MenuUiSupport.confirmButton(confirmLabel, confirmLines.toArray(new Component[0])));

            MenuUiSupport.fillBackground(container);
        }

        private void apply(long delta) {
            value = Math.clamp(value + delta, min, max);
            render();
        }

        @Override
        protected boolean onClick(int slot, int dragType, ClickKind kind, Player player) {
            if (kind != ClickKind.PICKUP && kind != ClickKind.QUICK_MOVE) {
                return slot >= 0 && slot < GRID_SLOTS;
            }
            if (slot < 0 || slot >= GRID_SLOTS) return false;

            long multiplier = kind == ClickKind.QUICK_MOVE ? 10 : 1;
            ServerPlayer serverPlayer = (ServerPlayer) player;

            for (int i = 0; i < steps.length && i < 4; i++) {
                if (slot == i) {
                    EconomySounds.click(serverPlayer);
                    apply(-steps[i] * multiplier);
                    return true;
                }
                if (slot == 8 - i) {
                    EconomySounds.click(serverPlayer);
                    apply(steps[i] * multiplier);
                    return true;
                }
            }

            if (slot == TYPE_SLOT) {
                EconomySounds.click(serverPlayer);
                TextInputUi.open(serverPlayer, "Enter a value", input.show().apply(value), Items.NAME_TAG,
                        "Use: ", "Type a number", (p, text) -> {
                            Long parsed = input.parse().apply(text);
                            if (parsed == null) {
                                p.sendSystemMessage(MenuUiSupport.line("\"" + text + "\" is not a number.", ChatFormatting.RED));
                            }
                            long next = parsed == null ? value : Math.clamp(parsed, min, max);
                            open(p, "Set " + label, subject, label, next, min, max, steps, format,
                                    confirmLabel, confirmLore, input, onConfirm, onCancel);
                        });
                return true;
            }

            if (slot == CANCEL_SLOT) {
                EconomySounds.click(serverPlayer);
                serverPlayer.closeContainer();
                if (onCancel != null) onCancel.accept(serverPlayer);
                return true;
            }

            if (slot == CONFIRM_SLOT) {
                serverPlayer.closeContainer();
                onConfirm.accept(serverPlayer, value);
                return true;
            }

            return true;
        }
    }
}
