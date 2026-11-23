# LinChi

A portable and easy-to-use calculator mod for Minecraft players on servers running the LinsMagic plugin.

Copyright 2026 DNT Group (https://dnteam.top)

## Overview

LinChi helps players track the base materials needed for magic items, follow crafting steps,
and fill crafting tables automatically. It is a client-side Fabric mod with no server-side
installation required. Data is bundled inside the mod; no external data download is needed.

## Features

- Material board (press `N`) showing all 2160 magic items with icons and names.
  - Search, add/remove targets, and adjust quantities.
  - Recipe and usage views with JEI-style mouse gestures (left click for recipe,
    right click for usage, scroll to switch recipe types, click an ingredient to expand).
- Material HUD (bottom left) listing missing base materials.
  - Scans inventory and shulker boxes; refreshes periodically.
  - Completed rows disappear automatically.
- Crafting guide HUD (bottom right) listing remaining crafting steps.
- One-click crafting table fill (one item per slot, hold Shift to fill multiple sets),
  synchronized with the server.
- In-game manual, shown once on first launch and always available from the config panel.
- Optional update check (multiplayer only, cached for 24 hours).

## Keybindings

- `CTRL + P` : open / close the config panel (default P requires CTRL).
- `N` : open / close the material board.

Both keybindings can be changed in Minecraft Options -> Controls -> "LinChi" category.

## Configuration

Settings are saved to `config/linchi.json`. The config panel offers toggles for the two HUDs,
update checking and item glow, plus sliders for HUD rows, step count, refresh interval,
background opacity and HUD offsets.

## Supported versions

Minecraft 26.3, 26.2 and 26.1 (Fabric).

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
