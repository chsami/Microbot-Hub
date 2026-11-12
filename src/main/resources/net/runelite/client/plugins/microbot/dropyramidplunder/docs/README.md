# Dro Pyramid Plunder

Runs the Pyramid Plunder minigame in Sophanem: finds the Guardian mummy's door, passes each room's spear trap, loots urns, the Grand Gold Chest and (optionally) sarcophagi, and picks the tomb doors through to the last room your Thieving level allows.

## Requirements

- 21+ Thieving. Start in Sophanem.
- The quest **Contact!** for the Sophanem bank. Without it the script keeps going on the food it carries and stops when food runs out.
- Recommended: a Prayer book and a holy or unholy symbol to cure the urn snakes' poison, and 43 Prayer for Protect from Melee against scarab swarms and mummies.

## How it plays

- **Better XP mode** (default): urns only in the last two rooms your Thieving level reaches (rooms 7 and 8 at 91, rooms 5 and 6 at 71), the Grand Gold Chest in every room, no sarcophagi. The room before the last is left with about 1 min 15 to 1 min 20 on the timer, so the last room gets the rest.
- **Normal plan**: urns in the last two rooms your level allows, the chest in every room, sarcophagi from room 4 when enabled. The last room keeps enough time to be emptied, learned from your own urn pace.
- Each urn counts as looted the moment its Thieving experience drops, and the next urn is clicked straight away.
- Sarcophagi are off by default (Strength experience only, about ten seconds each).
- The mummy's door changes every 25 minutes. The script tries the door that last worked, then the door other players are queued at, then the nearest untried door. World 493 (the Pyramid Plunder world) is used by default.
- Between runs it eats, cures poison and, when food is low, prays at the temple altar and banks in the Sophanem dungeon. An optional Inventory Setup is loaded at the bank.
- Breaks never interrupt the five-minute game: the break handler is locked while inside the pyramid.

## Overlay

A compact card with the run status, runtime, runs, Thieving XP and XP/hr, the current room, the time left (a live countdown), the urns left in the two urn rooms, and the last known mummy door. Click **-** / **+** to collapse or expand it.
