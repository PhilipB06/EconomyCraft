### Features
- Damaged items can now be sold.
- Enchantments now add to an item's sell price, and enchanted books sell for all of their enchantments.
- Raised the max balance to `$1Qi` (previously `$999B`), about a million times more.
- Added a `max_balance` setting to `config.json` and the admin settings panel.

### Improvements
- Item pickers now include items in your offhand.
- Added `Qa` (quadrillion) and `Qi` (quintillion), e.g. `/pay Steve 2.5Qa`.
- The Transactions screen now shows the actual item/player head. Older entries keep their previous icon.
- A broken file now stops the server with an error that names the file.

### Fixes
- Fixed auction sales showing as "Bought" in the seller's Transactions.
- Fixed a balance sidebar order bug.
- Fixed fulfilling an order taking worn armor.
- Fixed the singleplayer import skipping leaderboard stats.
- Fixed a buy-and-resell money loop. Both multipliers now have a minimum of `0.5`.
- Fixed enchanted books randomly having no sell price after a server start.