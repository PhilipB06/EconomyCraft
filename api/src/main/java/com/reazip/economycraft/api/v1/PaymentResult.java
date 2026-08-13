package com.reazip.economycraft.api.v1;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PaymentResult(
        BalanceMutationStatus status,
        UUID senderId,
        UUID receiverId,
        long amount,
        long senderPreviousBalance,
        long senderNewBalance,
        long receiverPreviousBalance,
        long receiverNewBalance,
        Optional<MutationSource> source
) {
    public PaymentResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(receiverId, "receiverId");
        source = Objects.requireNonNull(source, "source");
    }

    public boolean successful() {
        return status == BalanceMutationStatus.SUCCESS || status == BalanceMutationStatus.NO_CHANGE;
    }

    public long senderDifference() {
        return senderNewBalance - senderPreviousBalance;
    }

    public long receiverDifference() {
        return receiverNewBalance - receiverPreviousBalance;
    }
}
