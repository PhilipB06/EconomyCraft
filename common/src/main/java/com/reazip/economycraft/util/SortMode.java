package com.reazip.economycraft.util;

public enum SortMode {
    DEFAULT,
    PRICE_ASC,
    PRICE_DESC;

    public SortMode next() {
        SortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
