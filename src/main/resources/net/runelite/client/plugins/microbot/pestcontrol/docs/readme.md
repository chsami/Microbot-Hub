# Pest Control Script – Microbot RuneLite Client

![img.png](assets/img.png)

The **Pest Control Script** automates the Old School RuneScape Pest Control mini-game using the Microbot RuneLite client.  
It handles portal and NPC combat with ordered melee, ranged, and magic loadouts.

---

## Features

## Feature Overview

| Feature                   | Description                                                                 |
|---------------------------|-----------------------------------------------------------------------------|
| **Auto World Hop**        | Hops to your configured Pest Control world before starting.                 |
| **Travel to Island**      | Walks to the Pest Control island if not already there.                      |
| **Boat Selection**        | Uses novice below 70, intermediate at 70-99, and veteran at 100+.           |
| **Quick Prayer**          | Automatically enables Quick Prayer at game start (optional).                |
| **Special Attack**        | Uses special attacks only against configured portals when enough energy is available. |
| **Portal Zerg Targeting** | Joins the largest group at a live portal, stays through small crowd changes, and uses purple as a tie-break. |
| **Tribrid Loadouts**      | Supports three ordered styles, Void helmet switches, and verified weapon/off-hand changes. |
| **Melee Variants**        | Supports independent stab, slash, and crush weapon/off-hand combinations.    |
| **Magic Preparation**     | Supports powered staves or a startup-only remembered autocast check.         |
| **Combat Idle Handling**  | Attacks nearby NPCs when idle.                                               |
| **Brawler Blocking Fix**  | Attacks brawlers if they block movement.                                     |
| **Boat Alching**          | High-alchs a chosen item while waiting in the boat (optional).               |
| **Error Handling**        | Catches exceptions and prevents script crashes.                              |
| **Fast Loop**             | Runs every 300 ms for near real-time responses.                              |
| **Priority Requeue**      | Clicks the correct gangplank before post-round weapon restoration or cleanup. |


---

## Requirements
- Microbot RuneLite client
- Every enabled style's weapon, optional off-hand, and matching Void helmet
- One free inventory slot when changing from an equipped off-hand to an empty-off-hand/two-handed loadout
- Pest Control world access

---

## Configuration Options
- **World**: Target world to play on.
- **Quick Prayer**: Enable/disable Quick Prayer usage.
- **Alching in Boat**: Enable/disable high-alching between matches.
- **Alch Item**: Name of item to alch.
- **Style 1**: Mandatory main style and fallback loadout.
- **Style 2 / Style 3**: Optional additional unique combat styles.
- **Ranged**: Exact weapon and optional off-hand; Rapid is selected and verified.
- **Magic**: Exact weapon/off-hand plus powered-staff or autocast mode. Autocast is checked and set once at plugin startup, then left to the game's per-weapon memory.
- **Melee**: Independent stab, slash, and crush weapon/off-hand loadouts, plus configurable default, Yellow, and Red variants.
- **Void helmets**: Automatically maps Melee, Ranged, and Magic to their matching Void helmets.
- **Opening selection**: Style 1, equal random, or normalized Purple/Blue/Yellow weights. Red is never selected first.
- **Special attacks**: Enabled separately per portal and never used against ordinary pests or Brawlers.

`None` (or a blank off-hand) means that the loadout expects the shield slot to be empty. Missing style-specific weapons fall back to Style 1; missing equipped items or incompatible 2h/off-hand combinations are reported without repeated click spam. No equipment outside the weapon, shield/off-hand, and head slots is changed.

---

## How It Works
1. The script checks if you are logged in and on the right world.
2. If needed, it hops worlds and travels to the Pest Control island.
3. Validates the configured Style 1 loadout and performs any one-time autocast setup.
4. During games:
    - Stages at a melee or ranged/magic distance appropriate to the selected portal loadout.
    - Activates prayers and special attacks as configured.
    - Follows the largest live-portal group, kills nearby Spinners, then focuses the portal.
    - Verifies the Void helmet, weapon, off-hand, and combat option before attacking.
5. After games, it immediately reboards, then restores Style 1 once the boat is confirmed.

---

## Disclaimer
This script is intended for use within the **Microbot RuneLite Client** only.  
Use of automation software in Old School RuneScape is against Jagex’s rules and can result in penalties to your account.  
Use at your own risk.

