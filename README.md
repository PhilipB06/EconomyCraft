# EconomyCraft

EconomyCraft provides a simple cross-platform economy system for Fabric and NeoForge servers. The mod requires Architectury API and targets Minecraft 1.21.7 and newer.

## Commands

- `/balance [player]` – Check balances.
- `/pay <player> <amount>` – Transfer money.
- `/daily` – Claim a daily login bonus.
- `/shop` – Opens the shop UI.
- `/shop sell <price>` – Sell the item in your hand.
- `/orders` – Opens the orders UI to browse and fulfill requests.
- `/orders request <item> <amount> <price>` – Create an item request.
- `/orders claim` – Claim items bought or requested while offline.
- Admin: `/eco addmoney <player> <amount>`, `/eco setmoney <player> <amount>`, `/eco removeplayer <player>`, `/eco toggleScoreboard`.

Non-admin commands such as `/pay` or `/daily` are standalone by default and also work under `/eco` (e.g., `/eco pay`). Set `standalone_commands` to `false` in `config.json` to require the `/eco` prefix. Admin commands use `/eco` unless `standalone_admin_commands` is enabled.

Configuration and player data are stored in `config/economycraft/`. A balance sidebar shows the top players and can be toggled with `/eco toggleScoreboard`.

## Configuration

Example `config.json`:

```json
{
  "startingBalance": 1000,
  "dailyAmount": 100,
  "taxRate": 0.1,
  "standalone_commands": true,
  "standalone_admin_commands": false
}
```

- `startingBalance` – initial money for new players. Default: `1000`.
- `dailyAmount` – money given by `/daily`. Default: `100`.
- `taxRate` – percentage tax applied to trades and orders. Default: `0.1`.
- `standalone_commands` – enable standalone `/pay`, `/daily`, etc. Default: `true`.
- `standalone_admin_commands` – enable standalone `/addmoney`, `/setmoney`, etc. Default: `false`.

## Features

- Cross-platform economy for Fabric and NeoForge.
- Shop and order request system.
- Optional balance sidebar with top balances.
- Server-side only.

