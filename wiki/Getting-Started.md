# Getting started

Use the normal EconomyCraft jar that matches the Minecraft version and loader of your mod.

Add that jar as a mod compile dependency in your project.

Declare `economycraft` as a required mod dependency.

There is no separate runtime API mod and no loader-specific API entry point.

---

## Get the API

Call `EconomyCraftApi.get(...)` with the current `MinecraftServer`:

```java
import com.reazip.economycraft.api.v1.EconomyCraftApi;
import net.minecraft.server.MinecraftServer;

EconomyCraftApi api = EconomyCraftApi.get(server);
```

The server must be running and EconomyCraft must be initialized. Calling the entry point before its internal provider is ready throws `IllegalStateException`.

The returned API belongs to that server. Do not carry it over to another integrated-server world or a later server instance.

---

## Server thread

API v1 is server-thread-only. Get the API, call its services and register or unregister listeners on the Minecraft server thread.

Calling from another thread throws `IllegalStateException`. Schedule work onto the server first when handling asynchronous work:

```java
server.execute(() -> {
    EconomyCraftApi api = EconomyCraftApi.get(server);
    long balance = api.balances().getBalance(playerId);
});
```

---

## Services

```java
BalanceApi balances = api.balances();
PriceApi prices = api.prices();
LeaderboardApi leaderboard = api.leaderboard();
BalanceEvents events = api.balanceEvents();

String displayed = api.formatMoney(125000);
```

`formatMoney(...)` uses EconomyCraft's currency symbol and configured thousands separator. For example, the default separator formats `125000` as `$125.000`.

---

## Permissions

The API does not check command or player permissions. The calling mod decides who may trigger its features before it calls EconomyCraft.

EconomyCraft validates balance rules regardless of who calls the API.

---

## Namespaced sources

A mutation can carry a source that identifies the calling mod and reason:

```java
MutationSource source = MutationSource.of("examplemod:quest/reward");
```

Use your mod's own namespace. Both parts must be lowercase.

| Part | Allowed characters |
|---|---|
| Namespace | `a-z`, `0-9`, `.`, `_`, `-` |
| Reason | `a-z`, `0-9`, `/`, `.`, `_`, `-` |

The string must contain exactly one `:`. Invalid values throw `IllegalArgumentException` when the `MutationSource` is created.

Sources are optional. The overloads without a source return results and events with `Optional.empty()`.
