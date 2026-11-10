# Dro Pyramid Plunder

Runs the Pyramid Plunder minigame in Sophanem: finds the Guardian mummy's door, passes each room's spear trap, loots urns, the Grand Gold Chest and (optionally) sarcophagi, and picks the tomb doors through to the last room your Thieving level allows.

## Requirements

- 21+ Thieving. Start in Sophanem.
- The quest **Contact!** for the Sophanem bank. Without it the script keeps going on the food it carries and stops when food runs out.
- Recommended: a Prayer book and a holy or unholy symbol to cure the urn snakes' poison, and 43 Prayer for Protect from Melee against scarab swarms and mummies.

## How it plays

- **Better XP mode** (default): at 81+ Thieving, urns only in rooms 7 and 8, the Grand Gold Chest in every room, no sarcophagi. Room 7 gets about 1 min 30 for urns before moving to room 8.
- **Normal plan**: urns in the last two rooms your level allows, the chest in every room, sarcophagi from room 4 when enabled. The last room keeps enough time to be emptied, learned from your own urn pace.
- The mummy's door changes every 25 minutes. The script tries the door that last worked, then the door other players are queued at, then the nearest untried door. World 493 (the Pyramid Plunder world) is used by default.
- Between runs it eats, cures poison and, when food is low, prays at the temple altar and banks in the Sophanem dungeon. An optional Inventory Setup is loaded at the bank.
- Breaks never interrupt the five-minute game: the break handler is locked while inside the pyramid.

## Overlay

A compact card with the run status, runtime, runs, Thieving XP and XP/hr, the current room, time and urns left, and the last known mummy door. Click **-** / **+** to collapse or expand it.
