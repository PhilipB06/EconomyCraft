package com.reazip.economycraft.orders;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;


import java.util.UUID;

public class OrderRequest {
    public int id;
    public UUID requester;
    public ItemStack item;
    public int amount;
    public long price;

    public JsonObject save(HolderLookup.Provider provider) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (requester != null) obj.addProperty("requester", requester.toString());
        obj.addProperty("price", price);
        obj.addProperty("item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        obj.addProperty("amount", amount);
        JsonElement stackEl = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, provider), item).result().orElse(new JsonObject());
        obj.add("stack", stackEl);
        return obj;
    }

    public static OrderRequest load(JsonObject obj, HolderLookup.Provider provider) {
        OrderRequest r = new OrderRequest();
        r.id = obj.get("id").getAsInt();
        if (obj.has("requester")) r.requester = UUID.fromString(obj.get("requester").getAsString());
        r.price = obj.get("price").getAsLong();
        r.amount = obj.get("amount").getAsInt();
        if (obj.has("stack")) {
            r.item = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, provider), obj.get("stack")).result().orElse(ItemStack.EMPTY);
        }
        if (r.item == null || r.item.isEmpty()) {
            String itemId = obj.get("item").getAsString();
            Identifier rl = Identifier.tryParse(itemId);
            if (rl != null) {
                java.util.Optional<net.minecraft.world.item.Item> opt = BuiltInRegistries.ITEM.getOptional(rl);
                opt.ifPresent(item -> r.item = new ItemStack(item));
            }
        }
        return r;
    }
}
