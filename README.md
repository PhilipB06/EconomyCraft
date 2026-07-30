# EconomyCraft

A simple server-side economy mod for Fabric and NeoForge. Requires Architectury API.

---

## Commands

### Player commands
- `/bal [<player|selector>|top]` - check a balance, or see the top balances.
- `/pay <player> <amount>` - send money to another player.
- `/daily` - claim your daily bonus.
- `/shop` - a player-run marketplace where anyone can list items for sale.
  - `list <price> [<amount>]` - list the item in your hand.
  - `search <query>` - search listings by name or property.
- `/servershop` - the server's own shop, with unlimited stock. Prices set in `config/economycraft/prices.json`.
  - `search <query>` - search listings by name or property.
- `/sell [<amount>|all|everything]` - sell the item in your hand. `all` sells every matching item in your inventory, `everything` sells your whole inventory. If a `/orders` request is paying more than the server would, your items go there first.
- `/worth [<item> [<amount>]]` - check what an item buys/sells for. Defaults to hand-held item.
- `/orders` - a request board: post what you want, other players fulfill it.
  - `request <item> <amount> <price>` - post a request.
  - `claim` - collect anything you've bought or been paid while offline.
  - `search <query>` - search listings by name or property.

### Admin commands
- `/eco addmoney <player|selector> <amount>` - add money to a player's balance.
- `/eco setmoney <player|selector> <amount>` - set a player's balance.
- `/eco removemoney <player|selector> [amount]` - remove money from a player's balance.
- `/eco removeplayer <player|selector>` - remove a player from the economy.
- `/eco toggleScoreboard` - toggle the balance sidebar for all players.


Player commands like `/pay` and `/daily` work on their own by default, but also under `/eco` (e.g. `/eco pay`). Set `standalone_commands` to `false` in `config.json` if you'd rather require the `/eco` prefix. Admin commands are the other way around: they need the `/eco` prefix unless you turn on `standalone_admin_commands`.

---

## Configuration

Config and player data are stored in `config/economycraft/`.

### `config.json`

```json
{
  "startingBalance": 1000,
  "dailyAmount": 100,
  "dailySellLimit": 10000,
  "taxRate": 0.1,
  "pvp_balance_loss_percentage": 0.0,
  "standalone_commands": true,
  "standalone_admin_commands": false,
  "scoreboard_enabled": true,
  "server_shop_enabled": true,
  "sell_enabled": true,
  "balance_separator": "."
}
```

- `startingBalance` - money new players start with. Default `1000`.
- `dailyAmount` - money given by `/daily`. Default `100`.
- `dailySellLimit` - most a player can earn per day from selling. `0` turns the limit off. Default `10000`.
- `taxRate` - tax on trades and orders, as a decimal (`0.1` = 10%). Default `0.1`.
- `pvp_balance_loss_percentage` - share of a player's balance the killer takes on a PvP death, as a decimal. `0` turns it off. Default `0`.
- `standalone_commands` - let `/pay`, `/daily`, etc. work without the `/eco` prefix. Default `true`.
- `standalone_admin_commands` - let `/addmoney`, `/setmoney`, etc. work without the `/eco` prefix. Default `false`.
- `scoreboard_enabled` - show the balance sidebar. Default `true`.
- `server_shop_enabled` - enables/disables the server shop. Default `true`.
- `sell_enabled` - enables/disables `/sell`. Default `true`.
- `balance_separator` - the thousands separator used. Only the first character counts, e.g. `","` gives `$1,000`. Default `"."`.

### Server shop prices (`prices.json`)

Each entry is keyed by an item id:

```json
{
  "minecraft:enchanted_book": {
    "category": "enchantments",
    "stack": 16,
    "unit_buy": 800,
    "unit_sell": 200,
    "components": {
      "minecraft:stored_enchantments": {
        "minecraft:mending": 1
      }
    }
  }
}
```

- `category` - which section of the shop menu it shows up in. Use `top.sub` (e.g. `blocks.wood`) for a subcategory.
- `stack` - item's stack size.
- `unit_buy` / `unit_sell` - price for one item. Set either to `0` to disable that direction.
- `components` - only needed if the item has specific NBT (a custom name, enchantments...). 

JSON keys have to be unique, so you normally get one entry per item id. To sell more than one version of the same item (different loot shulkers, different enchants, etc.), add a `#label` to the id to keep the keys distinct - it's stripped out on load and never shown to players:

```json
{
  "minecraft:shulker_box#loot_common": {
    "category": "custom",
    "unit_buy": 5000,
    "components": {
      "minecraft:custom_name": {"text": "Loot Box"},
      "minecraft:container": [
        {"slot": 0, "item": {"id": "minecraft:diamond", "count": 5}},
        {"slot": 1, "item": {"id": "minecraft:emerald", "count": 10}}
      ]
    }
  },
  "minecraft:shulker_box#loot_rare": {
    "category": "custom",
    "unit_buy": 15000,
    "components": {
      "minecraft:custom_name": {"text": "Rare Loot Box"},
      "minecraft:container": [
        {"slot": 0, "item": {"id": "minecraft:netherite_ingot", "count": 3}}
      ]
    }
  }
}
```

---

## Placeholders

EconomyCraft can share economy data with other mods through [Text Placeholder API](https://modrinth.com/mod/placeholder-api) on Fabric, or the unofficial [Placeholder API NeoForge](https://modrinth.com/mod/placeholder-api-neoforge) port on NeoForge.

Both are **optional and not bundled with EconomyCraft**, the mod works fine without them, but you'll need to grab the jar for your version/loader and drop it into your server's `mods` folder before these placeholders will resolve:
- Fabric: [Text Placeholder API on Modrinth](https://modrinth.com/mod/placeholder-api)
- NeoForge: [Placeholder API NeoForge on Modrinth](https://modrinth.com/mod/placeholder-api-neoforge)

| Placeholder | Description                                                                                                                  |
| --- |------------------------------------------------------------------------------------------------------------------------------|
| `%economycraft:balance%` | Raw balance of the viewed player, e.g. `1000`.                                                                               |
| `%economycraft:balance_formatted%` | Balance with currency symbol and thousands separator, e.g. `$1.000`.                                                         |
| `%economycraft:daily_sell_remaining%` | Remaining amount the player can earn from `/sell` today before hitting `dailySellLimit`. Shows `∞` if the limit is disabled. |
| `%economycraft:top_name 1%` | Name of the player ranked `1` on the balance leaderboard (`1` = richest).                                                    |
| `%economycraft:top_balance 1%` | Raw balance of the player ranked `1`.                                                                                        |
| `%economycraft:top_balance_formatted 1%` | Formatted balance of the player ranked `1`.                                                                                  |

The `top_*` placeholders take the rank as an argument after the name, e.g. `%economycraft:top_name 3%` for 3rd place. If there aren't enough players to fill that rank, it just resolves as invalid.

---