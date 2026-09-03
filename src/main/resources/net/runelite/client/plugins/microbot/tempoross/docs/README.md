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
- Auto-equips the best Tempoross gear from the bank (Spirit Angler > Angler per slot, best
  wieldable harpoon, Imcando off-hand hammer), and re-checks after every reward collection so
  fresh outfit drops go straight on. A worn full Spirit Angler set skips ropes automatically

All decision thresholds are randomized slightly each game.

## Setup

1. **Start the plugin outside the minigame area** (Ruins of Unkah dock).
2. Keep your best harpoon in the bank (or anywhere) — auto-equip finds it. Infernal ranks first: at Tempoross it cooks harpoonfish in place without destroying them.
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
| Fish bare-handed | off | Needs Barbarian Fishing training. Otherwise the best owned harpoon is auto-supplied (infernal first for max permits) |

## Known limitations

- Tuned for **mass worlds**; solo mode is functional but far less exercised
- The forfeit-at-high-intensity behaviour is intentional: a round that reaches ~92%+ storm
  intensity during the final cook is already lost, and requeuing is faster than being washed out
- Requires a current Microbot client (see `minClientVersion`) — the plugin relies on newer
  cache/query APIs
