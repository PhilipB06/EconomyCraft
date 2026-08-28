### Features
- Added an in-game **Transactions** viewer: players browse their own balance history from `/eco` or `/eco transactions`, and admins can browse any player's history (with a category filter) from Admin → Players. Shop, auction and order transactions now record which item and how many.
- New orders reserve payment upfront; cancelling refunds what's left.
- Orders and auction listings now expire after a configurable time.
- Added `max_active_orders_per_player` and `max_active_auctions_per_player` config options (`0` = unlimited), overridable per player from the admin Players menu.

### Improvements

### Fixes
- Fixed a possible crash/corruption from the TAB balance placeholder.
- Fixed some player names getting permanently stuck as unresolved.
