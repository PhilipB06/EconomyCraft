package com.reazip.economycraft.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import com.reazip.economycraft.util.IdentifierCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Управляет объявлениями в магазине и доставками. */
public class ShopManager {
    private static final Gson GSON = new Gson();
    private final MinecraftServer server;
    private final Path file;
    private final Map<Integer, ShopListing> listings = new HashMap<>(); // Карта объявлений
    private final Map<UUID, List<ItemStack>> deliveries = new HashMap<>(); // Карта доставок
    private int nextId = 1;
    private final List<Runnable> listeners = new ArrayList<>(); // Слушатели изменений
    private List<ShopListing> values = new ArrayList<>();
    private ArrayList list;

    public ShopManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getFile("config/economycraft");
        Path dataDir = dir.resolve("data");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}
        this.file = dataDir.resolve("shop.json");
        load(); // Загружаем данные
    }

    /** Возвращает все объявления. */
    public Collection<ShopListing> getListings() {
        return listings.values();
    }
    public Collection<ShopListing> getPlayerListings(ServerPlayer viewer, String target) {
        values = new ArrayList<>(listings.values());
        list = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            ShopListing l = values.get(i);
            String sellerName;
            ServerPlayer sellerPlayer = viewer.level().getServer().getPlayerList().getPlayer(l.seller);
            if (sellerPlayer != null) {
                sellerName = IdentityCompat.of(sellerPlayer).name();
            } else {
                sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(l.seller);
            }
            if (sellerName != null && sellerName.equals(target)) {list.add(l);}
        }
        return list;
    }

    /** Возвращает объявление по ID. */
    public ShopListing getListing(int id) {
        return listings.get(id);
    }

    /** Добавляет новое объявление. */
    public void addListing(ShopListing listing) {
        listing.id = nextId++;
        listings.put(listing.id, listing);
        notifyListeners(); // Уведомляем слушателей
        save(); // Сохраняем
    }

    /** Удаляет объявление по ID. */
    public ShopListing removeListing(int id) {
        ShopListing l = listings.remove(id);
        if (l != null) {
            notifyListeners();
            save();
        }
        return l;
    }

    /** Уведомляет продавца о продаже его предмета. */
    public void notifySellerSale(ShopListing listing, ServerPlayer buyer) {
        if (listing == null || buyer == null) return;

        UUID sellerId = listing.seller;
        if (sellerId == null) return;

        ServerPlayer seller = server.getPlayerList().getPlayer(sellerId);
        if (seller == null) return;

        ItemStack stack = listing.item;
        int amount = (stack == null || stack.isEmpty()) ? 0 : stack.getCount();
        String itemName = (stack == null || stack.isEmpty())
                ? "предмет"
                : stack.getHoverName().getString();

        String buyerName = IdentityCompat.of(buyer).name();
        long price = listing.price;

        Component msg = Component.literal(
                "Продано " + amount + "x " + itemName +
                        " игроку " + buyerName +
                        " за " + EconomyCraft.formatMoney(price)
        ).withStyle(ChatFormatting.GREEN);

        seller.sendSystemMessage(msg);
    }

    /** Возвращает сервер. */
    public MinecraftServer server() {
        return server;
    }

    /** Добавляет доставку для игрока. */
    public void addDelivery(UUID player, ItemStack stack) {
        deliveries.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
        save();
    }

    /** Возвращает доставки для игрока без их удаления. */
    public List<ItemStack> getDeliveries(UUID player) {
        return deliveries.computeIfAbsent(player, k -> new ArrayList<>());
    }

    /** Удаляет конкретную доставку. */
    public void removeDelivery(UUID player, ItemStack stack) {
        List<ItemStack> list = deliveries.get(player);
        if (list != null) {
            list.remove(stack);
            if (list.isEmpty()) deliveries.remove(player);
            save();
        }
    }

    /** Забирает все доставки игрока. */
    public List<ItemStack> claimDeliveries(UUID player) {
        List<ItemStack> list = deliveries.remove(player);
        if (list != null) save();
        return list;
    }

    /** Проверяет, есть ли у игрока доставки. */
    public boolean hasDeliveries(UUID player) {
        List<ItemStack> list = deliveries.get(player);
        return list != null && !list.isEmpty();
    }

    /** Загружает данные из файла. */
    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                nextId = root.get("nextId").getAsInt();
                for (var el : root.getAsJsonArray("listings")) {
                    ShopListing l = ShopListing.load(el.getAsJsonObject(), server.registryAccess());
                    listings.put(l.id, l);
                }
                JsonObject dObj = root.getAsJsonObject("deliveries");
                for (String key : dObj.keySet()) {
                    UUID id = UUID.fromString(key);
                    List<ItemStack> list = new ArrayList<>();
                    for (var sEl : dObj.getAsJsonArray(key)) {
                        JsonObject o = sEl.getAsJsonObject();
                        ItemStack stack = ItemStack.EMPTY;
                        if (o.has("stack")) {
                            stack = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), o.get("stack")).result().orElse(ItemStack.EMPTY);
                        } else {
                            String itemId = o.get("item").getAsString();
                            int count = o.get("count").getAsInt();
                            IdentifierCompat.Id rl = IdentifierCompat.tryParse(itemId);
                            if (rl != null) {
                                java.util.Optional<Item> opt = IdentifierCompat.registryGetOptional(BuiltInRegistries.ITEM, rl);

                                if (opt.isPresent()) {
                                    Item item = opt.get();
                                    stack = new ItemStack(item, count);
                                }
                            }
                        }
                        if (!stack.isEmpty()) list.add(stack);
                    }
                    deliveries.put(id, list);
                }
            } catch (Exception ignored) {}
        }
    }

    /** Сохраняет данные в файл. */
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("nextId", nextId);
        JsonArray listArr = new JsonArray();
        for (ShopListing l : listings.values()) {
            listArr.add(l.save(server.registryAccess()));
        }
        root.add("listings", listArr);
        JsonObject dObj = new JsonObject();
        for (Map.Entry<UUID, List<ItemStack>> e : deliveries.entrySet()) {
            JsonArray arr = new JsonArray();
            for (ItemStack s : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                o.addProperty("count", s.getCount());
                JsonElement stackEl = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), s).result().orElse(new JsonObject());
                o.add("stack", stackEl);
                arr.add(o);
            }
            dObj.add(e.getKey().toString(), arr);
        }
        root.add("deliveries", dObj);
        try {
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ignored) {}
    }

    public void addListener(Runnable run) {
        listeners.add(run);
    }

    public void removeListener(Runnable run) {
        listeners.remove(run);
    }

    /** Уведомляет всех слушателей об изменениях. */
    private void notifyListeners() {
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }
}