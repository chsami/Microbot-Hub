# Charter Crafter

## Features

- Buys seaweed and buckets of sand, casts Superglass make, crafts glass items, sell them to the shop, and repeats.
- Hops worlds as needed.

## Configuration

- `Product`: Choose which glass item to craft.
- `Options`:
  - Beer glass
  - Candle lantern
  - Oil lamp
  - Vial
  - Fishbowl
  - Unpowered staff orb
  - Lantern lens
  - Light orb (still crafted; finished orbs are dropped later as they can’t be sold)

## Requirements

- `Glassblowing pipe` in inventory
- `Coins` ≥ 1,000
- `Astral rune` ≥ 2
- Either `Air rune` ≥ 10 or wear `Staff of air` / `Air battlestaff`
- Either `Fire rune` ≥ 6 or wear `Staff of fire` / `Fire battlestaff`
- Be near a `Trader Crewmember` (no walking logic is included)

## How It Works

1. Bootstrap checks setup items/runes/staves.
2. If `Molten glass` is in inventory → goes directly to glassblowing.
3. Drops any Light orbs (cannot be sold to shop)
4. If any sellable glass items are in inventory → opens shop and sells them.
5. Opens shop to buy seaweed/sand to reach a stack of 10 each (hops if needed).
6. Casts Superglass Make → crafts selected product.
7. Opens shop again to sell products, then loops.

## Notes & Limitations
- The plugin does not walk to Charter locations; start next to a `Trader Crewmember`.

## Troubleshooting
- “Cannot cast Superglass Make”: Verify runes/staves and that you’re on the Lunar spellbook.
- “Missing setup requirements”: The overlay/status lists what’s missing (coins, runes, staves, pipe).
