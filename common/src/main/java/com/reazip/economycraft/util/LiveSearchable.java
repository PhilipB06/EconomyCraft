package com.reazip.economycraft.util;

import net.minecraft.server.level.ServerPlayer;

public interface LiveSearchable {
    void applySearch(String query);

    static boolean apply(ServerPlayer player, String query) {
        if (player.containerMenu instanceof LiveSearchable searchable) {
            searchable.applySearch(MenuUiSupport.normalizeSearch(query));
            return true;
        }
        return false;
    }
}
