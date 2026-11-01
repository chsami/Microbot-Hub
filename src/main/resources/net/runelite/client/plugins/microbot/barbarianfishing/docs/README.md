# Barbarian Fishing Plugin

The **Barbarian Fishing Plugin** is an automation tool for Old School RuneScape, designed to efficiently train Fishing, Strength, and Agility by automating barbarian fishing at **Otto's Grotto** and **Mount Quidamortem** (Chambers of Xeric lake). Built for the Microbot RuneLite client with pure native `Rs2Walker` navigation.

---

## Features

- **Multiple Fishing Locations:**  
  - **Otto's Grotto:** Classic barbarian fishing area with power-dropping.
  - **Mount Quidamortem:** Lake right beside Chambers of Xeric with optional bank trips to the CoX bank chest.

- **Native Rs2Walker Navigation:**  
  Seamless movement between fishing spots and the Mount Quidamortem bank chest using standard `Rs2Walker`.

- **Fish Barrel Support:**  
  Automatically fills your fish barrel (open or closed) while inventory fills up, doubling your trip capacity before dropping or banking.

- **CoX Bank Chest Routing:**  
  When configured at Mount Quidamortem, walks to the nearby bank chest (`30087`), opens the bank via `Rs2TileObjectCache`, empties the fish barrel, deposits fish, and walks back to the lake.

- **Dragon Harpoon Special Attack:**  
  Optionally activates the Dragon Harpoon special attack whenever special energy reaches 100% for a temporary +3 Fishing level boost.

- **Configurable Dropping:**  
  Drop leaping trout, salmon, and sturgeon with configurable drop patterns (Standard, Left-to-right, Top-to-bottom, Snake, Random).

- **Real-Time Overlay:**  
  Displays location, current action, fish caught, XP gained, XP/hr, runtime, and fish barrel status.

---

## Requirements

- Barbarian fishing rod and feathers / fishing bait / fish offal in inventory.
- Required Fishing, Agility, and Strength levels for barbarian leaping fish (48+ Fishing, 15+ Agility/Strength for leaping trout; 58+ for leaping salmon; 70+ for leaping sturgeon).
- (Optional) Fish barrel for expanded capacity.
- (Optional) Dragon Harpoon equipped or in inventory for special attack boosts.

---

## Configuration

- **Location:** Choose between **Otto's Grotto** and **Mount Quidamortem**.
- **Drop Order:** Select inventory drop ordering (Standard, Top-to-bottom, Left-to-right, Snake, Random).
- **Use Fish Barrel:** Enable to store leaping fish in your fish barrel.
- **Bank at CoX:** (Mount Quidamortem only) Walk to the Chambers of Xeric bank chest to empty fish barrel and deposit catch instead of dropping.
- **Use Dragon Harpoon Spec:** Automatically trigger special attack when at 100%.

---

## Changelog

### 1.2.0
- Added Mount Quidamortem (Chambers of Xeric) location support.
- Added native `Rs2Walker` spot routing and CoX bank chest navigation.
- Added Fish Barrel auto-fill and auto-empty at CoX bank chest.
- Added Dragon Harpoon special attack support.
- Migrated tile object interactions to `Rs2TileObjectCache` query API.
- Replaced third-party walkers with standard `Rs2Walker`.
