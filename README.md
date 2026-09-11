# EconomyCraft

A server-side economy for Fabric and NeoForge.
Requires Architectury API.

---

## The `/eco` menu

| Buttom            | Description                                                                                                              |
|-------------------|--------------------------------------------------------------------------------------------------------------------------|
| **Shop**          | Buy and sell at fixed prices with unlimited stock. Left click buys, right click sells, shift-click uses the bulk amount. |
| **Auction House** | Buy items other players have listed, or list your own.                                                                   |
| **Sell Items**    | Drop items in, check the total, confirm. Unpriced items can not be sold.                                                 |
| **Orders**        | Request an item, amount and price. Other players fill it and get paid.                                                   |
| **Daily Reward**  | Claims the daily payout, once per day.                                                                                   |
| **Pay a Player**  | Send money to another player.                                                                                            |
| **Top Balances**  | The richest players on the server.                                                                                       |
| **Item Value**    | The buy and sell price of any item.                                                                                      |
| **Deliveries**    | Items or payouts that couldn't be delivered directly (full inventory/completed while offline).                           |
| **Transactions**  | Your recent balance history.                                                                                             |

Each screen also has a command: `/bal`, `/bal top`, `/pay`, `/daily`, `/shop`, `/ah`, `/auction`, `/sell`, `/worth`, `/orders`, `/orders claim`, `/transactions`.

---

## The Admin (`/eco admin`) menu

### Shop editor

Browse categories and click an item to edit it.

- **Category editor**: right-click a category to rename it, change its color, icon or visibility, or exclude it from dynamic pricing. Deleting a category moves its items to `misc` and zeroes their buy prices.
- **Add item**: pick any item in the game, or one from your inventory. Custom names, enchantments and container contents are kept.
- **Buy Price / Sell Price**: the price of one item; `0` disables that direction. If dynamic pricing applies, this also shows the current price and multiplier.
- **Dynamic Pricing**: opt this item out of dynamic pricing even while it's enabled server-wide.
- **Bulk Amount**: how many a shift-click buys or sells.
- **Category**: which shop page the item appears on. `blocks.wood` creates a sub-page.
- **Delete**: removes the entry.

### Dynamic shop pricing

Optional, off by default (`dynamic_prices_enabled`). Scales every buy price by:

```
current price = base price × active-player median balance / starting balance
```

The scale is clamped between `dynamic_price_min_multiplier` and `dynamic_price_max_multiplier`, and recalculated at most once an hour. Sell prices are never affected. Opt out per category or item from the Shop editor above.

### Settings

Every option in `config.json`, editable in-game.

### Players

Select any player, online or not, to give, take or set their balance, remove them from the economy, or override their max active orders/auctions.

### Admin commands

`/eco addmoney`, `/eco setmoney`, `/eco removemoney`, `/eco removeplayer`.

---

## Config files

Stored in `config/economycraft/` on a server (or `saves/<world>/economycraft/` per-world in singleplayer): `config.json`, `webhook.json` and `prices.json` at the top, player data under `data/`.

### `config.json`

