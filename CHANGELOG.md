### Features
- Added optional dynamic shop pricing: buy prices scale with the median balance of active players, so purchasing power stays roughly constant as the server economy grows. Sell prices are never affected. Configurable multiplier bounds and active-player window, cached and recalculated hourly. Categories and individual items can opt out.
- The Sell UI can now sell shulker boxes. Can be cycled between rejecting them, selling only their contents, and selling the box together with its contents.
- Added in-game transactions viewer: players browse their own balance history, while admins can browse any player's history.
- Orders and auction listings now expire after a configurable time.
- Added `max_active_orders_per_player` and `max_active_auctions_per_player` config options (`0` = unlimited), overridable per player from the admin Players menu.

### Improvements
- New orders now reserve payment upfront.

### Fixes
- Fixed a possible crash/corruption from the TAB balance placeholder.
- Fixed some player names getting permanently stuck as unresolved.
