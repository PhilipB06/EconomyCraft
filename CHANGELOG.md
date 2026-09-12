### Features
- Added optional dynamic shop pricing.
- The Sell UI can now sell shulker boxes.
- Added in-game transactions viewer.
- Orders and auction listings now expire after a configurable time.
- Added `max_active_orders_per_player` and `max_active_auctions_per_player` config options (`0` = unlimited), overridable per player from the admin Players menu.

### Improvements
- The shop, auction house, orders, admin shop, item picker, and player picker GUIs now have a recipe-book style search field on the right of the title bar. Typing filters in place; `/eco search` with no query clears it.
- New orders now reserve payment upfront.

### Fixes
- Fixed a possible crash/corruption from the TAB balance placeholder.
- Fixed some player names getting permanently stuck as unresolved.