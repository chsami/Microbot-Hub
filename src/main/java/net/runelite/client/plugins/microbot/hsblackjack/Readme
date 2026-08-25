# HSBlackJack

Automates blackjacking Menaphite Thugs in Pollnivneach: finds and lures a thug into a private tent,
manages the curtain, knocks them out, and pickpockets twice before repeating.

## Features

- Full lure → tent → knock-out → pickpocket cycle, using the NPC's own animation IDs to confirm a
  successful knock-out as fast as possible instead of guessing timing.
- Multi-tick lure verification (interaction + proximity, checked over several ticks) to correctly
  detect a rejected "Go away! I'm busy" instead of a false positive.
- Excludes a few manually-confirmed problem zones from the search area, where a lured NPC's
  follow-AI reliably gets stuck at a wall/corner on the way to the tent.
- Waits for a lured NPC to catch up if it falls behind while following, instead of repeatedly
  dashing ahead and stranding it.
- Detects when the tent is already occupied (by another NPC or another player) and hops worlds
  automatically rather than getting stuck.
- Recovers from real combat (the "You can't do this during combat." block) by unequipping the
  blackjack, breaking the NPC out of combat, then re-equipping and resuming.
- Automatic Jug of wine restocking from Faisal the Barman. Since Faisal only ever restocks up to
  13 at a time, the plugin buys his full stock, then hops worlds for a second full restock (up to
  26 total) before heading back, roughly halving how often a restock trip is needed. Also includes
  a minimum-stock check that hops worlds if the shop itself is temporarily low.
- Opens coin pouches automatically once 27+ are held.
- Smart, transport-aware travel to the starting position from anywhere in Gielinor at plugin start
  (fairy rings, Shantay Pass, etc.), with a stuck-detection fallback that tries opening nearby
  doors if progress stalls.
- Chains phase transitions within the same game tick whenever no real waiting is actually needed
  (e.g. after a pickpocket burst, straight back into the next knock-out attempt), instead of always
  waiting for the next scheduler tick - meaningfully improves effective XP/hr without changing any
  of the actual timing/verification that keeps the cycle reliable.
- Overlay panel with live status, a short rolling action history, knock-out/pickpocket counts,
  runtime, and XP/hr.

## Configuration

- **Blackjack** - which tier/type of blackjack to use.

## Requirements

- A blackjack (configurable type) equipped or in your inventory.
- Coins or Jug of wine in your inventory when starting (the plugin will refuse to start and show a
  warning popup otherwise).
- Access to Pollnivneach (completed The Feud, or membership as required by the area).

## Known limitations

- Hardcoded to the west tent in the Pollnivneach blackjacking area (fixed coordinates) - not
  currently configurable to other tents/locations.
- Targets Menaphite Thugs specifically.

## Testing

Manually tested over multiple live sessions, including combat-recovery, restock trips, world
hops, exclusion-zone behaviour, and multi-hour runs.