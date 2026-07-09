package com.reazip.economycraft.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Cross-version item accessors.
 *
 * <p>MC 26.2 replaced the per-color stained-glass-pane constants
 * ({@code Items.LIME_STAINED_GLASS_PANE}, ...) with color accessors on a single base item
 * ({@code Items.STAINED_GLASS_PANE.lime()}). Those two API shapes don't source-compile against
 * each other, which is what breaks a 26.1.x build of code written for 26.2 (and vice versa).
 * Resolving the panes by their registry id instead is stable across every supported version,
 * matching the approach used by the other {@code *Compat} helpers in this package.
 */
public final class ItemsCompat {
    private ItemsCompat() {}

    public static Item limeStainedGlassPane() {
        return byId("lime_stained_glass_pane");
    }

    public static Item redStainedGlassPane() {
        return byId("red_stained_glass_pane");
    }

    public static Item grayStainedGlassPane() {
        return byId("gray_stained_glass_pane");
    }

    private static Item byId(String path) {
        return IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, IdentifierCompat.withDefaultNamespace(path))
                .orElse(Items.AIR);
    }
}
