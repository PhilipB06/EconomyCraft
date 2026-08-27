package com.reazip.economycraft.orders;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class OrderRequest {
    public int id;
    public UUID requester;
    public ItemStack item;
    public int amount;
    public long price;
    public long escrow;
    public long createdAt;
    public long expiresAt;

    public JsonObject save(HolderLookup.Provider provider) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (requester != null) obj.addProperty("requester", requester.toString());
        obj.addProperty("price", price);
        obj.addProperty("amount", amount);
        obj.addProperty("escrow", escrow);
        obj.addProperty("createdAt", createdAt);
        obj.addProperty("expiresAt", expiresAt);
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
        if (obj.has("escrow")) {
            r.escrow = obj.get("escrow").getAsLong();
        }
        if (obj.has("createdAt")) r.createdAt = obj.get("createdAt").getAsLong();
        if (obj.has("expiresAt")) r.expiresAt = obj.get("expiresAt").getAsLong();
        r.item = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, provider), obj.get("stack")).result().orElse(ItemStack.EMPTY);
        return r;
    }
}
