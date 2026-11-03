# Barbarian Fishing Plugin

The **Barbarian Fishing Plugin** is an automation tool for Old School RuneScape, designed to efficiently train Fishing, Strength, and Agility by automating barbarian fishing at **Otto's Grotto** and **Mount Quidamortem** (Chambers of Xeric lake).

---

## Features

- **Multiple Fishing Locations:**  
  - **Otto's Grotto:** Classic barbarian fishing area with power-dropping.
  - **Mount Quidamortem:** Lake right beside Chambers of Xeric with optional bank trips to the CoX bank chest.

- **Automatic Walking:**  
  Walks to the next fishing spot when it moves, and to the CoX bank chest and back when banking.

- **Fish Barrel Support:**  
  Automatically fills your fish barrel (open or closed) while inventory fills up, doubling your trip capacity before banking. The barrel is only emptied at the CoX bank chest, so it matters only with **Bank at CoX** on; when power-dropping it fills once and stays full.

- **CoX Bank Chest Routing:**  
  With **Bank at CoX** on at Mount Quidamortem, walks to the nearby bank chest, opens it, empties the fish barrel, deposits the fish, and walks back to the lake. If the chest cannot be reached or opened after a few tries, the fish are dropped instead.

- **Dragon Harpoon Special Attack:**  
  Optionally activates the Dragon Harpoon special attack whenever special energy reaches 100% for a temporary +3 Fishing level boost.

- **Configurable Dropping:**  
  Drop leaping trout, salmon, and sturgeon with configurable drop patterns (STANDARD, EFFICIENT_ROW, COLUMN, EFFICIENT_COLUMN, ZIGZAG, RANDOM).

- **Real-Time Overlay:**  
  Displays location, current action, fish caught, XP gained, XP/hr, runtime, and fish barrel status.

---

## Requirements

- Start at Otto's Grotto or the Mount Quidamortem lake with a Barbarian rod (or Pearl barbarian rod) and feathers / fishing bait / fish offal in inventory.
- Fishing, Agility and Strength levels for each fish: leaping trout 48/15/15, leaping salmon 58/30/30, leaping sturgeon 70/45/45.
- (Optional) Fish barrel for expanded capacity.
- (Optional) Dragon harpoon equipped for special attack boosts.

---

## Configuration

- Location is detected automatically (**Otto's Grotto** or **Mount Quidamortem**); there is no Location setting.
- **Drop Order:** Select inventory drop ordering (STANDARD, EFFICIENT_ROW, COLUMN, EFFICIENT_COLUMN, ZIGZAG, RANDOM).
- **Use Fish Barrel:** Store leaping fish in your fish barrel. The barrel is only emptied at the CoX bank chest, so it matters only with Bank at CoX on.
- **Bank at CoX:** (Mount Quidamortem only) Walk to the Chambers of Xeric bank chest to empty fish barrel and deposit catch instead of dropping.
- **Dragon Harpoon Spec:** Automatically trigger special attack when at 100%.

---

## Changelog

### 1.2.0
- Added Mount Quidamortem (Chambers of Xeric) location support.
- Walks to fishing spots and the CoX bank chest automatically.
- Added Fish Barrel auto-fill and auto-empty at CoX bank chest.
- Added a Dragon Harpoon Spec toggle (on by default; only used when the Dragon harpoon is equipped).
- The plugin now stops and shows a message when your Barbarian rod or feathers/bait run out (it used to stand idle).
- Use Fish Barrel is on by default: a Fish barrel in your inventory is filled when your inventory is full (it is only emptied with Bank at CoX).
