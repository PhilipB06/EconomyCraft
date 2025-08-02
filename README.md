# EconomyCraft

EconomyCraft provides a simple cross-platform economy system for Fabric and NeoForge servers. The mod requires Architectury API and targets Minecraft 1.21.7 and newer.

## Features

- `/eco balance [player]` – Check balances.
- `/eco pay <player> <amount>` – Transfer money.
- `/eco addmoney <player> <amount>` – Admin command.
- `/eco setmoney <player> <amount>` – Admin command.
- `/eco removeplayer <player>` – Admin command to drop a player from the economy.
- `/eco daily` – Claim a daily login bonus.
Balances and configuration live in `config/EconomyCraft/` (`balances.json`, `daily.json`, `config.json`, etc.) and a live leaderboard is shown on the sidebar sorted by balance. Your rank appears below the top players.

### Shop Commands
- `/eco shop` – Opens the chest-based shop UI.
- `/eco shop sell <price>` – Sell the item in your hand.

### Market Commands
- `/eco market` – Opens the market UI to browse and fulfill requests.
- `/eco market request <item> <amount> <price>` – Create an item request.
- `/eco market claim` – Claim items bought or requested while offline.

Server-side only.
