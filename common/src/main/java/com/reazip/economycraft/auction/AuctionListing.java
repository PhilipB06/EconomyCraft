package com.reazip.economycraft.auction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class AuctionListing {
    public int id;
    public UUID seller;
    public ItemStack item;
    public long price;
    public long createdAt;
    public long expiresAt;

    public JsonObject save(HolderLookup.Provider provider) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (seller != null) obj.addProperty("seller", seller.toString());
        obj.addProperty("price", price);
        obj.addProperty("createdAt", createdAt);
        obj.addProperty("expiresAt", expiresAt);
        JsonElement stackEl = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, provider), item).result().orElse(new JsonObject());
        obj.add("stack", stackEl);
        return obj;
    }

    public static AuctionListing load(JsonObject obj, HolderLookup.Provider provider) {
        AuctionListing l = new AuctionListing();
        l.id = obj.get("id").getAsInt();
        if (obj.has("seller")) l.seller = UUID.fromString(obj.get("seller").getAsString());
        l.price = obj.get("price").getAsLong();
        if (obj.has("createdAt")) l.createdAt = obj.get("createdAt").getAsLong();
        if (obj.has("expiresAt")) l.expiresAt = obj.get("expiresAt").getAsLong();
        l.item = ItemStack.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, provider), obj.get("stack"))
                .result()
                .orElse(ItemStack.EMPTY);
        return l;
    }
}
