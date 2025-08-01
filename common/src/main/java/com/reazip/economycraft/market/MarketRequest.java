package com.reazip.economycraft.market;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class MarketRequest {
    public int id;
    public UUID requester;
    public ItemStack item;
    public long price;

    public JsonObject save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (requester != null) obj.addProperty("requester", requester.toString());
        obj.addProperty("price", price);
        obj.addProperty("item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        obj.addProperty("count", item.getCount());
        return obj;
    }

    public static MarketRequest load(JsonObject obj) {
        MarketRequest r = new MarketRequest();
        r.id = obj.get("id").getAsInt();
        if (obj.has("requester")) r.requester = UUID.fromString(obj.get("requester").getAsString());
        r.price = obj.get("price").getAsLong();
        String itemId = obj.get("item").getAsString();
        int count = obj.get("count").getAsInt();
        BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)).ifPresent(h -> r.item = new ItemStack(h.value(), count));
        return r;
    }
}
