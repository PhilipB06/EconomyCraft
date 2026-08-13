package com.reazip.economycraft.api.v1;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record MutationSource(String namespace, String reason) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern REASON = Pattern.compile("[a-z0-9/._-]+");

    public MutationSource {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(reason, "reason");
        if (!namespace.equals(namespace.toLowerCase(Locale.ROOT)) || !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid mutation source namespace: " + namespace);
        }
        if (!reason.equals(reason.toLowerCase(Locale.ROOT)) || !REASON.matcher(reason).matches()) {
            throw new IllegalArgumentException("Invalid mutation source reason: " + reason);
        }
    }

    public static MutationSource of(String namespacedReason) {
        Objects.requireNonNull(namespacedReason, "namespacedReason");
        int separator = namespacedReason.indexOf(':');
        if (separator <= 0 || separator != namespacedReason.lastIndexOf(':') || separator == namespacedReason.length() - 1) {
            throw new IllegalArgumentException("Expected a namespaced reason such as example:reward");
        }
        return new MutationSource(namespacedReason.substring(0, separator), namespacedReason.substring(separator + 1));
    }

    public String asString() {
        return namespace + ":" + reason;
    }
}
