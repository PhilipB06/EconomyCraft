package com.reazip.economycraft.api.v1;

import java.util.UUID;

public interface BalanceApi {
    long getBalance(UUID playerId);

    long getMaximumBalance();

    BalanceMutationResult addMoney(UUID playerId, long amount);

    BalanceMutationResult addMoney(UUID playerId, long amount, MutationSource source);

    BalanceMutationResult removeMoney(UUID playerId, long amount);

    BalanceMutationResult removeMoney(UUID playerId, long amount, MutationSource source);

    BalanceMutationResult setMoney(UUID playerId, long balance);

    BalanceMutationResult setMoney(UUID playerId, long balance, MutationSource source);

    PaymentResult pay(UUID senderId, UUID receiverId, long amount);

    PaymentResult pay(UUID senderId, UUID receiverId, long amount, MutationSource source);
}
