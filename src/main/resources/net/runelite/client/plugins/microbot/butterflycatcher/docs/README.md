# Butterfly Catcher
**Author:** StonksCode | **Version:** 1.0.0

Automates butterfly and moth catching for Hunter XP. Runs indefinitely with no banking required.

---

## Supported Species

| Species | Net Level | Barehanded Level | Location |
|---|---|---|---|
| Ruby Harvest | 5 | 15 | Puro-Puro / various |
| Sapphire Glacialis | 25 | 35 | Asgarnian Ice Dungeon area |
| Snowy Knight | 35 | 45 | Asgarnian Ice Dungeon area |
| Black Warlock | 45 | 55 | Feldip Hunter area |
| Sunlight Moth | 65 | 75 | Avium Savannah (Varlamore) |
| Moonlight Moth | 75 | 85 | Hunter Guild / Tonali Cavern (Varlamore) |

---

## Setup

1. Travel to a spawn location for your chosen species.
2. If using **Butterfly Net** mode, equip your net before starting.
3. Open the plugin config, select your species and catch mode.
4. Enable the plugin — it will run until you stop it.

---

## Catch Modes

**Barehanded** — Catch and instantly release. Nothing enters your inventory. Requires Hunter level 10 higher than the net threshold.

**Butterfly Net** — Requires a Butterfly Net (id 10010) or Magic Butterfly Net (id 11259) to be equipped before starting. Allows catching at 10 levels lower than barehanded.

---

## Notes

- The plugin checks your Hunter level on startup and stops with a message if you don't meet the requirement.
- In Butterfly Net mode, the plugin also verifies your net is equipped before starting.
- No banking — the script runs indefinitely at your chosen spawn.
- Moonlight Moth supports all three location NPC variants (Hunter Guild, Neypotzli, Tonali Cavern).
