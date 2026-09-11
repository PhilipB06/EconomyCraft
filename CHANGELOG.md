### Features
- Added optional dynamic shop pricing.
- The Sell UI can now sell shulker boxes.
- Added in-game transactions viewer.
- Orders and auction listings now expire after a configurable time.
- Added `max_active_orders_per_player` and `max_active_auctions_per_player` config options (`0` = unlimited), overridable per player from the admin Players menu.

### Improvements
- Paginated menus now use recipe-book style page controls in the gap above Inventory. They stay hidden when there is only one page.
- Menus with a Back action now use Minecraft's transferable-list unselect arrow next to the title instead of a barrier in the footer.
- New orders now reserve payment upfront.

### Fixes
- Fixed a possible crash/corruption from the TAB balance placeholder.
- Fixed some player names getting permanently stuck as unresolved.