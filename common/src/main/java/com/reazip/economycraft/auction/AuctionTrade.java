package com.reazip.economycraft.auction;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.EconomySources;
import com.reazip.economycraft.api.v1.PaymentResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class AuctionTrade {
    private AuctionTrade() {}

    public enum PurchaseStatus { OK, LISTING_GONE, OWN_LISTING, CANT_AFFORD, SELLER_CANT_RECEIVE }

    public record PurchaseResult(PurchaseStatus status, ItemStack item, long totalPaid, UUID seller, boolean stored) {
        public boolean success() {
            return status == PurchaseStatus.OK;
        }
    }

    public enum CancelStatus { OK, LISTING_GONE, NOT_OWNER }

    public record CancelResult(CancelStatus status, ItemStack item, boolean stored) {
        public boolean success() {
            return status == CancelStatus.OK;
        }
    }

    public static PurchaseResult purchase(EconomyManager eco, ServerPlayer buyer, int listingId) {
        AuctionManager auctions = eco.getAuctions();
        AuctionListing peek = auctions.getListing(listingId);
        if (peek == null) {
            return new PurchaseResult(PurchaseStatus.LISTING_GONE, ItemStack.EMPTY, 0, null, false);
        }
        if (peek.seller.equals(buyer.getUUID())) {
            return new PurchaseResult(PurchaseStatus.OWN_LISTING, peek.item.copy(), 0, peek.seller, false);
        }

        AuctionListing claimed = auctions.removeListing(listingId);
        if (claimed == null) {
            return new PurchaseResult(PurchaseStatus.LISTING_GONE, ItemStack.EMPTY, 0, null, false);
        }

        long cost = claimed.price;
        long tax = Math.round(cost * EconomyConfig.get().taxRate);
        long total = cost + tax;

        PaymentResult payment = eco.transferMoney(buyer.getUUID(), claimed.seller, total, cost, EconomySources.AUCTION_PURCHASE);
        if (!payment.successful()) {
            auctions.restoreListing(claimed);
            PurchaseStatus status = payment.status() == com.reazip.economycraft.api.v1.BalanceMutationStatus.MAX_BALANCE_EXCEEDED
                    ? PurchaseStatus.SELLER_CANT_RECEIVE : PurchaseStatus.CANT_AFFORD;
            return new PurchaseResult(status, claimed.item.copy(), 0, claimed.seller, false);
        }

        auctions.notifySellerSale(claimed, buyer);

        boolean stored = deliverOrStore(auctions, buyer, claimed.item.copy());
        return new PurchaseResult(PurchaseStatus.OK, claimed.item.copy(), total, claimed.seller, stored);
    }

    public static CancelResult cancel(AuctionManager auctions, ServerPlayer seller, int listingId) {
        AuctionListing peek = auctions.getListing(listingId);
        if (peek == null) {
            return new CancelResult(CancelStatus.LISTING_GONE, ItemStack.EMPTY, false);
        }
        if (!peek.seller.equals(seller.getUUID())) {
            return new CancelResult(CancelStatus.NOT_OWNER, ItemStack.EMPTY, false);
        }

        AuctionListing removed = auctions.removeListing(listingId);
        if (removed == null) {
            return new CancelResult(CancelStatus.LISTING_GONE, ItemStack.EMPTY, false);
        }

        boolean stored = deliverOrStore(auctions, seller, removed.item.copy());
        return new CancelResult(CancelStatus.OK, removed.item.copy(), stored);
    }

    private static boolean deliverOrStore(AuctionManager auctions, ServerPlayer player, ItemStack stack) {
        boolean stored = !player.getInventory().add(stack);
        if (stored) {
            auctions.addDelivery(player.getUUID(), stack);
        }
        return stored;
    }
}
