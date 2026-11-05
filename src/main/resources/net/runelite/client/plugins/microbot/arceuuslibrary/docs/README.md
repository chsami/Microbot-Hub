# Arceuus Library

Automates the Arceuus Library task by reading the upstream **Kourend Library** RuneLite plugin's solver state. The script handles the whole loop: find a customer, fetch their requested book from a bookcase, deliver it, repeat.

## How it works

The upstream Kourend Library plugin observes customer dialogue, bookcase searches, and reshuffle events to narrow down which bookcase contains which book. This plugin reads that solver state and drives the player through the loop.

- **Reads** — wanted book, customer NPC, possible bookcase locations, solver state (`NO_DATA` / `INCOMPLETE` / `COMPLETE`).
- **Drives** — traveling to the library, walking, talking, searching, delivering.
- **Does not** re-implement book inference. The upstream plugin is the brain.

## Prerequisites

- Members world. On a free-to-play world the script stops at once and asks you to hop to a members world.
- Start inside the Arceuus Library (region 6459) or anywhere else. Outside the library, the script travels there on its own with the web walker's ships, charters and teleports. If walking fails, it breaks an *Arceuus library teleport* or *Kourend castle teleport* tab from your inventory. When no walking route is found it waits 20 s, then 40 s, before trying again, and the overlay shows the countdown. After three failed tries it stops and asks you to go to Great Kourend first or bring one of those tabs.
- The script keeps run turned on whenever you have more than 10% run energy.
- The upstream **Kourend Library** plugin will be enabled automatically when you start this plugin.

## Config

| Option | Description |
|---|---|
| Reward skill | Which XP to claim from the *Book of arcane knowledge* reward. Magic = 15× Magic level, Runecraft = 5× Runecraft level. Auto picks whichever pays more; ties go to Magic. Default: Magic. |
| Read reward book | Read a *Book of arcane knowledge* as soon as you get one. When off, the books stay in your inventory. Default: on. |
| Read Soul Journey | If the requested book is *Soul Journey*, read it before delivery to start the *Bear Your Soul* miniquest. Default: on. |
| Use stamina/energy potions | Drink Extended stamina, Stamina, Super energy or Energy potions (any dose, best first) to keep running. While a stamina effect is active, an energy potion is drunk instead. Default: off. |
| Drink at run energy % | Drink a potion when run energy is at or below this value (1-99). Default: 30. |
| Potions to bring | When you run out of potions and energy is low, the script walks to the Arceuus bank, takes this many (0-10), and walks back to the library. If the bank has none, or none reach your inventory (for example a full inventory), it stops trying, says so in chat and carries on without them. 0 turns restocking off. Default: 2. |

## Out of scope

- Dark Manuscripts (removed January 2024).
- Library Historical Archive interactions.
- Bear Your Soul miniquest steps beyond the optional one-shot Soul Journey read.
