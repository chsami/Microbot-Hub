# Blessed Wine Plugin

Automates Prayer Training using blessed wines at Cam Torum. This plugin efficiently manages the prayer training process by automating the blessing of wines and managing prayer points through the libation system.

## Features

- **Automated Wine Blessing**: Automatically blesses wines at the Exposed Altar
- **Prayer Management**: Uses the Libation Bowl to drain prayer points efficiently
- **Prayer Restoration**: Automatically restores prayer at the Shrine of Ralos
- **Banking Integration**: Seamlessly handles banking operations at Cam Torum
- **Progress Tracking**: Real-time overlay showing XP gains, loop counts, and status
- **Resource Management**: Automatically calculates optimal runs based on available materials

## Requirements

### Prerequisites
- Access to Cam Torum (requires completion of Varlamore quest line)
- Blessed bone shards
- Jugs of wine
- Calcified moths for teleportation

### Material Ratios
- **1 Wine per 400 Blessed Bone Shards**: Ensures optimal blessing efficiency
- **1 Calcified Moth per 10,400 Shards**: Provides teleportation for banking runs

## How It Works

The plugin operates in a continuous loop:

1. **Initialization**: Checks bank for required materials and calculates optimal run capacity
2. **Altar Phase**: Walks to Exposed Altar and blesses wines
3. **Libation Phase**: Uses Libation Bowl to drain prayer points
4. **Restoration Phase**: Restores prayer at Shrine of Ralos when needed
5. **Banking Phase**: Teleports to Cam Torum bank to restock materials
6. **Repeat**: Continues until materials are exhausted or target XP is reached

## Usage

1. Ensure you have the required materials in your bank
2. Start the plugin from the Microbot plugin panel
3. The plugin will automatically begin the blessing process
4. Monitor progress through the overlay display
5. The plugin will stop automatically when materials are exhausted

## Overlay Information

The plugin provides a comprehensive overlay showing:
- **Status**: Current action being performed
- **Loop Count**: Number of completed blessing cycles
- **Total Loops**: Expected total cycles based on materials
- **Wines Left**: Remaining wines to be blessed
- **Start XP**: Prayer XP at plugin start
- **Expected XP**: Target XP based on available materials
- **Current Gained XP**: XP gained in current session

## Safety Features

- **Automatic Prayer Management**: Never runs out of prayer points
- **Resource Monitoring**: Stops when materials are insufficient
- **Pathfinding**: Safe navigation between locations
- **Banking Integration**: Automatic material restocking

## Tips for Optimal Use

1. **Stock Up**: Ensure you have sufficient blessed bone shards and wines before starting
2. **Monitor Progress**: Keep an eye on the overlay for real-time status updates
3. **Location Awareness**: The plugin works best when starting near Cam Torum
4. **Material Efficiency**: Maintain the 1:400 wine-to-shard ratio for optimal runs

## Technical Details

- **Plugin Version**: 1.0.0
- **Author**: Hal
- **Minimum Client Version**: 1.9.6
- **Dependencies**: Core Microbot functionality only
- **Compatibility**: RuneLite with Microbot integration

## Support

For issues, questions, or feature requests, please refer to the main Microbot repository or contact the plugin author.

---

*This plugin automates the prayer training process at Cam Torum, providing an efficient way to gain prayer experience through the blessed wine system.*

