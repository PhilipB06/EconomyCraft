package com.reazip.economycraft.auction;

import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ExpirationUtil;
import net.minecraft.world.item.ItemStack;

public final class AuctionExpiration {
    private AuctionExpiration() {}

    public static void expireOverdue(EconomyManager eco) {
        AuctionManager auctions = eco.getAuctions();
        long now = System.currentTimeMillis();
        for (AuctionListing listing : auctions.getListings()) {
            if (!ExpirationUtil.isExpired(listing.expiresAt, now)) continue;

            AuctionListing removed = auctions.removeListing(listing.id);
            if (removed == null) continue;

            ItemStack stack = removed.item.copy();
            auctions.addDelivery(removed.seller, stack);
            notifyExpired(eco, removed, stack);
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
