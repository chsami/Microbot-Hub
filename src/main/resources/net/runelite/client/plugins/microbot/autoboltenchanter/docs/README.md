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

