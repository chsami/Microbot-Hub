# Auto Herblore Plugin

The **Auto Herblore Plugin** automates herblore training with support for cleaning herbs and making various types of potions.

---

## Feature Overview

| Feature                    | Description                                                    |
|----------------------------|----------------------------------------------------------------|
| **Herb Cleaning**          | Automatically cleans grimy herbs                              |
| **Potion Making**          | Creates unfinished potions and finished potions               |
| **Multiple Modes**         | Support for different herblore training methods               |
| **Banking Integration**    | Automatically handles banking for supplies                     |
| **Herb Selection**         | Choose specific herbs to clean or use for potions             |
| **Potion Selection**       | Select from various potion types for automated creation       |

---

## Requirements

- Appropriate Herblore level for selected activity
- Required herbs, vials of water, and secondary ingredients
- Access to a bank
- Pestle and mortar (for some activities)

---

## Supported Training Methods

### Herb Cleaning
- **Grimy to Clean**: Automatically cleans grimy herbs
- **All Herb Types**: Supports all herbs from Guam to Torstol
- **Banking**: Withdraws grimy herbs and banks clean herbs

### Unfinished Potions
- **Vial + Herb**: Combines clean herbs with vials of water
- **All Potion Bases**: Creates unfinished potions for all types
- **Efficient Banking**: Manages herb and vial supplies

### Finished Potions
- **Complete Potions**: Adds secondary ingredients to unfinished potions
- **Various Types**: Supports combat, skilling, and utility potions
- **Ingredient Management**: Handles all required components

---

## Configuration Options

| Setting              | Description                                        |
|----------------------|----------------------------------------------------|
| **Training Mode**    | Select herb cleaning, unfinished, or finished potions |
| **Herb Type**        | Choose which herb to process                       |
| **Potion Type**      | Select specific potion to create                   |
| **Banking**          | Enable/disable automatic banking                   |

---

## Supported Herbs

| Herb      | Level | Common Potions                    |
|-----------|-------|-----------------------------------|
| **Guam**  | 1     | Relicym's balm, Antipoison        |
| **Marrentill** | 5 | Antipoison, Strength potion      |
| **Tarromin** | 11  | Serum 207, Relicym's balm        |
| **Harralander** | 20 | Compost potion, Energy potion   |
| **Ranarr** | 25    | Prayer potion, Defence potion    |
| **Toadflax** | 30  | Saradomin brew, Combat potion     |
| **Irit**   | 40    | Super attack, Fishing potion     |
| **Avantoe** | 48   | Fishing potion, Hunter potion     |
| **Kwuarm** | 54    | Super strength, Weapon poison     |
| **Snapdragon** | 59 | Super restore, Sanfew serum      |
| **Cadantine** | 65 | Super defence, Battlemage potion  |
| **Lantadyme** | 67  | Antifire potion, Magic potion     |
| **Dwarf weed** | 70 | Ranging potion, Magic potion      |
| **Torstol** | 75   | Super combat, Zamorak brew        |

---

## How It Works

1. Configure your desired training method and materials
2. Ensure you have the required Herblore level
3. Have necessary supplies in your bank (herbs, vials, secondaries)
4. Position yourself near a bank
5. Start the plugin
6. The plugin will:
   - Withdraw required materials from bank
   - Process herbs or create potions as configured
   - Bank finished products and withdraw new supplies
   - Repeat the process continuously

---

## Troubleshooting

- **Insufficient Herblore level**: Train to the required level for your selected activity
- **Missing supplies**: Ensure you have all required herbs, vials, and secondary ingredients
- **Banking issues**: Verify you're positioned near a supported bank
- **Wrong potion type**: Check that you've selected a potion matching your herb type

---

## Limitations

- Must have appropriate Herblore level for selected activities
- Requires manual setup of supplies in bank
- Cannot handle interruptions from other players or events
- Some potions require specific quest completions

