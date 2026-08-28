package com.reazip.economycraft;

import com.reazip.economycraft.util.TransactionEntry;

public enum TransactionCategory {
    ALL("All"),
    PAYMENTS("Payments"),
    SHOP("Shop"),
    AUCTION("Auction"),
    ORDERS("Orders"),
    ADMIN("Admin"),
    REWARDS("Rewards");

    private final String label;

    TransactionCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public TransactionCategory next() {
        TransactionCategory[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean matches(TransactionEntry entry) {
        return this == ALL || this == TransactionsUi.styleFor(entry).category();
    }
}
