package com.reazip.economycraft.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import com.reazip.economycraft.util.IdentifierCompat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;


import java.util.UUID;

/** Объявление о продаже одного предмета в магазине. */
public class ShopListing {
    public int id;                 // Уникальный идентификатор объявления
    public UUID seller;           // Продавец
    public ItemStack item;        // Предмет на продажу
    public long price;            // Цена

    /**
     * Сохраняет объявление в JSON-объект.
     * @param provider провайдер для доступа к реестрам
     * @return JSON-объект с данными объявления
     */
    public JsonObject save(HolderLookup.Provider provider) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (seller != null) obj.addProperty("seller", seller.toString());
        obj.addProperty("price", price);
        obj.addProperty("item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        obj.addProperty("count", item.getCount());
        JsonElement stackEl = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, provider), item).result().orElse(new JsonObject());
        obj.add("stack", stackEl);
        return obj;
    }

    /**
     * Загружает объявление из JSON-объекта.
     * @param obj JSON-объект с данными
     * @param provider провайдер для доступа к реестрам
     * @return загруженное объявление
     */
    public static ShopListing load(JsonObject obj, HolderLookup.Provider provider) {
        ShopListing l = new ShopListing();
        l.id = obj.get("id").getAsInt();
        if (obj.has("seller")) l.seller = UUID.fromString(obj.get("seller").getAsString());
        l.price = obj.get("price").getAsLong();
        
        // Пытаемся загрузить ItemStack через полный кодек
        if (obj.has("stack")) {
            l.item = ItemStack.CODEC
                    .parse(RegistryOps.create(JsonOps.INSTANCE, provider), obj.get("stack"))
                    .result()
                    .orElse(ItemStack.EMPTY);
        }
        
        // Резервный способ загрузки (если нет stack или он не загрузился)
        if (l.item == null || l.item.isEmpty()) {
            String itemId = obj.get("item").getAsString();
            int count = obj.get("count").getAsInt();
            IdentifierCompat.Id rl = IdentifierCompat.tryParse(itemId);

            if (rl != null) {
                java.util.Optional<Item> opt = IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, rl);
                opt.ifPresent(item -> l.item = new ItemStack(item, count));
            }
        }
        return l;
    }
}