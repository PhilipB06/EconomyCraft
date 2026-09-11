package com.reazip.economycraft.fabric.client;

import com.reazip.economycraft.client.ShopSearchOverlay;
import net.fabricmc.api.ClientModInitializer;

public final class EconomyCraftFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ShopSearchOverlay.register();
    }
}
