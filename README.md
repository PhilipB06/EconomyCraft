# EconomyCraft

EconomyCraft provides a simple server-side cross-platform economy system for Fabric and NeoForge servers.  
The mod requires Architectury API.

---

## Commands
### Player Commands
- `/bal [<player|selector>|top]` - Check balances or view the top balances.
- `/pay <player> <amount>` - Transfer money to another player.
- `/daily` - Claim a daily login bonus.
- `/shop` - Player-driven marketplace where players list items for sale.
  - `list <price> [<amount>]` - List the item in your hand. Omit `<amount>` to list up to a full stack.
- `/servershop` - Server-managed shop with unlimited supply. Prices can be edited in `config/economycraft/prices.json`.
- `/sell [<amount>|all|everything]` - Sell the item in your hand. Use `all` to sell all matching items from your inventory, or `everything` to sell your entire inventory. If an open `/orders` request pays more per item than the server would, the sale goes there first (best-paying order first), falling back to the server price for any remainder.
- `/orders` - Request-based trading system. Click a request to fulfill it fully/partially.
  - `request <item> <amount> <price>` - Create an item request.
  - `claim` - Claim items bought or requested while offline.

*Hover a filled shulker box and press Ctrl+Q to preview its contents before buying, selling, or fulfilling.*

### Admin Commands
- `/eco addmoney <player|selector> <amount>` - Add money to a player.
- `/eco setmoney <player|selector> <amount>` - Set a player’s balance.
- `/eco removemoney <player|selector> [amount]` - Remove money from a player.
- `/eco removeplayer <player|selector>` - Remove a player from the economy system.
- `/eco toggleScoreboard` - Toggle the balance sidebar for all players.

**Notes:**
- Non-admin commands such as `/pay` or `/daily` are standalone by default and also work under `/eco` (e.g., `/eco pay`).  
  Set `standalone_commands` to `false` in `config.json` to require the `/eco` prefix.
- Admin commands use `/eco` unless `standalone_admin_commands` is enabled.

---

## Configuration

Configuration and player data are stored in `config/economycraft/`.

### Default `config.json`

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

- `startingBalance` - initial money for new players. Default: `1000`.
- `dailyAmount` - money given by `/daily`. Default: `100`.
- `dailySellLimit` - maximum money a player can earn per day via selling. `0` disables the limit. Default: `10000`.
- `taxRate` - percentage tax applied to trades and orders (**decimal factor**, e.g. `0.1` = 10%). Default: `0.1`.
- `pvp_balance_loss_percentage` - percentage of a player’s balance lost on PvP death and transferred to the killer (**decimal factor**, e.g. `0.1` = 10%). `0` disables this feature. Default: `0`.
- `standalone_commands` - enable standalone `/pay`, `/daily`, etc. Default: `true`.
- `standalone_admin_commands` - enable standalone `/addmoney`, `/setmoney`, etc. Default: `false`.
- `scoreboard_enabled` - show the balance sidebar by default. Can be toggled with `/eco toggleScoreboard`. Default: `true`.
- `server_shop_enabled` - enables the server shop (`/servershop` and `/eco servershop`). Default: `true`.
- `sell_enabled` - enables the `/sell` command (selling farmed items directly to the server). Set to `false` to steer players toward supply-driven trading via `/shop` and `/orders`. Default: `true`.
- `balance_separator` - thousands-separator character used wherever a balance is displayed (commands, menus, `%economycraft:balance_formatted%`). Only the first character is used, e.g. `","` for `$1,000`. Default: `"."`.

### Server Shop Prices (`prices.json`)

Each entry is keyed by an item id (vanilla or modded):

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

- `category` - groups the entry in the shop menu. Use `top.sub` (e.g. `blocks.wood`) for a subcategory.
- `stack` - item's stack size
- `unit_buy` / `unit_sell` - price per single item; `0` disables that direction.
- `components` - optional, only needed for an item with specific NBT (custom name, enchantments, a shulker box with contents, etc.).
  - Tip: create the item as a `/shop` listing first, then copy its `components` object out of `config/economycraft/data/shop.json`.

A JSON key can only be used once, so normally you can only have one entry per item id. If you want to sell several versions of the **same** item (e.g. two different loot shulkers, swords with different enchantments, etc.), add a `#label` after the id to keep the JSON keys distinct; the label is discarded once the entry is read and **never** shown to players:

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

EconomyCraft can expose economy data to other mods through [Text Placeholder API](https://modrinth.com/mod/placeholder-api) on Fabric, or its unofficial [Placeholder API NeoForge](https://modrinth.com/mod/placeholder-api-neoforge) port on NeoForge.

Both are **optional and not bundled with EconomyCraft**, the mod runs fine without them, but you must download the jar matching your Minecraft version/loader and drop it into your server's `mods` folder yourself before any placeholder below will resolve:
- Fabric: [Text Placeholder API on Modrinth](https://modrinth.com/mod/placeholder-api)
- NeoForge: [Placeholder API NeoForge on Modrinth](https://modrinth.com/mod/placeholder-api-neoforge)

| Placeholder | Description                                                                                                                  |
| --- |------------------------------------------------------------------------------------------------------------------------------|
| `%economycraft:balance%` | Raw numeric balance of the viewed player (e.g. `1000`).                                                                        |
| `%economycraft:balance_formatted%` | Balance formatted with currency symbol and thousands separator (e.g. `$1.000`).                                              |
| `%economycraft:daily_sell_remaining%` | Remaining amount the player can earn from `/sell` today before hitting `dailySellLimit`. Shows `∞` if the limit is disabled. |
| `%economycraft:top_name 1%` | Name of the player ranked `1` on the balance leaderboard (`1` = richest). Not tied to the viewed player.                     |
| `%economycraft:top_balance 1%` | Raw numeric balance of the player ranked `1`.                                                                                |
| `%economycraft:top_balance_formatted 1%` | Formatted balance of the player ranked `1`.                                                                                  |

The `top_*` placeholders take the rank as an argument, separated from the placeholder name by a space (e.g. `%economycraft:top_name 3%` for 3rd place). If fewer players exist than the requested rank, the placeholder resolves as invalid.

---