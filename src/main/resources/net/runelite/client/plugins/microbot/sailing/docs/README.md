# Sailing Plugin

An automated sailing plugin that supports salvaging shipwrecks while sailing.

## Features

### Salvaging
- **Automatic Shipwreck Detection**: Finds and salvages nearby shipwrecks within a 15-tile radius
- **Smart Inventory Management**: Checks inventory status before attempting to salvage — clears a full inventory before looking for new wrecks
- **Salvaging Station Support**: Deposits salvage at your boat's salvaging station (if installed)
- **Hook Deployment**: Automatically deploys your boat's salvaging hook on nearby shipwrecks

### Inventory Management
- **High Alching**: Exhausts all stacks of each configured item before moving to the next (fixed from original single-item-per-name behaviour)
- **Configurable Alch Order**: Choose the order items are alched across your inventory (see Alch Order below)
- **Casket Opening**: Optionally open all caskets before alching/dropping to maximise loot from casket drops
- **Junk Dropping**: Drops configured junk items to make room for salvage — runs before casket opening and again after to catch any junk from casket loot
- **Salvage Deposition**: Deposits salvage at salvaging stations for processing

### Visual Highlighting
- **Active Wrecks**: Highlights shipwrecks you can salvage (green by default)
- **Inactive Wrecks**: Highlights depleted shipwrecks/stumps (gray by default)
- **High Level Wrecks**: Highlights shipwrecks above your sailing level (red by default)
- **Customizable Colors**: All highlight colors are fully customizable

## Configuration

### General Settings

**Salvaging** (default: disabled)
- Enable this to start the salvaging automation

**Enable Alching** (default: disabled)
- Automatically high alch valuable items when inventory is full
- Requires nature runes and fire runes/staff

**Alch Items** (default: rings and bracelets)
- Comma-separated list of items to high alch
- Default: `gold ring, sapphire ring, emerald ring, ruby ring, diamond ring, ruby bracelet, emerald bracelet, diamond bracelet, mithril scimitar`
- All stacks of each item are fully alched before moving to the next item on the list

**Open Caskets** (default: disabled)
- When enabled, opens all caskets in your inventory as part of the full-inventory routine
- Caskets are opened after the first drop pass (to ensure space for loot) and before alching
- Any junk from casket loot is caught by a second drop pass after alching

**Alch Order** (default: LIST_ORDER)
- Controls the order in which matching items are alched across your inventory
- `LIST_ORDER` — alches by item name order in your Alch Items list (original behaviour)
- `LEFT_TO_RIGHT` — sweeps inventory row by row, left to right (slot 0 → 27)
- `RIGHT_TO_LEFT` — sweeps inventory row by row, right to left
- `TOP_TO_BOTTOM` — sweeps inventory column by column, top to bottom (slot 0, 4, 8... then 1, 5, 9...)
- `BOTTOM_TO_TOP` — sweeps inventory column by column, bottom to top

**Drop Items** (default: common junk)
- Comma-separated list of items to drop when inventory is full
- Default includes: caskets, oyster pearls, logs, nails, planks, seeds, repair kits, etc.

### Salvaging Highlight Settings

**Enable Highlighting** (default: enabled)
- Toggle shipwreck highlighting overlay

**Highlight Active Wrecks** (default: enabled)
- Highlight shipwrecks you can salvage

**Active Wrecks Colour** (default: green)

**Highlight Inactive Wrecks** (default: disabled)
- Highlight depleted shipwrecks (stumps)

**Inactive Wrecks Colour** (default: gray)

**Highlight High Level Wrecks** (default: disabled)
- Highlight shipwrecks above your sailing level

**High Level Wrecks Colour** (default: red)

## How It Works

1. **Inventory Check First**: On each tick, the plugin checks whether inventory is full before looking for wrecks
2. **If inventory is full**:
   - If salvage items are present → deposit at salvaging station (or drop junk if no station)
   - Otherwise:
     1. Drop configured junk items
     2. Open caskets (if enabled) — space has been made by the drop step
     3. High alch configured items (if enabled) — includes any loot from opened caskets
     4. Drop again — catches any junk that came from casket loot
3. **If inventory has space**: find the nearest wreck and deploy the salvaging hook
4. **Repeat**

## Shipwreck Types

| Wreck | Level Required |
|---|---|
| Small Shipwreck | 15 |
| Fisherman Shipwreck | 26 |
| Barracuda Shipwreck | 35 |
| Large Shipwreck | 53 |
| Pirate Shipwreck | 64 |
| Mercenary Shipwreck | 73 |
| Fremennik Shipwreck | 80 |
| Merchant Shipwreck | 87 |

## Tips

- **Recommended Worlds**: Use world 596 or 597 for the best salvaging experience
- **Boat Equipment**: Ensure your boat has a salvaging hook installed
- **Salvaging Station**: Install a salvaging station on your boat to automatically process salvage
- **Alching Setup**: Ensure you have nature runes and a fire staff (or fire runes) for high alching
- **Casket Tip**: If using Open Caskets, consider removing "casket" from your Drop Items list so caskets are opened rather than dropped
- **Alch Order**: If your alching looks robotic, try `LEFT_TO_RIGHT` or `TOP_TO_BOTTOM` for more natural-looking inventory traversal
- **Inventory Space**: The plugin considers inventory "full" at 24-28 items (randomized) to handle salvage variations

## Requirements

- Sailing skill unlocked
- A boat with a salvaging hook installed
- Nature runes for alching (if enabled)
- Fire runes or fire staff for alching (if enabled)

## Optional Equipment

- **Salvaging Station**: Install on your boat to automatically process salvage items (recommended)
- Without a salvaging station, the plugin will drop junk items when inventory fills with salvage

## Known Limitations

- Requires shipwrecks to be within 15 tiles of the player
- Will wait if player is already animating (salvaging)
- Without a salvaging station, salvage items will be dropped when inventory is full
- Casket opening requires at least one free inventory slot — the drop pass before opening handles this in normal usage

## Version History

**2.1.0**
- Fixed alch loop only alching one of each item instead of exhausting all stacks
- Fixed inventory check now happens before wreck detection (prevents attempting to salvage with a full inventory)
- Added Open Caskets option: drops junk → opens caskets → alchs loot → drops remainder
- Added Alch Order config option with five modes: LIST_ORDER, LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, BOTTOM_TO_TOP

**2.0.1**
- Automatic shipwreck salvaging
- Inventory management with alching and dropping
- Shipwreck highlighting overlay
- Salvaging station support
