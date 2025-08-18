# Auto Herbiboar Plugin

The **Auto Herbiboar Plugin** automates herbiboar hunting on Fossil Island, extending the existing RuneLite Herbiboar plugin with automation capabilities.

---

## Feature Overview

| Feature                    | Description                                                    |
|----------------------------|----------------------------------------------------------------|
| **Automatic Trail Following** | Follows herbiboar trails automatically                    |
| **Tunnel Navigation**      | Navigates through herbiboar tunnels and search spots         |
| **Herb Collection**        | Automatically harvests herbs from herbiboar                   |
| **Error Recovery**         | Handles dead ends and confusion messages                      |
| **RuneLite Integration**   | Extends the official RuneLite Herbiboar plugin               |
| **Magic Secateurs Support** | Automatically equips magic secateurs when available         |

---

## Requirements

- **Fossil Island access** (Bone Voyage quest completion)
- **Level 80 Hunter** minimum
- **Level 31 Herblore** (for herbiboar hunting)
- **Completion of Fairytale I** (for magic secateurs benefit)
- **Magic secateurs** in inventory (optional, but recommended)
- **RuneLite Herbiboar plugin** (automatically enabled by this plugin)

---

## How It Works

1. The plugin automatically enables the RuneLite Herbiboar plugin
2. Start the plugin while on Fossil Island near herbiboar hunting area
3. The plugin will:
   - Automatically equip magic secateurs if available
   - Follow herbiboar trails by reading tracks
   - Navigate through tunnels and search spots
   - Handle dead ends and confusion by restarting
   - Harvest herbs from the herbiboar when found
   - Repeat the process for continuous hunting

---

## Setup Instructions

1. Complete the Bone Voyage quest to access Fossil Island
2. Achieve level 80 Hunter and level 31 Herblore
3. Obtain magic secateurs (optional but highly recommended)
4. Travel to Fossil Island herbiboar hunting area
5. Start the plugin

---

## Chat Message Handling

The plugin responds to specific game messages:
- **"The creature has successfully confused you..."** - Restarts the hunt
- **"You'll need to start again."** - Restarts the hunt  
- **"Nothing seems to be out of place here."** - Handles dead end tunnels

---

## Benefits of Magic Secateurs

- **Increased herb yield** when harvesting
- **Better experience rates**
- **More valuable herb drops**
- Plugin automatically equips them when available

---

## Troubleshooting

- **Plugin doesn't start**: Ensure you're on Fossil Island with required levels
- **Trail following issues**: Make sure the RuneLite Herbiboar plugin is enabled
- **No magic secateurs effect**: Ensure you have magic secateurs in inventory
- **Confusion loops**: The plugin automatically handles these by restarting

---

## Limitations

- **Must be on Fossil Island** in the herbiboar hunting area
- **Requires specific Hunter and Herblore levels**
- **Dependent on RuneLite Herbiboar plugin** for trail detection
- **May be affected by server lag** or crowded hunting areas

---

## Integration Notes

This plugin works as an extension of the official RuneLite Herbiboar plugin:
- Automatically enables the base plugin if disabled
- Uses the base plugin's trail detection and overlay system
- Adds automation on top of existing functionality

