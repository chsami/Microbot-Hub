# Auto Bolt Enchanter

Automatically enchants crossbow bolts using the Enchant Crossbow Bolt spell. The plugin handles all magic levels and bolt types with intelligent staff detection and rune management.

## Features

- **Smart Staff Detection**: Automatically detects and equips the best available staff for your selected bolt type
- **Rune Management**: Calculates required runes based on equipped staff and bolt selection
- **Banking Integration**: Automatically withdraws required supplies and handles inventory management
- **All Bolt Types**: Supports all crossbow bolt types from Opal (level 4) to Onyx (level 87)
- **Dragon Bolt Support**: Full support for dragon crossbow bolts
- **Error Recovery**: Built-in error handling and state recovery

## Setup Instructions

1. **Requirements**:
   - Required Magic level for your selected bolt type
   - Crossbow bolts (unenchanted) in bank
   - Required runes in bank (or suitable staff equipped)
   - Access to a bank

2. **Configuration**:
   - Select your desired bolt type from the dropdown menu
   - The plugin will automatically calculate required runes and magic level

3. **Starting the Plugin**:
   - Position yourself near any bank
   - Start the plugin
   - The plugin will handle all banking, rune management, and enchanting

## Supported Bolt Types

| Bolt Type | Magic Level | Runes Required |
|-----------|-------------|----------------|
| Opal | 4 | 2 Air + 1 Cosmic |
| Sapphire | 7 | 1 Water + 1 Cosmic + 1 Mind |
| Jade | 14 | 2 Earth + 1 Cosmic |
| Pearl | 24 | 2 Water + 1 Cosmic |
| Emerald | 27 | 3 Air + 1 Cosmic + 1 Nature |
| Topaz | 29 | 2 Fire + 1 Cosmic |
| Ruby | 49 | 5 Fire + 1 Blood + 1 Cosmic |
| Diamond | 57 | 10 Earth + 1 Cosmic + 2 Law |
| Dragonstone | 68 | 15 Earth + 1 Cosmic + 1 Soul |
| Onyx | 87 | 20 Fire + 1 Cosmic + 1 Death |

*All bolt types also have Dragon variants with identical requirements*

## Staff Recommendations

The plugin will automatically select the best available staff for your bolt type:

- **Air Staff**: For Opal and Emerald bolts
- **Water Staff**: For Sapphire and Pearl bolts  
- **Earth Staff**: For Jade, Diamond, and Dragonstone bolts
- **Fire Staff**: For Topaz, Ruby, and Onyx bolts
- **Combination Staves**: Automatically prioritized for maximum rune savings

## Known Limitations

- Must start near a bank
- Requires manual setup of required supplies in bank
- Plugin will stop if required items are missing
- Does not handle interruptions from combat or other players

## Troubleshooting

- **"Missing required items"**: Ensure you have enough bolts and runes in your bank
- **"Insufficient magic level"**: Train Magic to the required level for your selected bolt type
- **Plugin stops unexpectedly**: Check that you have sufficient runes for your selected bolt type

## Safety Features

- Validates magic level before starting
- Checks for required items before beginning
- Built-in timeout protection to prevent infinite loops
- Automatic error recovery and state management