package com.reazip.economycraft.api.v1;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Common-loader bootstrap. This is implementation infrastructure, not part of the API contract. */
public final class EconomyCraftApiBootstrap {
    private static final EconomyCraftApiAccess.Provider PROVIDER = EconomyCraftApiImpl::new;
    public static final Object INITIALIZED;

    static {
        EconomyCraftApiAccess.install(PROVIDER);
        INITIALIZED = new Object();
    }

    private EconomyCraftApiBootstrap() {}
}

final class EconomyCraftApiImpl implements EconomyCraftApi {
    private final MinecraftServer server;
    private final BalanceApi balances;
    private final PriceApi prices;
    private final LeaderboardApi leaderboard;
    private final BalanceEvents events;

    EconomyCraftApiImpl(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("EconomyCraft API must be called from the server thread");
        }
        this.server = server;
        this.balances = new BalanceApiImpl(server);
        this.prices = new PriceApiImpl(server);
        this.leaderboard = new LeaderboardApiImpl(server);
        this.events = listener -> {
            requireServerThread();
            ListenerRegistration registration = manager().getBalanceEvents().register(listener);
            return () -> {
                requireServerThread();
                registration.unregister();
            };
        };
    }

    private EconomyManager manager() {
        requireServerThread();
        return EconomyCraft.getManager(server);
    }

    private void requireServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("EconomyCraft API must be called from the server thread");
        }
    }

    @Override
    public BalanceApi balances() {
        requireServerThread();
        return balances;
    }

    @Override
    public PriceApi prices() {
        requireServerThread();
        return prices;
    }

    @Override
    public LeaderboardApi leaderboard() {
        requireServerThread();
        return leaderboard;
    }

    @Override
    public BalanceEvents balanceEvents() {
        requireServerThread();
        return events;
    }

    @Override
    public String formatMoney(long amount) {
        requireServerThread();
        return EconomyCraft.formatMoney(amount);
    }
}

final class BalanceApiImpl implements BalanceApi {
    private final MinecraftServer server;

    BalanceApiImpl(MinecraftServer server) {
        this.server = server;
    }

    private EconomyManager manager() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("EconomyCraft API must be called from the server thread");
        }
        return EconomyCraft.getManager(server);
    }

    @Override
    public long getBalance(java.util.UUID playerId) {
        return manager().getBalance(Objects.requireNonNull(playerId, "playerId"), true);
    }

    @Override
    public long getMaximumBalance() {
        manager().requireServerThread();
        return EconomyManager.MAX;
    }

    @Override
    public BalanceMutationResult addMoney(java.util.UUID playerId, long amount) {
        return manager().addMoney(playerId, amount, null);
    }

    @Override
    public BalanceMutationResult addMoney(java.util.UUID playerId, long amount, MutationSource source) {
        return manager().addMoney(playerId, amount, Objects.requireNonNull(source, "source"));
    }

    @Override
    public BalanceMutationResult removeMoney(java.util.UUID playerId, long amount) {
        return manager().removeMoney(playerId, amount, null);
    }

    @Override
    public BalanceMutationResult removeMoney(java.util.UUID playerId, long amount, MutationSource source) {
        return manager().removeMoney(playerId, amount, Objects.requireNonNull(source, "source"));
    }

    @Override
    public BalanceMutationResult setMoney(java.util.UUID playerId, long balance) {
        return manager().setMoney(playerId, balance, null);
    }

    @Override
    public BalanceMutationResult setMoney(java.util.UUID playerId, long balance, MutationSource source) {
        return manager().setMoney(playerId, balance, Objects.requireNonNull(source, "source"));
    }

    @Override
    public PaymentResult pay(java.util.UUID senderId, java.util.UUID receiverId, long amount) {
        return manager().pay(senderId, receiverId, amount, null);
    }

    @Override
    public PaymentResult pay(java.util.UUID senderId, java.util.UUID receiverId, long amount,
                             MutationSource source) {
        return manager().pay(senderId, receiverId, amount, Objects.requireNonNull(source, "source"));
    }
}

final class PriceApiImpl implements PriceApi {
    private final MinecraftServer server;

    PriceApiImpl(MinecraftServer server) {
        this.server = server;
    }

    private EconomyManager manager() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("EconomyCraft API must be called from the server thread");
        }
        return EconomyCraft.getManager(server);
    }

    @Override
    public Optional<ItemPrice> resolve(ItemStack stack) {
        EconomyManager manager = manager();
        manager.requireServerThread();
        if (stack == null || stack.isEmpty()) return Optional.empty();
        PriceRegistry.PriceEntry entry = manager.getPrices().resolve(stack);
        return entry == null ? Optional.empty() : Optional.of(toApi(entry));
    }

    @Override
    public List<String> categories() {
        EconomyManager manager = manager();
        manager.requireServerThread();
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (PriceRegistry.PriceEntry entry : manager.getPrices().allEntries()) {
            categories.add(entry.category());
        }
        return List.copyOf(categories);
    }

    @Override
    public List<ItemPrice> entries(String category) {
        EconomyManager manager = manager();
        manager.requireServerThread();
        if (category == null) return List.of();
        List<ItemPrice> entries = new ArrayList<>();
        for (PriceRegistry.PriceEntry entry : manager.getPrices().allByCategory(category)) {
            entries.add(toApi(entry));
        }
        return List.copyOf(entries);
    }

    private ItemPrice toApi(PriceRegistry.PriceEntry entry) {
        EconomyManager manager = manager();
        return new ItemPrice(
                entry.key(),
                entry.id().asString(),
                entry.category(),
                entry.stack(),
                entry.unitBuy() > 0 ? OptionalLong.of(entry.unitBuy()) : OptionalLong.empty(),
                entry.unitSell() > 0 ? OptionalLong.of(entry.unitSell()) : OptionalLong.empty(),
                entry.customItem() != null,
                manager.getPrices().createPrototype(entry)
        );
    }
}

final class LeaderboardApiImpl implements LeaderboardApi {
    private final MinecraftServer server;

    LeaderboardApiImpl(MinecraftServer server) {
        this.server = server;
    }

    private EconomyManager manager() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("EconomyCraft API must be called from the server thread");
        }
        return EconomyCraft.getManager(server);
    }

    @Override
    public List<LeaderboardEntry> getLeaderboardEntries(int limit) {
        EconomyManager manager = manager();
        manager.requireServerThread();
        if (limit <= 0) return List.of();
        List<LeaderboardEntry> entries = manager.getBalancesSnapshot().entrySet().stream()
                .map(entry -> new LeaderboardEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(LeaderboardEntry::balance).reversed()
                        .thenComparing(LeaderboardEntry::playerId))
                .limit(limit)
                .toList();
        return List.copyOf(entries);
    }

    @Override
    public Optional<LeaderboardEntry> getLeaderboardEntry(int rank) {
        if (rank < 1) return Optional.empty();
        List<LeaderboardEntry> entries = getLeaderboardEntries(rank);
        return entries.size() < rank ? Optional.empty() : Optional.of(entries.get(rank - 1));
    }
}
