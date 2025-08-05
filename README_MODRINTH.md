# EconomyCraft

EconomyCraft adds a lightweight, server-side economy system for Fabric and NeoForge.

## Commands
- `/balance [player]` – Check balances.
- `/pay <player> <amount>` – Transfer money.
- `/daily` – Claim a daily login bonus.
- `/shop` – Open the player shop.
- `/orders` – Browse and fulfill item orders.
- Admin: `/eco addmoney <player> <amount>`, `/eco setmoney <player> <amount>`, `/eco removeplayer <player>`, `/eco toggleScoreboard`.

Non-admin commands such as `/pay` or `/daily` are standalone by default and also work under `/eco`. Set `standalone_commands` to `false` in the config to require the `/eco` prefix. Admin commands use `/eco` unless `standalone_admin_commands` is enabled.

## Configuration
Configuration and data are stored at `config/economycraft/`.

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
- `taxRate` – percentage tax on trades and orders. Default: `0.1`.
- `standalone_commands` – enable standalone `/pay`, `/daily`, etc. Default: `true`.
- `standalone_admin_commands` – enable standalone `/addmoney`, `/setmoney`, etc. Default: `false`.

## Features
- Cross-platform economy for Fabric and NeoForge.
- Shop and order request system.
- Optional balance sidebar with top balances.
- Daily rewards and player-to-player payments.

Server-side only.

