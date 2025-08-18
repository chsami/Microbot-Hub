# Auto Bolt Enchanter Plugin

The **Auto Bolt Enchanter Plugin** automates the enchanting of crossbow bolts using the Enchant Crossbow Bolt spell with intelligent staff detection and rune management.

---

## Feature Overview

| Feature                     | Description                                                               |
|-----------------------------|---------------------------------------------------------------------------|
| **Smart Staff Detection**   | Automatically detects and equips the best available staff for bolt type  |
| **Rune Management**         | Calculates required runes based on equipped staff and bolt selection     |
| **Banking Integration**     | Automatically withdraws required supplies and manages inventory           |
| **All Bolt Types**          | Supports all crossbow bolt types from Opal (level 4) to Onyx (level 87) |
| **Dragon Bolt Support**     | Full support for dragon crossbow bolts                                   |
| **Error Recovery**          | Built-in error handling and state recovery                               |
| **Magic Level Validation**  | Validates required magic level before starting                           |

---

## Requirements

- Required Magic level for your selected bolt type
- Crossbow bolts (unenchanted) in bank
- Required runes in bank OR suitable elemental staff equipped
- Access to a bank
- Cosmic runes for all bolt types

---

## Supported Bolt Types

| Bolt Type     | Magic Level | Runes Required                  |
|---------------|-------------|---------------------------------|
| Opal          | 4           | 2 Air + 1 Cosmic               |
| Sapphire      | 7           | 1 Water + 1 Cosmic + 1 Mind    |
| Jade          | 14          | 2 Earth + 1 Cosmic             |
| Pearl         | 24          | 2 Water + 1 Cosmic             |
| Emerald       | 27          | 3 Air + 1 Cosmic + 1 Nature    |
| Topaz         | 29          | 2 Fire + 1 Cosmic              |
| Ruby          | 49          | 5 Fire + 1 Blood + 1 Cosmic    |
| Diamond       | 57          | 10 Earth + 1 Cosmic + 2 Law    |
| Dragonstone   | 68          | 15 Earth + 1 Cosmic + 1 Soul   |
| Onyx          | 87          | 20 Fire + 1 Cosmic + 1 Death   |

*All bolt types also have Dragon variants with identical requirements*

---

## Configuration Options

- **Bolt Type**: Select which type of bolt to enchant from the dropdown

---

## How It Works

1. Select your desired bolt type from the plugin configuration
2. Position yourself near any bank
3. Ensure you have the required bolts and runes (or suitable staff) in your bank
4. Start the plugin
5. The plugin will:
   - Validate your magic level
   - Automatically detect and equip the best available staff
   - Withdraw required runes and bolts
   - Cast Enchant Crossbow Bolt spells
   - Handle all banking and inventory management

---

## Staff Recommendations

The plugin automatically selects the best available staff:

- **Air Staff**: For Opal and Emerald bolts
- **Water Staff**: For Sapphire and Pearl bolts  
- **Earth Staff**: For Jade, Diamond, and Dragonstone bolts
- **Fire Staff**: For Topaz, Ruby, and Onyx bolts
- **Combination Staves**: Automatically prioritized for maximum rune savings

---

## Troubleshooting

- **"Missing required items"**: Ensure you have enough bolts and runes in your bank
- **"Insufficient magic level"**: Train Magic to the required level for your selected bolt type
- **Plugin stops unexpectedly**: Check that you have sufficient runes for your selected bolt type
- **Staff not detected**: Ensure the staff is in your bank or inventory before starting

