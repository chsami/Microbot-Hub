# Arceuus Library

Automates the Arceuus Library task by reading the upstream **Kourend Library** RuneLite plugin's solver state. The script handles the whole loop: find a customer, fetch their requested book from a bookcase, deliver it, repeat.

## How it works

The upstream Kourend Library plugin observes customer dialogue, bookcase searches, and reshuffle events to narrow down which bookcase contains which book. This plugin reads that solver state and drives the player through the loop.

- **Reads** — wanted book, customer NPC, possible bookcase locations, solver state (`NO_DATA` / `INCOMPLETE` / `COMPLETE`).
- **Drives** — walking, talking, searching, delivering.
- **Does not** re-implement book inference. The upstream plugin is the brain.

## Prerequisites

- Members world.
- Inside the Arceuus Library (region 6459). The script will not auto-travel there — fairy ring CIS or another teleport, then start.
- The upstream **Kourend Library** plugin will be enabled automatically when you start this plugin.

## Config

| Option | Description |
|---|---|
| Reward XP | Which XP to claim from the *Book of arcane knowledge* reward. `MAGIC` = 15× Magic level, `RUNECRAFT` = 5× Runecraft level. Default: `MAGIC`. |
| Read Soul Journey | If the requested book is *Soul Journey*, read it before delivery to start the *Bear Your Soul* miniquest. Default: on. |

## Out of scope

- Auto-travel to the library.
- Dark Manuscripts (removed January 2024).
- Library Historical Archive interactions.
- Bear Your Soul miniquest steps beyond the optional one-shot Soul Journey read.
