### Features
- Damaged items can now be sold.
- Enchantments now add to an item's sell price, and enchanted books sell for all of their enchantments.
- Raised the max balance to `$1Qi` (previously `$999B`), about a million times more.
- Added an `economy.max_balance` setting to `config.json` and the admin settings panel.
- Added a `dynamic_prices.update_minutes` setting to control how often dynamic prices update.
- Configurable currency: `display.currency_symbol`, `display.currency_symbol_position` (`prefix`/`suffix`) and an optional `display.currency_name` for chat messages, also editable in the admin settings panel. Applies to menus, chat, placeholders, the sidebar, the webhook and the API.
- API: added `formatMoneyChat(long)` and `parseMoney(String)` so addons can show chat amounts and read typed amounts like `1.5k` the same way EconomyCraft does.

### Improvements
- `config.json` is now grouped into sections. Existing configs are converted automatically.
- The admin settings screen is now split into matching categories.
- Architectury API is no longer required.
- Item pickers now include items in your offhand.
- Added `Qa` (quadrillion) and `Qi` (quintillion), e.g. `/pay Steve 2.5Qa`.
- The transactions screen now shows the actual item/player head. Older entries keep their previous icon.
- A broken file now stops the server with an error that names the file.

### Fixes
- Fixed auction sales showing as "Bought" in the seller's Transactions.
- Fixed a balance sidebar order bug.
- Fixed fulfilling an order taking worn armor.
- Fixed the singleplayer import skipping leaderboard stats.
- Fixed a buy-and-resell money loop. Both multipliers now have a minimum of `0.5`.
- Fixed enchanted books randomly having no sell price after a server start.
- Fixed placing and cancelling an order raising a player's Earned, Spent, Bought and Traded stats for free.
- Fixed open sell, shop, auction and order menus still trading after an admin turned that feature off.