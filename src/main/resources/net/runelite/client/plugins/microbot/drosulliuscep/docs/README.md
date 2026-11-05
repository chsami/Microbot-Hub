# Dro Sulliuscep

Created by **DRO (droplugins)**.

Dro Sulliuscep cuts the six Sulliusceps on Fossil Island. It travels to Sticky Swamp with a Digsite pendant and the Magic Mushtree, clears the vines between trees, and banks at Ferox Enclave or through your house. It can also loop with the Rubber cap mushroom instead of banking.

## Installing from the Hub

Install Dro Sulliuscep through the Microbot Hub; the Hub handles its plugin JAR and catalog assets. For manual source imports into a compatible client, the `drosulliuscep` Java folder includes its descriptor constants, `LICENSE.txt` and `CREDITS.txt`, and needs no separate runtime resources.

## Requirements

- Microbot client version 2.6.32 or later.
- 65+ Woodcutting.
- Fossil Island access (Bone Voyage).
- A rake (required for the vines before tree #4).
- A usable woodcutting axe.
- A charged Digsite pendant, equipped or in the inventory.
- A charged Ring of dueling for the Ferox and Castle Wars travel modes.
- POH travel modes: an ornate rejuvenation pool, a mounted Digsite pendant, and a house tablet or Construction cape. The Crafting Guild mode banks with a Crafting cape.

## Usage

1. Choose a **Travel mode**: Ferox / Ring of dueling, POH / Castle Wars / Ring of dueling, or POH / Crafting Guild / Crafting cape.
2. Choose what **Drop when full** removes: both mushrooms and Mort myre fungus (optionally with small or all fossils), mushrooms only, or Mort myre fungus only.
3. Optional settings: food and antipoison are off by default; the axe special attack is on by default; smart breaks are off by default.
4. Optional: turn on **Jump on Rubber cap mushroom** to start each lap from the Rubber cap instead of banking. The script fills the mud pit with 9 mushrooms it cuts on the way, and still banks when the pit is not filled, the inventory is too full, enabled food runs out, or HP reaches the emergency level.
5. Enable **[Dro] Sulliuscep Run** and start the script.

## What happens on start

- **Auto retaliate is turned off**, so the swamp creatures do not pull you off the route.
- At the first bank visit, **everything in your inventory except the rake is deposited**. Equipped items stay on. The script then takes out the travel items for your mode and any food or antipoison you enabled.

## Drop when full

Numulites, caps, supplies and gear are always kept. **Both**, **Mushrooms only** and **Mort myre fungus only** never drop fossils. **Both + small fossils** also drops unidentified small fossils, and **Both + ALL fossils** drops every unidentified fossil.

## When it stops

If something it needs is missing, the script stops and shows a short reason such as `Stopped: no rake`. The Microbot log says what to fix, for example "Put a rake in your bank, then restart".

## Route

The script follows the game's active-tree marker and cuts the trees in order. The eight vine obstacles are cleared on the way:

| Leg | Obstacles |
| --- | --- |
| Sticky Swamp → #1 | none |
| #1 → #2 | Thick vine (chop) |
| #2 → #3 | 3 × Thick vines (chop) |
| #3 → #4 | Vines (rake) |
| #4 → #5 | 2 × Thick vines, 1 × Thick vine (chop) |
| #5 → #6 | none |

After tree #6 the script travels to the selected bank, restores HP only when it is missing, banks the run's loot, and travels back to Sticky Swamp.

## Walking

Pool and bank approaches use Microbot's walker. Inside Sticky Swamp the script walks a fixed route between the trees.

## Credits and license

Dro Sulliuscep is © 2026 DRO (droplugins) and is free and open source under the BSD-2-Clause license. Any copy, fork or excerpt must keep the copyright notice, the author credit and the license text. See `LICENSE.txt` and `CREDITS.txt` in the source folder.
