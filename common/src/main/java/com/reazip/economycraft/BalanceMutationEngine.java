package com.reazip.economycraft;

import com.reazip.economycraft.api.v1.BalanceChangeEvent;
import com.reazip.economycraft.api.v1.BalanceMutationResult;
import com.reazip.economycraft.api.v1.BalanceMutationStatus;
import com.reazip.economycraft.api.v1.BalanceMutationType;
import com.reazip.economycraft.api.v1.MutationSource;
import com.reazip.economycraft.api.v1.PaymentResult;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class BalanceMutationEngine {
    private final Map<UUID, Long> balances;
    private final LongSupplier startingBalance;
    private final Runnable onInitialized;
    private final Runnable onMutationCommitted;
    private final BalanceEventDispatcher events;
    private final Consumer<PaymentResult> onTransfer;

    BalanceMutationEngine(
            Map<UUID, Long> balances,
            LongSupplier startingBalance,
            Runnable onInitialized,
            Runnable onMutationCommitted,
            BalanceEventDispatcher events,
            Consumer<PaymentResult> onTransfer
    ) {
        this.balances = balances;
        this.startingBalance = startingBalance;
        this.onInitialized = onInitialized;
        this.onMutationCommitted = onMutationCommitted;
        this.events = events;
        this.onTransfer = onTransfer;
    }

    long getBalance(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Long existing = balances.get(playerId);
        if (existing != null) return existing;

        long initialized = configuredStartingBalance();
        balances.put(playerId, initialized);
        onInitialized.run();
        return initialized;
    }

    BalanceMutationResult add(UUID playerId, long amount, MutationSource source) {
        Objects.requireNonNull(playerId, "playerId");
        BalanceState state = state(playerId);
        if (amount <= 0) return result(BalanceMutationStatus.INVALID_AMOUNT, BalanceMutationType.ADD,
                playerId, amount, state.balance(), state.balance(), source);
        if (amount > EconomyManager.MAX - state.balance()) {
            return result(BalanceMutationStatus.MAX_BALANCE_EXCEEDED, BalanceMutationType.ADD,
                    playerId, amount, state.balance(), state.balance(), source);
        }
        return commitSingle(state, amount, state.balance() + amount, BalanceMutationType.ADD, source);
    }

    BalanceMutationResult remove(UUID playerId, long amount, MutationSource source) {
        Objects.requireNonNull(playerId, "playerId");
        BalanceState state = state(playerId);
        if (amount <= 0) return result(BalanceMutationStatus.INVALID_AMOUNT, BalanceMutationType.REMOVE,
                playerId, amount, state.balance(), state.balance(), source);
        if (state.balance() < amount) {
            return result(BalanceMutationStatus.INSUFFICIENT_FUNDS, BalanceMutationType.REMOVE,
                    playerId, amount, state.balance(), state.balance(), source);
        }
        return commitSingle(state, amount, state.balance() - amount, BalanceMutationType.REMOVE, source);
    }

    BalanceMutationResult set(UUID playerId, long requestedBalance, MutationSource source) {
        Objects.requireNonNull(playerId, "playerId");
        BalanceState state = state(playerId);
        if (requestedBalance < 0) {
            return result(BalanceMutationStatus.INVALID_AMOUNT, BalanceMutationType.SET,
                    playerId, requestedBalance, state.balance(), state.balance(), source);
        }
        if (requestedBalance > EconomyManager.MAX) {
            return result(BalanceMutationStatus.MAX_BALANCE_EXCEEDED, BalanceMutationType.SET,
                    playerId, requestedBalance, state.balance(), state.balance(), source);
        }
        if (requestedBalance == state.balance()) {
            if (!state.existed()) {
                balances.put(playerId, state.balance());
                onInitialized.run();
            }
            return result(BalanceMutationStatus.NO_CHANGE, BalanceMutationType.SET,
                    playerId, requestedBalance, state.balance(), state.balance(), source);
        }
        return commitSingle(state, requestedBalance, requestedBalance, BalanceMutationType.SET, source);
    }

    PaymentResult pay(UUID senderId, UUID receiverId, long amount, MutationSource source) {
        return transfer(senderId, receiverId, amount, amount, source);
    }

    PaymentResult transfer(UUID senderId, UUID receiverId, long debitAmount, long creditAmount, MutationSource source) {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(receiverId, "receiverId");
        BalanceState sender = state(senderId);
        BalanceState receiver = state(receiverId);

        if (senderId.equals(receiverId)) {
            return paymentResult(BalanceMutationStatus.SAME_PLAYER, senderId, receiverId, debitAmount,
                    sender.balance(), sender.balance(), receiver.balance(), receiver.balance(), source);
        }
        if (debitAmount <= 0 || creditAmount < 0) {
            return paymentResult(BalanceMutationStatus.INVALID_AMOUNT, senderId, receiverId, debitAmount,
                    sender.balance(), sender.balance(), receiver.balance(), receiver.balance(), source);
        }
        if (sender.balance() < debitAmount) {
            return paymentResult(BalanceMutationStatus.INSUFFICIENT_FUNDS, senderId, receiverId, debitAmount,
                    sender.balance(), sender.balance(), receiver.balance(), receiver.balance(), source);
        }
        if (creditAmount > EconomyManager.MAX - receiver.balance()) {
            return paymentResult(BalanceMutationStatus.MAX_BALANCE_EXCEEDED, senderId, receiverId, debitAmount,
                    sender.balance(), sender.balance(), receiver.balance(), receiver.balance(), source);
        }

        long senderNew = sender.balance() - debitAmount;
        long receiverNew = receiver.balance() + creditAmount;
        balances.put(senderId, senderNew);
        if (creditAmount > 0) balances.put(receiverId, receiverNew);
        onMutationCommitted.run();

        Optional<MutationSource> optionalSource = Optional.ofNullable(source);
        PaymentResult result = new PaymentResult(BalanceMutationStatus.SUCCESS, senderId, receiverId, debitAmount,
                sender.balance(), senderNew, receiver.balance(), receiverNew, optionalSource);
        events.emit(new BalanceChangeEvent(senderId, sender.balance(), senderNew,
                BalanceMutationType.PAYMENT_SENT, Optional.of(receiverId), optionalSource));
        if (creditAmount > 0) {
            events.emit(new BalanceChangeEvent(receiverId, receiver.balance(), receiverNew,
                    BalanceMutationType.PAYMENT_RECEIVED, Optional.of(senderId), optionalSource));
        }
        onTransfer.accept(result);
        return result;
    }

    void delete(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (balances.remove(playerId) != null) onMutationCommitted.run();
    }

    private BalanceMutationResult commitSingle(
            BalanceState state,
            long requestedAmount,
            long newBalance,
            BalanceMutationType type,
            MutationSource source
    ) {
        balances.put(state.playerId(), newBalance);
        onMutationCommitted.run();
        Optional<MutationSource> optionalSource = Optional.ofNullable(source);
        BalanceMutationResult result = new BalanceMutationResult(BalanceMutationStatus.SUCCESS, type,
                state.playerId(), requestedAmount, state.balance(), newBalance, optionalSource);
        events.emit(new BalanceChangeEvent(state.playerId(), state.balance(), newBalance, type,
                Optional.empty(), optionalSource));
        return result;
    }

    private BalanceState state(UUID playerId) {
        Long existing = balances.get(playerId);
        return existing == null
                ? new BalanceState(playerId, configuredStartingBalance(), false)
                : new BalanceState(playerId, existing, true);
    }

    private long configuredStartingBalance() {
        return Math.clamp(startingBalance.getAsLong(), 0, EconomyManager.MAX);
    }

    private static BalanceMutationResult result(
            BalanceMutationStatus status,
            BalanceMutationType type,
            UUID playerId,
            long requestedAmount,
            long previousBalance,
            long newBalance,
            MutationSource source
    ) {
        return new BalanceMutationResult(status, type, playerId, requestedAmount,
                previousBalance, newBalance, Optional.ofNullable(source));
    }

    private static PaymentResult paymentResult(
            BalanceMutationStatus status,
            UUID senderId,
            UUID receiverId,
            long amount,
            long senderPrevious,
            long senderNew,
            long receiverPrevious,
            long receiverNew,
            MutationSource source
    ) {
        return new PaymentResult(status, senderId, receiverId, amount, senderPrevious, senderNew,
                receiverPrevious, receiverNew, Optional.ofNullable(source));
    }

    private record BalanceState(UUID playerId, long balance, boolean existed) {}
}
