### Fixes
- Fixed a PvP kill-reward exploit by a misconfigured `pvp_balance_loss_percentage`
- Fixed the same class of bug in `taxRate`
- Fixed `/daily` deducting money instead of granting it if `dailyAmount` was configured negative
- Fixed a crash on startup, and a crash in `/daily` for one player, if `balances.json` or `daily.json` contained a corrupted null entry
