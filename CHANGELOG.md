### Features
- Added support for 26.1.2 and 26.2.
- Added optional placeholder support for other mods (TAB, HUD mods, etc.), exposing `economycraft:balance`, `economycraft:balance_formatted`, and `economycraft:daily_sell_remaining`
- Added `/sell everything` to sell your entire inventory at once
- Added a `sell_enabled` config option to disable `/sell` entirely
- `/shop list <price>` now accepts an optional `<amount>`, to list only part of a held stack
- Server shop now shows the sell price alongside the buy price, and supports selling directly from the menu (right-click, or shift-click for a stack)
- Order requests can now be fulfilled partially from the Orders UI, not just in full

### Improvements
- Selling an enchanted item now asks for confirmation first, since enchantments don't add to the sell value
- The orders confirm screen only opens if you actually have items to give; the player shop confirm screen only opens if you can afford the purchase
- Server shop entries that fail to load are now logged with their item id instead of silently showing as unavailable
- `prices.json` now cleans up stale/renamed entries and sorts newly added ones into their category automatically, instead of leaving them at the bottom of the file

### Fixes
- Fixed splash and lingering potions (e.g. Splash Potion of Healing) showing as unavailable in the server shop
- Fixed the Potion of Wind Charging showing as unavailable and refusing to sell
- Fixed the balance scoreboard reclaiming the sidebar on every balance change (even while disabled), which could overwrite another plugin's or mod's own sidebar
