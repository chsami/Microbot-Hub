# NMZ Plugin  Overview
## Purpose
NmzScript automates the Nightmare Zone (NMZ) minigame in Old School RuneScape using the Microbot framework. It manages inventory, potion usage, combat, prayer, and in-game interactions to maximize efficiency and survivability during NMZ runs.

![img.png](assets/img.png)

## Key Features
- Automated Inventory & Equipment Setup

- Loads and verifies inventory/equipment against a predefined setup.
Walks to the nearest bank if setup does not match, loads required items, and closes the bank.
NMZ Entry & Dream Start

- Walks to the NMZ entrance.
- Interacts with Dominic Onion to start a dream.
- Handles all required widget interactions to enter the dream.
Potion Management

- Handles fetching, storing, and consuming Overload and Absorption potions.
Ensures the correct number of potions are in inventory.
Uses Overload potions when appropriate and manages health reduction for absorption efficiency.
Combat Automation

- Enables auto-retaliate.
Attacks the nearest NPC if not in combat for a period.
Uses special attack when configured.
Prayer Management

- Toggles Protect from Melee and Rapid Heal prayers based on configuration and random triggers.
Orb Interactions

- Interacts with Zapper, Power Surge, and Recurrent Damage orbs if configured.
Self-Harm for Absorption Efficiency

- Uses Locator Orb or Dwarven Rock Cake to reduce HP to optimal absorption levels.
Absorption Potion Usage

- Drinks absorption potions when absorption points fall below a randomized threshold.
Empty Vial Consumption

- Handles the consumption of empty vials to clear inventory space.
NMZ Reward Shop Automation

- Buys potions from the NMZ reward shop if points are sufficient.
Handles bank pin entry if required.
Error Handling & Logging

- Shuts down the bot if critical conditions are not met (e.g., not enough points to buy potions).
- Configuration Options
- Overload potion amount
- Absorption potion amount
- Use Zapper, Power Surge, Recurrent Damage orbs
- Toggle prayer potions
- Random mouse movements
- Walk to center
- Randomly trigger Rapid Heal


Note: This script is designed for advanced automation and may interact with game widgets, inventory, and other client features. Use responsibly and in accordance with game rules.