# Dro Sulliuscep

Created by **Dorian (DRO / droplugins)**.

Dro Sulliuscep runs the Fossil Island Sulliuscep course: Digsite pendant and Magic Mushtree travel to Sticky Swamp, the recorded six-tree route with its vines, and pool and bank recovery at Ferox Enclave.

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
4. Enable **[Dro] Sulliuscep** and start the script.

## Route

The active tree is read from varbit 5808. Trees are targeted by name and **Cut** action near each recorded anchor, with recorded object IDs used only as a fallback. The eight vine obstacles are cleared in route order:

| Leg | Obstacles |
| --- | --- |
| Sticky Swamp → #1 | none |
| #1 → #2 | Thick vine (chop) |
| #2 → #3 | 3 × Thick vines (chop) |
| #3 → #4 | Vines (rake) |
| #4 → #5 | 2 × Thick vines, 1 × Thick vine (chop) |
| #5 → #6 | none |

After tree #6 the script travels to the selected bank, restores HP only when it is missing, banks the run's loot, and travels back to Sticky Swamp.

## Walking in the Hub build

The Hub build walks to the pool and bank with Microbot's walker, on a separate worker thread so the script keeps checking safety while it walks. Once the character reaches Sticky Swamp, the walker stops and the recorded course handles all movement.

## Credits and license

Dro Sulliuscep is © 2026 Dorian (DRO / droplugins) and is free and open source under the BSD-2-Clause license. Any copy, fork or excerpt must keep the copyright notice, the author credit and the license text. See `LICENSE.txt` and `CREDITS.txt` in the source folder.
