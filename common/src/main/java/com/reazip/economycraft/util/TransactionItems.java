package com.reazip.economycraft.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public final class TransactionItems {
    private TransactionItems() {}

    public static @Nullable String keyOf(@Nullable ItemStack stack) {
        if (stack == null || stack.getItem() == Items.AIR) return null;
        IdentifierCompat.Id id = IdentifierCompat.wrap(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return id == null ? null : id.asString();
    }

    public static @Nullable Item resolve(@Nullable String key) {
        if (key == null) return null;
        IdentifierCompat.Id id = IdentifierCompat.tryParse(key);
        if (id == null) return null;
        return IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, id)
                .filter(item -> item != Items.AIR)
                .orElse(null);
    }
}
