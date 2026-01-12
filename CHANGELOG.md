### New Features
- Added `/sell` command to quickly exchange items for money
    - `/sell all` sells all matching items from the inventory
    - Configurable daily sell limit
- Added server shop with infinite stock via `/servershop`
- Renamed `/balance` to `/bal`
- Added `/bal top` to view the richest players
- Player balance is now displayed across all relevant UIs
- Sellers are now notified when their item is sold

### Improvements
- Updated UI styling and overall design
- Tax values are no longer shown if tax is 0, either by config or low price

### Fixes
- Fixed internal error when using `/balance <player>`
