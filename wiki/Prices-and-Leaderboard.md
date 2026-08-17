# Prices and leaderboard

Both services are read-only. Returned collections are immutable and no internal mutable price entry is exposed.

---

## Resolve an item price

```java
PriceApi prices = EconomyCraftApi.get(server).prices();

Optional<ItemPrice> resolved = prices.resolve(stack);
resolved.ifPresent(price -> {
    OptionalLong buy = price.unitBuyPrice();
    OptionalLong sell = price.unitSellPrice();
});
```

`resolve(...)` uses the same price matching as EconomyCraft. It supports normal and modded items, stored custom item components, potions, tipped arrows and enchanted books.

An empty or unknown stack returns `Optional.empty()`.

The result answers which unit prices EconomyCraft defines for the item. It does not answer whether a specific player can buy or sell that concrete stack at the moment.

The API does not check:

- Player inventory or available space.
- Player balance.
- Daily sell limits.
- Item damage or container-content sell restrictions.
- Whether the shop or selling is enabled.
- Category visibility or menu navigation.

---

## ItemPrice

| Method | Value |
|---|---|
| `key()` | Exact key of the configured price entry |
| `itemId()` | Namespaced item id, such as `minecraft:diamond` |
| `category()` | Stored price category |
| `bulkAmount()` | Configured bulk amount |
| `unitBuyPrice()` | Defined unit buy price, or empty when disabled |
| `unitSellPrice()` | Defined unit sell price, or empty when disabled |
| `hasBuyPrice()` | Whether a positive unit buy price is defined |
| `hasSellPrice()` | Whether a positive unit sell price is defined |
| `customItem()` | Whether the entry contains stored custom item data |
| `prototype()` | Item prototype for the entry |

`hasBuyPrice()` and `hasSellPrice()` only describe configured prices. They are not complete purchase or selling eligibility checks.

`ItemPrice` is immutable. EconomyCraft copies the prototype when the object is created, and `prototype()` returns a new copy every time. Changing the returned `ItemStack` cannot change EconomyCraft's stored price data or later API results.

---

## Price categories

```java
List<String> categories = prices.categories();
List<ItemPrice> ores = prices.entries("ores");
```

`categories()` lists the category strings currently used by price entries. `entries(...)` returns entries belonging to the requested category. An unknown category returns an empty immutable list.

Category icons, colors, display names, visibility, navigation, search and editing are not public API data.

---

## Leaderboard

```java
LeaderboardApi leaderboard = EconomyCraftApi.get(server).leaderboard();

List<LeaderboardEntry> topTen = leaderboard.getLeaderboardEntries(10);
Optional<LeaderboardEntry> first = leaderboard.getLeaderboardEntry(1);
```

Entries are sorted by balance from highest to lowest. Equal balances are ordered by UUID. Rank values are one-based, so rank `1` is the highest balance.

`getLeaderboardEntries(limit)` returns an empty immutable list when `limit` is `0` or lower. `getLeaderboardEntry(rank)` returns `Optional.empty()` for ranks below `1` or beyond the stored leaderboard.

Each immutable `LeaderboardEntry` contains:

```java
UUID playerId();
long balance();
```

Names are deliberately not part of the result. Entries remain usable when a UUID cannot be resolved to a current player name. The calling mod may resolve names separately when it needs to display them.

Only stored balances appear on the leaderboard. Reading an unknown UUID through `getBalance(...)` initializes and stores it, after which it can appear in later leaderboard queries.
