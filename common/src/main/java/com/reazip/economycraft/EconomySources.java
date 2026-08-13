package com.reazip.economycraft;

import com.reazip.economycraft.api.v1.MutationSource;

public final class EconomySources {
    public static final MutationSource PLAYER_PAYMENT = MutationSource.of("economycraft:player_payment");
    public static final MutationSource ADMIN_ADD = MutationSource.of("economycraft:admin_add");
    public static final MutationSource ADMIN_REMOVE = MutationSource.of("economycraft:admin_remove");
    public static final MutationSource ADMIN_SET = MutationSource.of("economycraft:admin_set");
    public static final MutationSource DAILY_REWARD = MutationSource.of("economycraft:daily_reward");
    public static final MutationSource PVP_REWARD = MutationSource.of("economycraft:pvp_reward");
    public static final MutationSource SERVER_SHOP_PURCHASE = MutationSource.of("economycraft:server_shop_purchase");
    public static final MutationSource SERVER_SHOP_SALE = MutationSource.of("economycraft:server_shop_sale");
    public static final MutationSource PLAYER_SHOP_PURCHASE = MutationSource.of("economycraft:player_shop_purchase");
    public static final MutationSource ORDER_FULFILLMENT = MutationSource.of("economycraft:order_fulfillment");

    private EconomySources() {}
}
