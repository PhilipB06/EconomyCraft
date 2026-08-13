package com.reazip.economycraft.api.v1;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.OptionalLong;

public final class ItemPrice {
    private final String key;
    private final String itemId;
    private final String category;
    private final int bulkAmount;
    private final OptionalLong unitBuyPrice;
    private final OptionalLong unitSellPrice;
    private final boolean customItem;
    private final ItemStack prototype;

    public ItemPrice(
            String key,
            String itemId,
            String category,
            int bulkAmount,
            OptionalLong unitBuyPrice,
            OptionalLong unitSellPrice,
            boolean customItem,
            ItemStack prototype
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.category = Objects.requireNonNull(category, "category");
        this.bulkAmount = bulkAmount;
        this.unitBuyPrice = Objects.requireNonNull(unitBuyPrice, "unitBuyPrice");
        this.unitSellPrice = Objects.requireNonNull(unitSellPrice, "unitSellPrice");
        this.customItem = customItem;
        this.prototype = Objects.requireNonNull(prototype, "prototype").copy();
    }

    public String key() { return key; }

    public String itemId() { return itemId; }

    public String category() { return category; }

    public int bulkAmount() { return bulkAmount; }

    public OptionalLong unitBuyPrice() { return unitBuyPrice; }

    public OptionalLong unitSellPrice() { return unitSellPrice; }

    public boolean hasBuyPrice() { return unitBuyPrice.isPresent(); }

    public boolean hasSellPrice() { return unitSellPrice.isPresent(); }

    public boolean customItem() { return customItem; }

    public ItemStack prototype() { return prototype.copy(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemPrice that)) return false;
        return bulkAmount == that.bulkAmount
                && customItem == that.customItem
                && key.equals(that.key)
                && itemId.equals(that.itemId)
                && category.equals(that.category)
                && unitBuyPrice.equals(that.unitBuyPrice)
                && unitSellPrice.equals(that.unitSellPrice)
                && ItemStack.isSameItemSameComponents(prototype, that.prototype)
                && prototype.getCount() == that.prototype.getCount();
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, itemId, category, bulkAmount, unitBuyPrice, unitSellPrice, customItem);
    }

    @Override
    public String toString() {
        return "ItemPrice[key=" + key + ", itemId=" + itemId + ", category=" + category
                + ", bulkAmount=" + bulkAmount + ", unitBuyPrice=" + unitBuyPrice
                + ", unitSellPrice=" + unitSellPrice + ", customItem=" + customItem + "]";
    }
}
