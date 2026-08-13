package com.reazip.economycraft.api.v1;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public interface PriceApi {
    Optional<ItemPrice> resolve(ItemStack stack);

    List<String> categories();

    List<ItemPrice> entries(String category);
}
