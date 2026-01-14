package com.reazip.economycraft.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class ItemStackCompat {
    private ItemStackCompat() {}

    public static void setCustomName(ItemStack stack, Component name) {
        stack.setHoverName(name);
    }

    public static void setLore(ItemStack stack, List<Component> lore) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag loreTag = new ListTag();
        for (Component line : lore) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        display.put("Lore", loreTag);
    }

    public static void setSkullOwner(ItemStack stack, @Nullable GameProfile profile, @Nullable String fallbackName) {
        CompoundTag tag = stack.getOrCreateTag();
        if (profile != null) {
            tag.put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), profile));
            return;
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            tag.putString("SkullOwner", fallbackName);
        }
    }

    public static boolean hasContainerContents(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        if (tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            CompoundTag blockTag = tag.getCompound("BlockEntityTag");
            if (blockTag.contains("Items", Tag.TAG_LIST)
                    && !blockTag.getList("Items", Tag.TAG_COMPOUND).isEmpty()) {
                return true;
            }
        }
        return tag.contains("Items", Tag.TAG_LIST) && !tag.getList("Items", Tag.TAG_COMPOUND).isEmpty();
    }

    public static Map<Enchantment, Integer> getEnchantments(ItemStack stack) {
        return EnchantmentHelper.getEnchantments(stack);
    }

    public static Potion getPotion(ItemStack stack) {
        return PotionUtils.getPotion(stack);
    }

    public static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
