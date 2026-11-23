# LinChiMCMod

Project/mod name: **LinChiMCMod**. In-game display name: **LinsMagic 材料计算器**.

A portable and easy-to-use calculator mod for Minecraft players on servers running the LinsMagic plugin.

Copyright 2026 DNT Group (https://dnteam.top)

## Overview

LinChiMCMod (shown in-game as LinsMagic 材料计算器) helps players track the base materials needed for magic items, follow crafting steps, and fill crafting tables automatically. It is a client-side Fabric mod with no server-side installation required. The mod bundles a default item database and can update it over the air (OTA) on startup.

## Features

- Material board (press `N`) showing all 2160 magic items with icons and names.
  - Search, add/remove targets, and adjust quantities.
  - The left panel is a combined target/material list. Craftable intermediates show an
    `Expand / Collapse` toggle, so you can decide whether to break an item down into its
    recipe or collect it as a material; the HUD recalculates immediately.
  - Recipe and usage views with JEI-style mouse gestures (left click for recipe,
    right click for usage, scroll to switch recipe types, click an ingredient to expand).
- Material HUD (bottom left) listing missing base materials.
  - Each row shows a plain missing count (no minus sign and no prefix).
  - Scans inventory and shulker boxes; refreshes periodically.
  - Completed rows disappear automatically.
- Crafting guide HUD (bottom right) listing remaining crafting steps.
- One-click crafting table fill: fills as many sets as possible (up to 64 items per slot),
  bounded by the remaining output count and the materials actually in your inventory.
  Clicks are sent through the server container-click path, and the remaining count refreshes
  shortly after crafting. Materials are matched by display name; missing materials are skipped
  silently, and shulker-box contents are not used for filling.
- In-game manual, shown once on first launch and always available from the config panel.
- The config panel shows the current mod version and includes a manual "Check updates"
  button that checks both the mod version and the item database version, then reports the result
  in the panel and chat.
- OTA item database: the server update JSON (`data.json`) carries `dbVersion` and an optional
  `dbUrl`. On first join after launch the client compares the local database version and file
  integrity, then downloads the latest database when needed. The local version is stored in
  `config/linchi_data_version.json` (starts at `1.0.0`), and the downloaded data goes to
  `config/linchi_data.json`. The automatic update can be disabled in the config panel.
- Optional mod update check (multiplayer only, cached for 24 hours).

## Keybindings

- `CTRL + P` : open / close the config panel (default P requires CTRL).
- `N` : open / close the material board.

Both keybindings can be changed in Minecraft Options -> Controls -> "LinsMagic 材料计算器" category.

## Configuration

Settings are saved to `config/linchi.json`. The config panel offers toggles for the two HUDs,
mod update checking, item glow and automatic database updates, plus sliders for HUD rows,
step count, refresh interval, background opacity and HUD offsets.

## Supported versions

Minecraft 26.3, 26.2 and 26.1 (Fabric).

Release JARs are named `LinChiMCMod-Fabric-<mod version>-<minecraft version>.jar`,
for example `LinChiMCMod-Fabric-1.1.1-26.3.jar`.

## Requirements

- Fabric Loader
- Fabric API
- Java 25 or newer

## Building

```
./gradlew build
```

The build output is placed in `build/libs/`.

## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

Copyright 2026 DNT Group (https://dnteam.top)
