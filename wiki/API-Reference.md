# API reference

All public v1 types are in:

```java
com.reazip.economycraft.api.v1
```

Implementation and provider classes are internal. Other mods should only import the types listed here.

---

## EconomyCraftApi

```java
public interface EconomyCraftApi {
    static EconomyCraftApi get(MinecraftServer server);

    BalanceApi balances();
    PriceApi prices();
    LeaderboardApi leaderboard();
    BalanceEvents balanceEvents();
    String formatMoney(long amount);
}
```

---

## BalanceApi

```java
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
```

---

## BalanceMutationResult

```java
public record BalanceMutationResult(
        BalanceMutationStatus status,
        BalanceMutationType type,
        UUID playerId,
        long requestedAmount,
        long previousBalance,
        long newBalance,
        Optional<MutationSource> source
) {
    boolean successful();
    long difference();
}
```

## PaymentResult

```java
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
    boolean successful();
    long senderDifference();
    long receiverDifference();
}
```

## BalanceMutationStatus

```java
public enum BalanceMutationStatus {
    SUCCESS,
    NO_CHANGE,
    INVALID_AMOUNT,
    INSUFFICIENT_FUNDS,
    SAME_PLAYER,
    MAX_BALANCE_EXCEEDED
}
```

## BalanceMutationType

```java
public enum BalanceMutationType {
    ADD,
    REMOVE,
    SET,
    PAYMENT_SENT,
    PAYMENT_RECEIVED
}
```

## MutationSource

```java
public record MutationSource(String namespace, String reason) {
    static MutationSource of(String namespacedReason);
    String asString();
}
```

---

## PriceApi

```java
public interface PriceApi {
    Optional<ItemPrice> resolve(ItemStack stack);
    List<String> categories();
    List<ItemPrice> entries(String category);
}
```

## ItemPrice

```java
public final class ItemPrice {
    public ItemPrice(
            String key,
            String itemId,
            String category,
            int bulkAmount,
            OptionalLong unitBuyPrice,
            OptionalLong unitSellPrice,
            boolean customItem,
            ItemStack prototype
    );

    public String key();
    public String itemId();
    public String category();
    public int bulkAmount();
    public OptionalLong unitBuyPrice();
    public OptionalLong unitSellPrice();
    public boolean hasBuyPrice();
    public boolean hasSellPrice();
    public boolean customItem();
    public ItemStack prototype();
}
```

The constructor copies `prototype`. The `prototype()` accessor returns another copy.

---

## LeaderboardApi

```java
public interface LeaderboardApi {
    List<LeaderboardEntry> getLeaderboardEntries(int limit);
    Optional<LeaderboardEntry> getLeaderboardEntry(int rank);
}
```

## LeaderboardEntry

```java
public record LeaderboardEntry(UUID playerId, long balance) {}
```

---

## BalanceEvents

```java
public interface BalanceEvents {
    ListenerRegistration register(BalanceChangeListener listener);
}
```

## BalanceChangeListener

```java
@FunctionalInterface
public interface BalanceChangeListener {
    void onBalanceChanged(BalanceChangeEvent event);
}
```

## BalanceChangeEvent

```java
public record BalanceChangeEvent(
        UUID playerId,
        long previousBalance,
        long newBalance,
        BalanceMutationType type,
        Optional<UUID> counterpartyId,
        Optional<MutationSource> source,
        Optional<String> detail
) {
    long difference();
}
```

`detail` is a short, human-readable description of what caused the change (e.g. `"12x Iron Ingot"`) when EconomyCraft's own shop, auction or order features triggered it. It's empty for payments, admin commands, rewards, and for changes made through `addMoney`/`removeMoney`/`setMoney`/`pay` without a detail argument.

## ListenerRegistration

```java
public interface ListenerRegistration extends AutoCloseable {
    void unregister();
    void close();
}
```

---

## General contract

- All calls are server-thread-only.
- UUID arguments support offline players.
- Calling mods handle their own permissions.
- Normal absent values use `Optional`, `OptionalLong` or an empty immutable list instead of `null`.
- Failed mutations do not change or implicitly save balances.
- Returned price and leaderboard data is immutable.
- Balance events are successful after-events and are not cancellable.
