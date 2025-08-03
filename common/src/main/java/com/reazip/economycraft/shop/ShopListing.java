package com.reazip.economycraft.shop;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Listing for one item in the shop. */
public class ShopListing {
    public int id;
    public UUID seller;
    public ItemStack item;
    public long price;

    public JsonObject save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (seller != null) obj.addProperty("seller", seller.toString());
        obj.addProperty("price", price);
        obj.addProperty("item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        obj.addProperty("count", item.getCount());
        return obj;
    }

    public static ShopListing load(JsonObject obj) {
        ShopListing l = new ShopListing();
        l.id = obj.get("id").getAsInt();
        if (obj.has("seller")) l.seller = UUID.fromString(obj.get("seller").getAsString());
        l.price = obj.get("price").getAsLong();
        String itemId = obj.get("item").getAsString();
        int count = obj.get("count").getAsInt();
        BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)).ifPresent(h -> l.item = new ItemStack(h.value(), count));
        return l;
    }
}
