## Important:
Use at your own risk.

Babysit until you are 100% comfortable.Run the latest nightly build.

## Why use the plugin?

- +17.5 % combat experience boost means fast training
- Infinite supplies → no banking required
- Gear rewards = strong GP/hour
- Passive fishing & cooking XP

# Prerequisites For Using The Plugin
- The script eats and drinks by itself during the whole boss fight (see Health % topup and Prayer % topup in General settings)
- Optional backup: turn on the Quality Of Life (QoL) plugin at a lower threshold than the script, so it only steps in if the script misses a top-up:
  - 'Auto Eat Food' set to ~ 30-40%.
  - 'Auto Drink Prayer Pot' set to ~ 5-10
- InventorySetup preset per boss + for eclipse moon clone phase (4 in total)
  - Follow the wiki gear guide: https://oldschool.runescape.wiki/w/Moons_of_Peril/Strategies
  - Degradeable armor (e.g. barrows, moons) needs to be fuzzy selected within your InventorySetups plugin
  - When first setting up Inventory setups, create a blank setup and then ensure you swap to blank then back to selected Moons Boss
- Base 70+ combat stats (Att/Str/Def/HP)
- Stable connection & low ping world
- Perilous Moons quest completed
- 50+ Fishing & CookingOne 

## Script Sequence
1. Start inside Neypotzli
2. Auto-stocks food and potions
3. Boss order: Eclipse → Blue → Blood (optimal GP/hr)
4. Claim rewards chest, then repeat

## Boss Notes
- During the main attack phase the script eats when your HP drops below Health % topup and drinks when prayer drops below Prayer % topup (both in General settings). Lower these values, for example to 50%, for more DPS, or raise them if you die.
- **Eclipse Moon:** use your best **stab** weapon.
  - Clone phase = burst DPS with your highest-hit 2-handed weapon. 
  - If you spawn into the boss arena mid-shield special attack, the script exits the arena and re-enters.
- **Blue Moon:** use your best **crush** weapon. 
  - The plugin ignores the tornadoes special attack; ~100 HP heal is negligible compared to hitpoint impact of tornadoes
- **Blood Moon:** use your best slash weapon
  - high defense & tank gear helps to block the boss healing, which improves boss completion times.
  - maximise your normal attack sequence DPS for best results

## FAQ
How do I get the plugin?
- The plugin is on the main Microbot release.

The script sits on “Idle”. What now?
- Verify you selected an InventorySetup after any edits (switch to another preset, then back).
- Degraded gear users (eg Barrows, Moons): enable Fuzzy matching so degraded armour is recognised.
- If it still idles, create a brand-new inventory setup and re-select it.

## How do I report a bug?
- Enable Debug Logs in the plugin configuration, record a clip or video, and post it in the discord Bug Reports channel, following the Microbot template for Bug Reporting. 