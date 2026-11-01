# Drift Net Plugin

Automation for the Drift Net fishing and Hunter minigame in the underwater cavern of Fossil Island, delivering Hunter and Fishing experience together. Originally written by Mocrosoft; extended by droplugins.

## Features

- **Autonomous drift net operation:** sets nets, monitors fill levels via game varbits, loots full nets and resets empty ones immediately.
- **Directional shoal targeting:** weighs shoal distance, angle and line of sight to herd shoals toward the active nets with minimal movement.
- **Annette resupply:** withdraws more drift nets from Annette when the inventory runs out, including Store-All for noted nets.
- **Rs2Walker travel:** all area and surface travel uses `Rs2Walker`.
- **Gear and stance management:** unequips conflicting weapon slots and manages underwater swim speed via sprint toggling.
- **Collapsible overlay:** runtime, Fishing and Hunter XP/hr, next break countdown and status.
- **Humanized behaviour:** configurable mouse activity, off-screen idle parking and AFK/logout break intervals.

## Requirements

- Fossil Island access plus a fishbowl helmet and diving apparatus.
- Drift nets stored with Annette (or noted nets to deposit).
- A merfolk trident or an empty weapon slot to prod shoals.

## Usage

1. Equip the diving apparatus and fishbowl helmet.
2. Have drift nets with Annette inside the underwater cavern.
3. Adjust the plugin configuration (mouse activity, breaks, shoal chasing distance).
4. Start the plugin near the underwater entrance or inside the net area.

## Source files

- `DriftNetPlugin.java` – plugin lifecycle and event wiring.
- `DriftNetScript.java` – core automation loop.
- `DriftNetConfig.java` – configuration.
- `DriftNetOverlay.java` – overlay.
- `DriftNetStatus.java`, `DriftNet.java`, `DriftNetTargeting.java` – net state and shoal targeting.
- `DriftNetTravel.java`, `DriftNetInventorySetup.java` – travel and equipment handling.
- `DriftNetProfile.java` – mouse, break and timing behaviour.
