package com.reazip.economycraft;

public enum LeaderboardCategory {
    BALANCE("Top Balances", "Balance", "See who is richest on the server."),
    EARNED("Top Earners", "Earned", "Who has earned the most money, from any source."),
    SPENT("Top Spenders", "Spent", "Who has spent the most money, on anything."),
    SOLD("Top Sellers", "Sold", "Who has made the most money selling items."),
    BOUGHT("Top Buyers", "Bought", "Who has spent the most money buying items."),
    TRADED("Top Traders", "Traded", "Who has moved the most money buying and selling.");

    private final String title;
    private final String metricLabel;
    private final String hint;

    LeaderboardCategory(String title, String metricLabel, String hint) {
        this.title = title;
        this.metricLabel = metricLabel;
        this.hint = hint;
    }

    public String title() {
        return title;
    }

    public String metricLabel() {
        return metricLabel;
    }

    public String hint() {
        return hint;
    }
}
