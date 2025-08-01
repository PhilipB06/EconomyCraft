# EconomyCraft

EconomyCraft provides a simple cross-platform economy system for Fabric and NeoForge servers. The mod requires Architectury API and targets Minecraft 1.21.7 and newer.

## Features

- `/eco balance [player]` – Check balances.
- `/eco pay <player> <amount>` – Transfer money.
- `/eco addmoney <player> <amount>` – Admin command.
- `/eco setmoney <player> <amount>` – Admin command.
Balances are saved to `economycraft_balances.json` and a live leaderboard is shown on the sidebar sorted by balance. Your rank appears below the top players.

### Shop Commands
- `/eco shop` – Opens the chest-based shop UI.
- `/eco shop sell <price>` – Sell the item in your hand.

### Market Commands
- `/eco market list` – Show all player item requests.
- `/eco market request <price>` – Request the item in your hand for a price.
- `/eco market fulfill <id>` – Fulfill a request using the item in your hand.
- `/eco market claim` – Claim items bought or requested while offline.

Server-side only.
