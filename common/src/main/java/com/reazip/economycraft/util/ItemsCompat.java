package com.reazip.economycraft.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class ItemsCompat {
    private ItemsCompat() {}

    private static volatile List<Item> allItems;

    public static List<Item> allItems() {
        List<Item> cached = allItems;
        if (cached != null) return cached;

        List<Item> out = new ArrayList<>();
        for (Object entry : BuiltInRegistries.ITEM) {
            Item item = unwrap(entry);
            if (item != null && item != Items.AIR) out.add(item);
        }
        allItems = List.copyOf(out);
        return allItems;
    }

    private static Item unwrap(Object value) {
        Object current = value;
        while (current instanceof Holder<?> holder) {
            current = holder.value();
        }
        return current instanceof Item item ? item : null;
    }

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
