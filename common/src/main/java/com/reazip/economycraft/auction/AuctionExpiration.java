package com.reazip.economycraft.auction;

import com.reazip.economycraft.DeliveryManager;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ExpirationUtil;
import net.minecraft.world.item.ItemStack;

public final class AuctionExpiration {
    private AuctionExpiration() {}

    public static void expireOverdue(EconomyManager eco) {
        AuctionManager auctions = eco.getAuctions();
        DeliveryManager deliveries = eco.getDeliveries();
        long now = System.currentTimeMillis();
        boolean anyExpired = false;
        for (AuctionListing listing : auctions.getListings()) {
            if (!ExpirationUtil.isExpired(listing.expiresAt, now)) continue;

            AuctionListing removed = auctions.removeListing(listing.id, false);
            if (removed == null) continue;
            anyExpired = true;

            ItemStack stack = removed.item.copy();
            auctions.addDelivery(removed.seller, stack, false);
            notifyExpired(eco, removed, stack);
        }
        if (anyExpired) {
            auctions.save();
            deliveries.save();
        }
        eco.getNotifications().flush();
    }

    private static void notifyExpired(EconomyManager eco, AuctionListing listing, ItemStack stack) {
        String itemName = stack.getHoverName().getString();
        String message = "Your auction listing for " + stack.getCount() + "x " + itemName
                + " expired; the item was returned to your deliveries.";
        eco.getNotifications().notify(listing.seller, message);
    }
}
