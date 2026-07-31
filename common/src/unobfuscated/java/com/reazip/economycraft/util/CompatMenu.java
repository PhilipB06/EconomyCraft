package com.reazip.economycraft.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public abstract class CompatMenu extends AbstractContainerMenu {
    protected CompatMenu(MenuType<?> type, int id) {
        super(type, id);
    }

    @Override
    public void clicked(int slot, int dragType, ContainerInput type, Player player) {
        ClickKind kind = switch (type) {
            case PICKUP -> ClickKind.PICKUP;
            case QUICK_MOVE -> ClickKind.QUICK_MOVE;
            case THROW -> ClickKind.THROW;
            default -> ClickKind.OTHER;
        };
        if (onClick(slot, dragType, kind, player)) return;
        super.clicked(slot, dragType, type, player);
        afterClick(slot, dragType, kind, player);
    }

    protected abstract boolean onClick(int slot, int dragType, ClickKind kind, Player player);

    protected void afterClick(int slot, int dragType, ClickKind kind, Player player) {}

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
