# Tempoross

Plays the Tempoross minigame on mass worlds using the points-focused "cook everything" strategy —
the goal is **reward permits per game**, not fishing XP. Typical rounds land in the 4,000–5,000
point range.

## What it does

- Boards the boat, stocks up (buckets, rope, hammer as configured), and fishes the opening
  7 fish (9 at 85+ Fishing)
- Cooks everything it catches at the shrine, loads cooked fish into the ammunition crate
- Tethers to the mast or totem for every colossal wave, repairs both when damaged
- Douses fires on its routes, dodges fire clouds, and sidesteps fires when out of water
- Stops catching at ~49% energy so the bag is cooked and loaded before the last wave
- Stages at the spirit pool around 5% energy and harpoons it back to 97–98%
- Optionally hops to a configured world on startup and uses the harpoon special attack

All decision thresholds are randomized slightly each game.

## Setup

1. **Start the plugin outside the minigame area** (Ruins of Unkah dock).
2. Bring the harpoon selected in the config (or none for bare-handed with the barbarian training).
3. Leave at least the configured number of inventory slots free — fish fill everything that
   buckets, rope and hammer do not use.

## Configuration

| Option | Default | Notes |
|---|---|---|
| Buckets | 6 | Water for dousing. In mass mode 2–3 is usually enough and frees fish slots |
| Hammer | on | Repairs mast/totem for points |
| Rope | on | Needed to tether; auto-refetched if lost. Not needed with Spirit Angler's |
| Solo | off | Solo instance; requires Infernal Harpoon and 19+ free slots |
| World | 422 | Hops there on script start when outside the minigame. 0 disables |
| Spirit Angler's | off | Enable when wearing the full outfit (no rope needed) |
| Harpoon | Infernal | Dragon/Infernal/Crystal enable the special attack option |

## Known limitations

- Tuned for **mass worlds**; solo mode is functional but far less exercised
- The forfeit-at-high-intensity behaviour is intentional: a round that reaches ~92%+ storm
  intensity during the final cook is already lost, and requeuing is faster than being washed out
- Requires a current Microbot client (see `minClientVersion`) — the plugin relies on newer
  cache/query APIs
