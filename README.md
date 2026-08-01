# EconomyCraft

A server-side economy for Fabric and NeoForge.
Requires Architectury API.

---

## Setup

1. Put the jar in your server's `mods` folder and start the server.
2. Type `/eco` in game to open the menu.
3. Operators get an **Admin** button in that menu, or can run `/eco admin`.

Default configuration works without manual changes.

---

## The `/eco` menu

| Button           | Description                                                                                                                                   |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| **Server Shop**  | Buy and sell at fixed prices with unlimited stock. Left click buys, right click sells, shift-click uses the bulk amount.                      |
| **Player Shop**  | Buy items other players have listed. **Sell an item** walks through picking an item and setting a price.                                      |
| **Sell Items**   | Put items in the slots, check the total, confirm. Items without a sell price will not fit, and closing without confirming returns everything. |
| **Orders**       | **New request** picks any item in the game, an amount and a price. Other players fill the request and get paid.                               |
| **Daily Reward** | Claims the daily payout, once per day.                                                                                                        |
| **Pay a Player** | Select a player and an amount.                                                                                                                |
| **Top Balances** | The richest players on the server.                                                                                                            |
| **Item Value**   | The buy and sell price of any item.                                                                                                           |
| **Deliveries**   | Items bought while the inventory was full or orders that have been completed while being offline.                                             |

Each screen also has a command: `/bal`, `/bal top`, `/pay`, `/daily`, `/shop`, `/servershop`, `/sell`, `/worth`, `/orders`, `/orders claim`.

---

## The Admin menu

### Server Shop editor

Browse categories and click an item to change it. Changes are written to `prices.json` when confirmed.

- **Add item** — select any item in the game or one from the inventory. Custom names, enchantments and container contents are stored with the entry.
- **Buy Price / Sell Price** — the price of one item. `0` disables that direction.
- **Bulk Amount** — how many a shift-click buys or sells.
- **Category** — which page of the shop the item appears on. `blocks.wood` creates a sub-page.
- **Delete** — removes the entry.

### Settings

Covers every option in `config.json`: starting balance, daily reward, daily sell limit, tax rate, PvP money loss, thousands separator, and switches for the server shop, player shop, orders, selling, the balance sidebar and the short command aliases.

### Players

Select any player, online or not, to give, take or set their balance, or remove them from the economy.

### Admin commands

`/eco addmoney`, `/eco setmoney`, `/eco removemoney`, `/eco removeplayer`, `/eco toggleScoreboard`.

---

## Config files

On a server, config and player data are stored in `config/economycraft/`: `config.json` and `prices.json` at the top, balances, shops, orders and deliveries under `data/`.

In singleplayer each world gets that same folder inside its own save, at `saves/<world>/economycraft/`.


### `config.json`

| Key                           | Default  | Description                                                                     |
|-------------------------------|----------|---------------------------------------------------------------------------------|
| `startingBalance`             | `1000`   | Money new players start with.                                                   |
| `dailyAmount`                 | `100`    | Money given by the daily reward.                                                |
| `dailySellLimit`              | `10000`  | Most a player can earn per day from selling. `0` disables the limit.            |
| `taxRate`                     | `0.1`    | Tax on trades and orders, as a decimal (`0.1` = 10%).                           |
| `pvp_balance_loss_percentage` | `0`      | Share of a balance the killer takes on a PvP death. `0` disables it.            |
| `standalone_commands`         | `true`   | Allow `/pay`, `/daily` and similar without the `/eco` prefix.                   |
| `standalone_admin_commands`   | `false`  | Allow `/addmoney`, `/setmoney` and similar without the `/eco` prefix.           |
| `scoreboard_enabled`          | `true`   | Show the balance sidebar.                                                       |
| `server_shop_enabled`         | `true`   | Enable the server shop.                                                         |
| `shop_enabled`                | `true`   | Enable the player shop.                                                         |
| `orders_enabled`              | `true`   | Enable the orders board. Collecting deliveries works either way.                |
| `sell_enabled`                | `true`   | Enable selling.                                                                 |
| `balance_separator`           | `"."`    | Thousands separator. Only the first character is used, so `","` gives `$1,000`. |

### `prices.json`

One entry per server shop item, keyed by item id:

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

`category` accepts `top.sub` for a sub-page. `stack` is the shift-click bulk amount. `unit_buy` and `unit_sell` are the price of one item, and `0` disables that direction.

Two further keys are written by the editor:

- `components` holds NBT for custom items such as a name, enchantments or shulker contents. JSON keys must be unique, so a second variant of the same item takes a `#label` suffix, e.g.: `minecraft:shulker_box#loot_rare`. The suffix is stripped on load and is not shown to players.
- `"removed": true` marks a bundled default that was deleted, so it is not restored on the next start. Delete the entry to restore it.

---

## Placeholders

EconomyCraft can expose economy data to other mods through [Text Placeholder API](https://modrinth.com/mod/placeholder-api) on Fabric, or the unofficial [Placeholder API NeoForge](https://modrinth.com/mod/placeholder-api-neoforge) port on NeoForge.

Both are optional and not bundled. The mod works without them, but the matching jar for your version and loader must be in the server's `mods` folder for these placeholders to resolve.

| Placeholder                              | Description                                                                                |
|------------------------------------------|--------------------------------------------------------------------------------------------|
| `%economycraft:balance%`                 | Raw balance of the viewed player, e.g. `1000`.                                             |
| `%economycraft:balance_formatted%`       | Balance with currency symbol and thousands separator, e.g. `$1.000`.                       |
| `%economycraft:daily_sell_remaining%`    | How much the player can still earn from selling today. Shows `∞` if the limit is disabled. |
| `%economycraft:top_name 1%`              | Name of the player ranked `1` on the balance leaderboard (`1` = richest).                  |
| `%economycraft:top_balance 1%`           | Raw balance of the player ranked `1`.                                                      |
| `%economycraft:top_balance_formatted 1%` | Formatted balance of the player ranked `1`.                                                |

The `top_*` placeholders take the rank as an argument, e.g. `%economycraft:top_name 3%` for third place. Ranks beyond the number of players resolve as invalid.

---