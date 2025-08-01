package com.reazip.economycraft.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Listing for one item in the shop. */
public class ShopListing {
    public int id;
    public UUID seller;
    public ItemStack item;
    public long price;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        if (seller != null)
            tag.putUUID("seller", seller);
        tag.putLong("price", price);
        CompoundTag itemTag = new CompoundTag();
        item.save(itemTag);
        tag.put("item", itemTag);
        return tag;
    }

    public static ShopListing load(CompoundTag tag) {
        ShopListing l = new ShopListing();
        l.id = tag.getInt("id");
        if (tag.hasUUID("seller"))
            l.seller = tag.getUUID("seller");
        l.price = tag.getLong("price");
        l.item = ItemStack.of(tag.getCompound("item"));
        return l;
    }
}
