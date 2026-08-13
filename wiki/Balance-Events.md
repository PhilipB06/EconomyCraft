# Balance events

API v1 provides successful post-mutation events. Events cannot cancel or change an operation.

---

## Register a listener

```java
EconomyCraftApi api = EconomyCraftApi.get(server);

ListenerRegistration registration = api.balanceEvents().register(event -> {
    UUID playerId = event.playerId();
    long before = event.previousBalance();
    long after = event.newBalance();
    long difference = event.difference();
});
```

Keep the returned registration and unregister it when the addon stops using that server:

```java
registration.unregister();
```

`ListenerRegistration` also implements `AutoCloseable`, so `registration.close()` does the same thing.

Registration, unregistration and listener calls happen on the server thread.

---

## Event data

`BalanceChangeEvent` contains:

| Method | Value |
|---|---|
| `playerId()` | UUID whose balance changed |
| `previousBalance()` | Balance before the change |
| `newBalance()` | Balance after the change |
| `difference()` | `newBalance - previousBalance` |
| `type()` | Kind of mutation |
| `counterpartyId()` | Other UUID for a payment, otherwise empty |
| `source()` | Optional namespaced source supplied with the mutation |

Mutation types are:

| Type | Meaning |
|---|---|
| `ADD` | Money was added directly |
| `REMOVE` | Money was removed directly |
| `SET` | Balance was set directly |
| `PAYMENT_SENT` | Sender side of a completed payment |
| `PAYMENT_RECEIVED` | Receiver side of a completed payment |

---

## When events fire

An event fires after an actual successful balance change has been saved.

No event fires for:

- A failed mutation.
- `NO_CHANGE`.
- Initializing a missing balance through `getBalance(...)`.

A successful payment saves both balances first, then fires `PAYMENT_SENT` followed by `PAYMENT_RECEIVED`. Each event has the other UUID in `counterpartyId()` and carries the same optional source.

Listener failures are isolated. If a listener throws an exception, the mutation result stays successful and the remaining listeners are still called.

Listeners should still handle errors internally and return quickly because they run synchronously on the server thread.

---

## Filter by source

```java
ListenerRegistration registration = api.balanceEvents().register(event -> {
    boolean fromThisAddon = event.source()
            .map(MutationSource::namespace)
            .filter("examplemod"::equals)
            .isPresent();

    if (fromThisAddon) {
        // Handle this addon's completed balance changes.
    }
});
```

EconomyCraft's own operations use `economycraft:*` sources. Addons should use their own mod id as the namespace.
