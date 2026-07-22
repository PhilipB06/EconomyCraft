package com.reazip.economycraft.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Builds an {@link ItemStack} from a parsed {@link ItemInput} across the version where
 * {@code createItemStack} takes a stackable-size validation flag and the version where it doesn't.
 */
public final class ItemArgumentCompat {
    private static final Method CREATE_ITEM_STACK;

    static {
        Method found = null;
        for (Method method : ItemInput.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 1 || params[0] != int.class) continue;
            if (method.getReturnType() != ItemStack.class) continue;
            found = method;
            if (params.length == 1) break;
        }
        if (found == null) {
            throw new ExceptionInInitializerError("ItemInput.createItemStack-shaped method not found");
        }
        CREATE_ITEM_STACK = found;
    }

    private ItemArgumentCompat() {}

    public static ItemStack createItemStack(ItemInput input, int count) throws CommandSyntaxException {
        Object[] args = CREATE_ITEM_STACK.getParameterCount() == 1
                ? new Object[]{count}
                : new Object[]{count, false};
        try {
            return (ItemStack) CREATE_ITEM_STACK.invoke(input, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof CommandSyntaxException cse) throw cse;
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
