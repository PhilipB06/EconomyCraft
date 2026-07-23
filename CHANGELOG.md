### Fixes
- NeoForge: added placeholder-api-neoforge support for 1.21.11 (previously only available on 26.x).
- NeoForge: fixed the `placeholder-api-neoforge` presence check, which used an invalid mod ID and never actually matched, so placeholders never registered on any NeoForge version.