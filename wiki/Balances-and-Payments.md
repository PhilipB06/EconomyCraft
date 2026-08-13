# Balances and payments

Get the balance service from the server's API:

```java
BalanceApi balances = EconomyCraftApi.get(server).balances();
```

Every operation uses a UUID and works for offline players. Player-name lookup is not part of the API.

---

## Reading a balance

```java
long balance = balances.getBalance(playerId);
long maximum = balances.getMaximumBalance();
```

`getBalance(...)` creates a missing balance with EconomyCraft's configured starting balance and stores it. Missing balances are not exposed as a separate API state.

The current maximum balance is `999999999`. Use `getMaximumBalance()` instead of copying that value into an addon.

---

## Adding, removing and setting

```java
MutationSource source = MutationSource.of("examplemod:quest/reward");

BalanceMutationResult added = balances.addMoney(playerId, 250, source);
BalanceMutationResult removed = balances.removeMoney(playerId, 50, source);
BalanceMutationResult set = balances.setMoney(playerId, 1000, source);
```

Each method also has an overload without `MutationSource`.

| Operation | Valid value | Failure without mutation |
|---|---:|---|
| `addMoney` | Amount greater than `0` | Invalid amount or result above maximum |
| `removeMoney` | Amount greater than `0` | Invalid amount or insufficient funds |
| `setMoney` | Balance from `0` through maximum | Invalid amount or result above maximum |

Values are never silently clamped. Arithmetic overflow also fails without mutation.

A mutation against a missing UUID starts its calculation from the configured starting balance. A failed mutation does not save an implicitly initialized balance. A successful change saves the new balance.

Setting a balance to its existing value returns `NO_CHANGE`. `NO_CHANGE` counts as successful but does not fire a balance-change event.

---

## Mutation results

```java
BalanceMutationResult result = balances.addMoney(playerId, 250, source);

if (result.successful()) {
    long before = result.previousBalance();
    long after = result.newBalance();
    long changedBy = result.difference();
} else {
    BalanceMutationStatus reason = result.status();
}
```

`BalanceMutationResult` contains:

| Method | Value |
|---|---|
| `status()` | Outcome of the operation |
| `type()` | `ADD`, `REMOVE` or `SET` |
| `playerId()` | UUID that was checked or changed |
| `requestedAmount()` | Amount passed to the operation |
| `previousBalance()` | Balance used before validation or mutation |
| `newBalance()` | Final balance; unchanged on failure |
| `source()` | Optional source supplied by the caller |
| `successful()` | `true` for `SUCCESS` and `NO_CHANGE` |
| `difference()` | `newBalance - previousBalance` |

Possible statuses:

| Status | Meaning |
|---|---|
| `SUCCESS` | The balance changed and was saved |
| `NO_CHANGE` | The request was valid but the balance was already equal to the requested value |
| `INVALID_AMOUNT` | The amount or requested balance is outside the allowed range |
| `INSUFFICIENT_FUNDS` | Removing the amount would take the balance below zero |
| `MAX_BALANCE_EXCEEDED` | Adding or setting would exceed the maximum |
| `SAME_PLAYER` | Used by payments where sender and receiver match |

Check the status instead of assuming every failed operation has the same cause.

---

## Paying another UUID

```java
MutationSource source = MutationSource.of("examplemod:market/purchase");
PaymentResult result = balances.pay(senderId, receiverId, 500, source);

if (!result.successful()) {
    // Nothing was changed.
    BalanceMutationStatus reason = result.status();
}
```

Payment rules:

- Sender and receiver must be different UUIDs.
- Amount must be greater than `0`.
- Sender must have enough money.
- Receiver must remain at or below the maximum balance.
- Missing balances use the configured starting balance.
- Offline UUIDs work.

A payment is atomic. Both new balances are validated before either is saved. If any rule fails, neither balance changes and an unknown UUID is not implicitly saved.

`PaymentResult` provides the sender and receiver UUIDs, amount, both previous balances, both final balances, optional source and status. `senderDifference()` and `receiverDifference()` give the signed changes.

Successful payments fire `PAYMENT_SENT` and `PAYMENT_RECEIVED` events only after both balances have been saved.
