package com.reazip.economycraft.neoforge;

import net.neoforged.fml.ModList;

final class EconomyCraftNeoForgeModIds {
    private EconomyCraftNeoForgeModIds() {}

    private static final String[] PLACEHOLDER_API_NEOFORGE = {"placeholder_api_neoforge", "placeholder-api-neoforge"};

    static boolean isPlaceholderApiLoaded() {
        for (String id : PLACEHOLDER_API_NEOFORGE) {
            if (ModList.get().isLoaded(id)) return true;
        }
        return false;
    }
}
