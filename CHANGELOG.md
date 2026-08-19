### Features
- Added EconomyCraft API v1 for balances, payments, formatting, prices, leaderboards and balance events.
- Modded items now get added to `prices.json` automatically.
- Added sound effects.
- - Raised the maximum balance from `999,999,999` to `999,999,999,999`.
- Added `balance_short` and `top_balance_short` placeholders for abbreviated balances (e.g. `$1.2k`, `$36k`, `$234M`).
- `/pay`, `/addmoney`, `/setmoney` and `/removemoney` now accept shorthand amounts, e.g. `/pay Steve 20k` or `/setmoney Steve 1.2M`.

### Improvements
- Added support for Minecraft 1.21.1.
- Removed the scoreboard toggle command (UI-only now).
- Renamed the player shop to the auction house (`/ah`, `/auction`) and moved the server-shop to `/shop`.
- The balance sidebar now shows abbreviated balances (e.g. `$999B`) instead of the raw number.

### Fixes
- Fixed a text-input crash on NeoForge.
- Fixed name suggestions not filtering as you type.
- Prevented partial order fulfillment when it would pay nothing.
