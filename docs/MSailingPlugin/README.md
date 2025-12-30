# Sailing Plugin

An automated sailing plugin that supports salvaging shipwrecks while sailing.

## Features

### Salvaging
- **Automatic Shipwreck Detection**: Finds and salvages nearby shipwrecks within a 15-tile radius
- **Smart Inventory Management**: Automatically handles full inventory by depositing salvage, alching valuable items, or dropping junk
- **Salvaging Station Support**: Deposits salvage at your boat's salvaging station (if installed)
- **Hook Deployment**: Automatically deploys your boat's salvaging hook on nearby shipwrecks

### Inventory Management
- **High Alching**: Automatically high alchs valuable items when inventory is full (configurable)
- **Junk Dropping**: Drops configured junk items to make room for salvage
- **Salvage Deposition**: Deposits salvage at salvaging stations for processing

### Visual Highlighting
- **Active Wrecks**: Highlights shipwrecks you can salvage (green by default)
- **Inactive Wrecks**: Highlights depleted shipwrecks/stumps (gray by default)
- **High Level Wrecks**: Highlights shipwrecks above your sailing level (red by default)
- **Customizable Colors**: All highlight colors are fully customizable
- **Salvageable Area**: Shows the 15-tile salvageable area around each shipwreck

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

**Drop Items** (default: common junk)
- Comma-separated list of items to drop when inventory is full
- Default includes: caskets, oyster pearls, logs, nails, planks, seeds, repair kits, etc.

### Salvaging Highlight Settings

**Enable Highlighting** (default: enabled)
- Toggle shipwreck highlighting overlay

**Highlight Active Wrecks** (default: enabled)
- Highlight shipwrecks you can salvage

**Active Wrecks Colour** (default: green)
- Color for active shipwrecks

**Highlight Inactive Wrecks** (default: disabled)
- Highlight depleted shipwrecks (stumps)

**Inactive Wrecks Colour** (default: gray)
- Color for inactive shipwrecks

**Highlight High Level Wrecks** (default: disabled)
- Highlight shipwrecks above your sailing level

**High Level Wrecks Colour** (default: red)
- Color for high level shipwrecks

## How It Works

1. **Detection**: The plugin scans for shipwrecks within a 15-tile radius using their object IDs
2. **Priority**: Finds the nearest shipwreck within the salvageable area
3. **Salvaging**: Deploys your boat's salvaging hook when a wreck is found and inventory has space
4. **Inventory Management**: When inventory fills up:
   - If salvage items exist and player isn't animating:
     - If you have a salvaging station on your boat: deposits salvage at the station
     - If you don't have a salvaging station: drops junk items
   - If alching is enabled: high alchs configured items
   - Drops remaining junk items
5. **Repeat**: Continues the cycle while the plugin is enabled

## Shipwreck Types

The plugin recognizes the following shipwreck types with their level requirements:

- Small Shipwreck (level 15)
- Fisherman Shipwreck (level 26)
- Barracuda Shipwreck (level 35)
- Large Shipwreck (level 53)
- Pirate Shipwreck (level 64)
- Mercenary Shipwreck (level 73)
- Fremennik Shipwreck (level 80)
- Merchant Shipwreck (level 87)

## Tips

- **Recommended Worlds**: Use world 596 or 597 for the best salvaging experience
- **Boat Equipment**: Ensure your boat has a salvaging hook installed to collect salvage from shipwrecks
- **Salvaging Station**: Install a salvaging station on your boat to automatically process salvage items when inventory is full
- **Alching Setup**: Ensure you have nature runes and a fire staff (or fire runes) for high alching
- **Item Configuration**: Customize the alch and drop lists based on your needs and which items you want to keep
- **Highlight Colors**: Adjust highlight colors if the defaults don't stand out enough on your screen
- **Inventory Space**: The plugin considers inventory "full" at 24-28 items (randomized) to handle salvage variations

## Requirements

- Sailing skill unlocked
- A boat with a salvaging hook installed
- Nature runes for alching (if enabled)
- Fire runes or fire staff for alching (if enabled)

## Optional Equipment

- **Salvaging Station**: Install on your boat to automatically process salvage items (recommended for best efficiency)
- Without a salvaging station, the plugin will drop junk items when inventory fills with salvage

## Known Limitations

- Requires shipwrecks to be within 15 tiles of the player
- Will wait if player is already animating (salvaging)
- Without a salvaging station on your boat, salvage items will be dropped when inventory is full

## Version History

**0.0.1** - Initial release
- Automatic shipwreck salvaging
- Inventory management with alching and dropping
- Shipwreck highlighting overlay
- Salvaging station support
