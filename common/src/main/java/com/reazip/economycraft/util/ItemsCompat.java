package com.reazip.economycraft.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

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
