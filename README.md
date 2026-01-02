# EconomyCraft

EconomyCraft provides a simple server-side cross-platform economy system for Fabric and NeoForge servers.  
The mod requires Architectury API and targets **Minecraft 1.21.x**.

---

## Commands
### Player Commands
- `/bal [<player|selector>|top]` – Check balances or view the top balances.
- `/pay <player> <amount>` - Transfer money.
- `/daily` - Claim a daily login bonus.
- `/shop` - Opens the shop UI.
- `/shop list <price>` - List the item in your hand.
- `/sell [<amount>|all]` – Sell the item in your hand. Use `all` to sell all matching items from your inventory.
- `/orders` - Opens the orders UI to browse and fulfill requests.
- `/orders request <item> <amount> <price>` - Create an item request.
- `/orders claim` - Claim items bought or requested while offline.

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
  "scoreboard_enabled": true
}
```

- `startingBalance` - initial money for new players. Default: `1000`.
- `dailyAmount` - money given by `/daily`. Default: `100`.
- `dailySellLimit` – maximum money a player can earn per day via selling. `0` disables the limit. Default: `10000`.
- `taxRate` - percentage tax applied to trades and orders (**decimal factor**, e.g. `0.1` = 10%). Default: `0.1`.
- `pvp_balance_loss_percentage` - percentage of a player’s balance lost on PvP death and transferred to the killer (**decimal factor**, e.g. `0.1` = 10%). `0` disables this feature. Default: `0`.
- `standalone_commands` - enable standalone `/pay`, `/daily`, etc. Default: `true`.
- `standalone_admin_commands` - enable standalone `/addmoney`, `/setmoney`, etc. Default: `false`.
- `scoreboard_enabled` - show the balance sidebar by default. Can be toggled with `/eco toggleScoreboard`. Default: `true`.

---