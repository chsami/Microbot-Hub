# Basche's Auto MTA Plugin

## Overview
Automates the Mage Training Arena minigame in Old School RuneScape. This plugin automatically cycles through the four MTA rooms (Telekinetic, Graveyard, Enchantment, and Alchemist) to earn points and purchase rewards.

## Features
- **Automated room cycling**: Automatically handles all four MTA rooms
- **Smart reward targeting**: Configure which rewards to aim for
- **Repeat room functionality**: Option to repeat a specific room
- **Integrated with RuneLite MTA plugin**: Uses data from the official MTA plugin for optimal performance
- **Progress tracking**: Real-time overlay showing current progress and points
- **Comprehensive configuration**: Customizable settings for different playstyles

## Prerequisites
1. **Enable the official RuneLite "Mage Training Arena" plugin** - This is required for the bot to function properly
2. **Modern Spellbook**: Must be using the standard spellbook
3. **Required items in inventory only** (no rune pouch):
   - Staves and/or Tomes as configured
   - Law runes, Cosmic runes, Nature runes
   - Food for Graveyard room

## Equipment Requirements by Room

### Telekinetic Room
- Any staff or Magic weapon

### Graveyard Room
- Food (configurable healing thresholds)
- Any staff or Magic weapon

### Enchantment Room
- **T6 enchant**: Lava staff OR Tome of Fire + any Earth staff
- **T5 enchant**: Tome of Water + any Earth staff OR water/earth runes

### Alchemist Room
- Nature runes for High Level Alchemy
- Any staff or Magic weapon

## Configuration Options

### Rooms Section
- **Repeat Room**: Enable to stay in the current room instead of cycling

### Rewards Section
- **Buy rewards**: Whether to purchase rewards when points are reached
- **Reward**: Select target reward from dropdown:
  - All items (Collection Log completion)
  - Infinity equipment pieces
  - Wands (Beginner → Master progression)
  - Mage's Book
  - Rune Pouch
  - Bones to Peaches

### Graveyard Section
- **Healing threshold (min/max)**: Random healing threshold range for natural behavior

## How It Works

1. **Initialization**: Verifies MTA plugin is active and spellbook is correct
2. **Room Detection**: Identifies current location and room state
3. **Task Execution**: Performs room-specific activities:
   - **Telekinetic**: Moves maze guardians using Telekinetic Grab
   - **Graveyard**: Casts Bones to Peaches/Bananas on bones
   - **Enchantment**: Enchants jewelry with appropriate spells
   - **Alchemist**: Performs High Level Alchemy on items
4. **Progress Tracking**: Monitors points and determines when to change rooms or buy rewards
5. **Banking**: Handles restocking when needed

## Reward Logic

- **Buy Rewards Mode**: Cycles rooms until target reward points are met, then purchases
- **Collection Mode**: Continues cycling indefinitely for training
- **"All Items" Option**: Calculates points needed for complete Collection Log

## Safety Features

- Checks for required RuneLite MTA plugin
- Validates spellbook before starting
- Monitors health in Graveyard room
- Handles banking and restocking automatically
- Error detection and recovery

## Plugin Structure

### Core Files
- `MageTrainingArenaPlugin.java` - Main plugin class with Microbot-Hub integration
- `MageTrainingArenaScript.java` - Core automation logic
- `MageTrainingArenaConfig.java` - Configuration interface
- `MageTrainingArenaOverlay.java` - Progress display overlay

### Enums
- `Rooms.java` - MTA room definitions and locations
- `Rewards.java` - Available rewards and point requirements
- `Points.java` - Point types (Telekinetic, Graveyard, Enchantment, Alchemist)
- `EnchantmentShapes.java` - Enchantment room shape patterns
- `TelekineticRooms.java` - Telekinetic maze layouts

## Version
- **Current Version**: 1.1.4
- **Author**: Basche
- **Microbot-Hub Integration**: Yes

## Troubleshooting

### Common Issues
1. **Plugin not starting**: Ensure MTA plugin is enabled
2. **Wrong spellbook error**: Switch to Modern/Standard spellbook
3. **Inventory issues**: Remove rune pouch, use inventory runes only
4. **Equipment problems**: Verify staff/tome combinations for enchanting

### Performance Tips
- Start in desired room if using "Repeat Room" mode
- Ensure stable internet connection for banking operations
- Monitor progress via overlay for optimal point tracking

## Dependencies
- RuneLite MTA Plugin (official)
- RuneLite Skill Calculator
- Microbot Rs2 API suite
- Standard RuneLite base classes

This plugin provides a comprehensive automation solution for the Mage Training Arena minigame, supporting both casual players seeking specific rewards and completionists aiming for Collection Log completion.