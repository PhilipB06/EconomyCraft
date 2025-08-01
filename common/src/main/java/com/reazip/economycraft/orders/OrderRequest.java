package com.reazip.economycraft.orders;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class OrderRequest {
    public int id;
    public UUID requester;
    public ItemStack item;
    public int amount;
    public long price;

    public JsonObject save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (requester != null) obj.addProperty("requester", requester.toString());
        obj.addProperty("price", price);
        obj.addProperty("item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        obj.addProperty("amount", amount);
        return obj;
    }

    public static OrderRequest load(JsonObject obj) {
        OrderRequest r = new OrderRequest();
        r.id = obj.get("id").getAsInt();
        if (obj.has("requester")) r.requester = UUID.fromString(obj.get("requester").getAsString());
        r.price = obj.get("price").getAsLong();
        String itemId = obj.get("item").getAsString();
        r.amount = obj.get("amount").getAsInt();
        BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)).ifPresent(h -> r.item = new ItemStack(h.value()));
        return r;
    }
}
