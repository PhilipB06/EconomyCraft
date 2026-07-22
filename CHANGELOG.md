### Features
- Added a `balance_separator` config option to customize the thousands separator used wherever a balance is displayed
- Added `top_name`, `top_balance`, and `top_balance_formatted` leaderboard placeholders (e.g. `%economycraft:top_name 1%` for the richest player)
- `/sell` now routes to an open order instead of the server whenever that order pays more per item, before falling back to the server price for the rest
- `/shop`, `/servershop`, and `/orders` (including the claim screen) now show a preview for filled shulker boxes (hover the item and press Ctrl+Q), letting players see the contents
- Server shop (`prices.json`) entries can now carry a `components` field for items that need specific NBT (custom names, enchantments, shulker boxes with contents, etc.); add a `#label` suffix to the key (e.g. `minecraft:shulker_box#loot_rare`) to sell several different variants of the same item
- `/orders request <item>` now tab-completes to any item id, vanilla or modded

### Improvements
- Simplified `shop.json`/`orders.json` storage by dropping redundant top-level item fields (item identity now comes only from the existing `stack` field)
- Shop and order deliveries are now stored in one shared `deliveries.json` instead of being duplicated inside `shop.json` and `orders.json`, with any pending deliveries migrated automatically on first load
- `/shop` listings and `/orders` requests now consistently show newest first, instead of an unspecified order

### Fixes
- Fixed a PvP kill-reward exploit by a misconfigured `pvp_balance_loss_percentage`
- Fixed the same class of bug in `taxRate`
- Fixed `/daily` deducting money instead of granting it if `dailyAmount` was configured negative
- Fixed a crash on startup, and in `/daily`, from a corrupted null entry in `balances.json` or `daily.json`
- Fixed a shop listing or order request with unreadable item data becoming a stuck "phantom" delivery that reappeared as an unclaimed item on every login
- Fixed a duplication exploit in the deliveries/claim screen
- Fixed `/orders request <item>` being unable to request modded items at all