| Key                              | Default | Description                                                                                                     |
|----------------------------------|---------|-----------------------------------------------------------------------------------------------------------------|
| `startingBalance`                | `1000`  | Money new players start with.                                                                                   |
| `dailyAmount`                    | `100`   | Money given by the daily reward.                                                                                |
| `dailySellLimit`                 | `10000` | Most a player can earn per day from selling. `0` disables the limit.                                            |
| `taxRate`                        | `0.1`   | Tax on trades and orders, as a decimal (`0.1` = 10%).                                                           |
| `standalone_commands`            | `true`  | Allow `/pay`, `/daily` etc. without the `/eco` prefix.                                                          |
| `standalone_admin_commands`      | `false` | Allow `/addmoney`, `/setmoney` etc. without the `/eco` prefix.                                                  |
| `scoreboard_enabled`             | `true`  | Show the balance sidebar.                                                                                       |
| `shop_enabled`                   | `true`  | Enable the fixed-price shop.                                                                                    |
| `auction_enabled`                | `true`  | Enable the auction house.                                                                                       |
| `orders_enabled`                 | `true`  | Enable the orders board. Deliveries still work either way.                                                      |
| `sell_enabled`                   | `true`  | Enable selling.                                                                                                 |
| `worth_enabled`                  | `true`  | Enable item value lookups (`/worth`).                                                                           |
| `balance_separator`              | `"."`   | Thousands separator, e.g. `","` gives `$1,000`.                                                                 |
| `transaction_log_enabled`        | `true`  | Record every balance change to a daily log file.                                                                |
| `transaction_log_retention_days` | `7`     | How many days of transaction logs to keep.                                                                      |
| `order_expiration_hours`         | `168`   | Hours before an unfulfilled order expires and its escrow is refunded. `0` disables expiration.                  |
| `auction_expiration_hours`       | `168`   | Hours before an unsold auction expires and its item goes to deliveries. `0` disables expiration.                |
| `max_active_orders_per_player`   | `0`     | Most open orders a player can have at once. `0` = unlimited. Overridable per player.                            |
| `max_active_auctions_per_player` | `0`     | Most active auctions a player can have at once. `0` = unlimited. Overridable per player.                        |
| `dynamic_prices_enabled`         | `false` | Scale shop buy prices with the active-player median balance. See [Dynamic shop pricing](#dynamic-shop-pricing). |
| `dynamic_price_min_multiplier`   | `0.5`   | Lowest allowed price scale.                                                                                     |
| `dynamic_price_max_multiplier`   | `5.0`   | Highest allowed price scale.                                                                                    |
| `dynamic_price_min_active_days`  | `30`    | Players must have logged in within this many days to count as active. `0` includes everyone.                    |

### `webhook.json`

| Key                  | Default | Description                                            |
|----------------------|---------|--------------------------------------------------------|
| `webhook_enabled`    | `false` | Post transactions to `webhook_url`.                    |
| `webhook_url`        | `""`    | Discord-compatible incoming webhook URL.               |
| `webhook_min_amount` | `0`     | Skip webhook posts for transactions smaller than this. |

### `prices.json`

One entry per shop item, keyed by item id:

```json
{
  "minecraft:diamond": {
    "category": "ores",
    "stack": 64,
    "unit_buy": 800,
    "unit_sell": 200
  }
}
```

`category` accepts `top.sub` for a sub-page, `stack` is the shift-click bulk amount, and `unit_buy`/`unit_sell` are the price of one item (`0` disables that direction). Items from installed mods are added automatically, using their mod ID as category and `0` for both prices.

The editor also writes a few extra keys:

- `components`: NBT for custom items (name, enchantments, container contents). A `#label` suffix distinguishes duplicates of the same item, e.g. `minecraft:shulker_box#loot_rare`.
- `"removed": true`: marks a deleted default so it isn't restored on the next start. Delete the entry to restore it.
- `"dynamic_price_enabled": false`: opts an item, or a category under `_categories`, out of dynamic pricing.

---

## Placeholders

Exposes economy data to other mods via [Text Placeholder API](https://modrinth.com/mod/placeholder-api) (Fabric) or [Placeholder API NeoForge](https://modrinth.com/mod/placeholder-api-neoforge) (NeoForge). Both are optional, the mod works without them, but the matching jar must be in `mods/` for placeholders to resolve.

| Placeholder                                   | Description                                                              |
|-----------------------------------------------|--------------------------------------------------------------------------|
| `%economycraft:balance%`                      | Raw balance, e.g. `1000`.                                                |
| `%economycraft:balance_formatted%`            | Formatted balance, e.g. `$1.000`.                                        |
| `%economycraft:balance_short%`                | Abbreviated balance, e.g. `$1.2k`.                                       |
| `%economycraft:daily_sell_remaining%`         | How much the player can still earn from selling today. `∞` if unlimited. |
| `%economycraft:top_name <rank>%`              | Name of the player at that rank (`1` = richest).                         |
| `%economycraft:top_balance <rank>%`           | Raw balance at that rank.                                                |
| `%economycraft:top_balance_formatted <rank>%` | Formatted balance at that rank.                                          |
| `%economycraft:top_balance_short <rank>%`     | Abbreviated balance at that rank.                                        |

Ranks beyond the number of players resolve as invalid.

---

## Transaction logs and webhook

Every balance change is logged to `logs/transactions-YYYY-MM-DD.log` inside the config folder, and kept for `transaction_log_retention_days` days (default `7`). Setting it above 90 logs a console warning on start.

Enable `webhook_enabled` in `webhook.json` to also POST each transaction to a Discord-compatible webhook; use `webhook_min_amount` to only notify on larger transactions.

---

## Developer API

The normal EconomyCraft jar includes API v1 for other server-side mods, no separate runtime API mod to install. Covers balances and payments, money formatting, read-only item prices, leaderboard data and balance-change events. Public classes are under `com.reazip.economycraft.api.v1`.

See the [Developer API wiki](https://github.com/PhilipB06/EconomyCraft/wiki) for setup, examples and the complete reference.

---