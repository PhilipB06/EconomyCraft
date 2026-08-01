### Features
- Added `/eco`, a menu covering every player feature: the shops, selling, orders, deliveries, paying, item values and the leaderboard. Listing items, posting orders and selling all happen there now.
- Added an Admin menu for operators: a server shop editor, a settings editor for every `config.json` option, and player balance management.
- Added search and sorting to `/shop`, `/servershop` and `/orders`.
- Added `/worth [<item> [<amount>]]` to check an item's buy and sell price.
- Added `shop_enabled` and `orders_enabled` to switch off the player shop and the orders board. Collecting deliveries keeps working either way.
- Replaced `/sell`'s subcommands with a menu.

### Improvements
- Feature switches apply immediately instead of needing a restart.
- Menus use one colour scheme: gold labels for values, aqua for click actions, grey for hints.

### Fixes
- Fixed every singleplayer world sharing one economy and one config.
- Fixed 12 brewing entries showing as unavailable in the server shop.
- Fixed `server_shop_enabled` only taking effect when commands were registered.
