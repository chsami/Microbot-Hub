# NMZ Plugin  Overview

## Repeating rounds and potion restocking (2.4.8)

Enable **Repeat rounds** to restock and enter your previous dream after a round.
Its initial value follows the existing **Stop after death** preference (off for
the default stop-after-death setting). When explicitly enabled, it takes
precedence over **Stop after death**. Keep a previous dream and coffer funds ready.

Potion amounts are four-dose bottles; purchases and barrel stock are doses.
Restocking counts full bottles already carried, stores partial/excess bottles,
buys only missing doses and withdraws only missing bottles. Both potion targets
must be ready before entry, including zero-overload and prayer-potion setups.
Unconfirmed deposits never drop leftover potions.

Enable **Fill potion barrels when buying** to fill an understocked barrel to
255 doses. Buy-X requests the difference (35 stored doses requests 220), with
confirmed Buy-50/10/5/1 batches as a fallback. The option defaults off, retaining
trip-shortfall purchases. Withdrawing prepared bottles does not cause another
top-up in the same cycle. The quantity-title widget is checked before typing,
and withdrawal prompts are rejected.

Insufficient points, inventory space or required prayer potions stop the script
with a reason. Other failed lobby operations retry at most three times. Prayer
potions must be carried; bank restocking is not added by this update. One prayer
worker runs during dreams and stops with the plugin.

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
