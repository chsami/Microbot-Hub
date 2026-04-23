# AIO AIO — Record & Replay

Record a sequence of in-game menu actions, then play it back on demand or on loop. 80/20 automation for one-off tasks.

## What it does

- Captures `MenuOptionClicked` events (NPC/object/ground-item interactions, inventory actions, widget clicks)
- Saves recordings as JSON under `~/.runelite/microbot-recordings/`
- Replays them with proper tick pacing; for inventory item actions it delegates to `Rs2Inventory.interact()` so stale slot positions don't cause misses
- For NPCs/objects/ground items it re-resolves the target by id/name each run (via the queryable cache) — your recording keeps working even if the target moves or respawns

## Usage

1. Enable the plugin, open the AIO AIO side panel
2. Click **Record script**, perform the actions in-game, click **Stop** when done
3. The recording shows in the **Script:** dropdown (sorted by last recorded/played time)
4. Select it, hit **Run script** — loops until you hit **Stop script**

## Editing a recording

- Double-click or ✎ to edit a step's pre-delay (in ticks)
- ↑ / ↓ to reorder, ⧉ to duplicate, ✕ to delete
- ✎ Rename / 🗑 Delete on the whole script

## Limits

- Actions whose target can't be found at replay time are skipped (see logs)
- The playback loop enables `naturalMouse` while running and restores it on exit
