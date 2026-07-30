### Features
- Added search to `/shop`, `/servershop`, and `/orders`: use `search <query>` from the command, or click the Search button in the menu to type a query in-GUI. Also matches items stored inside a searched listing (e.g. a shulker box), not just its own name, and matches by enchantment (e.g. searching "mending" finds an Enchanted Book with Mending, whether it's the listing itself or tucked inside a shulker box in the listing).

### Fixes
- NeoForge: added placeholder-api-neoforge support for 1.21.11 (previously only available on 26.x).
- NeoForge: fixed the `placeholder-api-neoforge` presence check, which used an invalid mod ID and never actually matched, so placeholders never registered on any NeoForge version.