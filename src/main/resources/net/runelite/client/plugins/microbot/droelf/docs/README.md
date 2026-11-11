# Dro Elf

Created by **DRO (droplugins)**.

Dro Elf pickpockets elves in Prifddinas. On start it locks onto the nearest elf and the nearest bank, then walks between them with its own built-in Prifddinas route planner. It eats, opens coin pouches, keeps a Dodgy necklace on, and restocks food and necklaces at the bank.

## Requirements

- Microbot client version 2.6.55 or later.
- Access to Prifddinas and 85+ Thieving.
- Food and Dodgy necklaces in the bank (both optional; amounts are set in the config).
- Optional: Shadow Veil (Arceuus spellbook) with runes or a lava battlestaff.

## Usage

1. Stand next to the elf you want to thieve in Prifddinas.
2. Set food, food amount, eat-below HP % and Dodgy necklace amount under **Consumables**.
3. Optional: turn on **Shadow Veil**, adjust **Adaptive behavior**, and set up **Break manager**.
4. Enable **[Dro] Elf**.

## Walking

The route planner uses an embedded Prifddinas collision map and checks the live scene before each step, opening doors on the way. Run is turned back on automatically when the game turns it off and energy has come back. It never clicks the run orb when run is already on, and it waits about two game ticks before clicking, so it does not undo a run toggle from the Microbot client's own auto-run.
