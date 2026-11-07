# Scurrius

Banks, walks to Scurrius in the Rat Pits (private or public entrance), fights him, loots, and restocks.

## Features

- Eats and drinks prayer potions at configurable percentages; loots by name list and value.
- **Prayer mode** (config, default Lazy flick):
  - *Always on*: the protection prayer for Scurrius's current attack style stays on.
  - *Lazy flick*: the prayer is on only for the tick before a hit lands and the tick it lands.
  - *One-tick flick*: the prayer is switched off and on every tick so it never drains.
  - Flicking learns the animation-to-hit delay of each attack style from the hits it takes (two equal observations per
    style) and acts on game ticks. Until all three styles are learned, after any unblocked hit, and while giant rats
    attack you, it holds the prayer on (always-on behaviour).
- **Falling rocks**: tracks the rock tiles itself, steps to the nearest safe tile (preferring tiles where Scurrius can
  still be attacked) and does not click Attack until the rocks are gone from your tile.
- **Emergency teleport**: with food left at or under the threshold (default 2) and hitpoints under the percent of your
  real hitpoints level (default 50), breaks a Varrock teleport tab, banks, walks back and fights again. With no tab
  it keeps eating what is left. The old rule (no food and under 25 hitpoints) still applies.

## Requirements

Food, prayer potions and Varrock teleport tabs in the bank (amounts are configurable).
