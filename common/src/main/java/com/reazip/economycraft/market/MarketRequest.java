package com.reazip.economycraft.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class MarketRequest {
    public int id;
    public UUID requester;
    public ItemStack item;
    public long price;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        if (requester != null)
            tag.putUUID("requester", requester);
        tag.putLong("price", price);
        CompoundTag c = new CompoundTag();
        item.save(c);
        tag.put("item", c);
        return tag;
    }

    public static MarketRequest load(CompoundTag tag) {
        MarketRequest r = new MarketRequest();
        r.id = tag.getInt("id");
        if (tag.hasUUID("requester"))
            r.requester = tag.getUUID("requester");
        r.price = tag.getLong("price");
        r.item = ItemStack.of(tag.getCompound("item"));
        return r;
    }
}
