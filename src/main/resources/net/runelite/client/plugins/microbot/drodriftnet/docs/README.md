# Dro Drift Net

Dro Drift Net is a standalone automation plugin for the Drift Net fishing and Hunter minigame located in the underwater cavern of Fossil Island. It delivers high-efficiency dual Hunter and Fishing experience with humanized antiban routines, automated net servicing, Annette resupply, and shoal chasing.

## Features

- **Autonomous Drift Net Operation:** Sets nets, monitors fill levels via game Varbits, loots full nets, and resets empty nets immediately.
- **Directional Shoal Targeting:** Evaluates fish shoal distance, angle, and line of sight to chase and herd shoals toward the active nets with minimal excess movement.
- **Annette Resupply:** Automatically withdraws additional drift nets from Annette when inventory is depleted.
- **Rs2Walker Integration:** All area and surface travel is handled natively by `Rs2Walker` with zero external bridge dependencies.
- **Gear & Stance Management:** Handles un-equipping conflicting weapon slots and manages underwater swim speed via sprint toggling.
- **Collapsible HUD Overlay:** Real-time stats panel displaying runtime, Fishing XP/hr, Hunter XP/hr, next break countdown, and operational status with a clickable toggle button.
- **Humanized Antiban:** Configurable mouse activity, off-screen idle parking, micro-delays, and customizable AFK/logout break intervals.

## Requirements

- Fossil Island quest access and diving gear (Fishbowl helmet + Diving apparatus).
- Drift nets deposited with Annette (or noted nets to deposit).
- Merfolk trident or unequipped weapon slot to prod fish shoals.
- Microbot client version 2.6.32 or newer.

## Usage

1. Equip diving apparatus and fishbowl helmet.
2. Deposit drift nets with Annette inside the underwater cavern.
3. Configure desired mouse profile, break intervals, and shoal chasing distance in **[Dro] Drift Net** config.
4. Start the plugin near the underwater entrance or inside the net area.
