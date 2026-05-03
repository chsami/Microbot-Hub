# Sisyphus: Infernal Pact

Automates the **Yama's Lair stepping-stone circuit** in the Demonic Pacts League.

## What it does

- Navigates the full stepping-stone route through Yama's Lair
- **Never clicks a tile with fire on it** (checks NPC ID 15608 with a 2-tile exclusion radius)
- Loops the central stone circuit continuously until the configured stone count is reached
- Uses rapid-fire 200–400 ms click intervals for maximum efficiency
- Prevents backtracking by tracking the last 3 visited stones

## Setup

1. Log into your account and travel to Yama's Lair
2. Stand near the first entry stone at `(1486, 5598)`
3. Enable the plugin in the Microbot plugin list
4. Set **Stones to Step** to however many you need (default 666 for the Demonic Pacts task)
5. Click **Start** — the bot handles the rest

## Settings

| Setting | Default | Description |
|---|---|---|
| Stones to Step | 666 | Total safe stone hops before the plugin stops |

## Notes

- The plugin stops automatically once the configured stone count is reached
- Fire NPCs move — the bot re-checks every tick so it will never step on an active fire tile
- Works on a tight 200–400 ms scheduler (effectively 1-tick clicking)

## Author

Sisyphus
