# JOJ Blackjack

Enable both **JOJ Blackjack** and **JOJ Blackjack Entry Swap**. Entry Swap chooses Knock-Out while the target is standing and Pickpocket while it is unconscious; automation uses verified left clicks rather than modifier bindings or right-click menus.

## Targets And Starting Location

| Target | Required Thieving | Preparation |
| --- | --- | --- |
| Bandit, combat level 41 | 45 | Manually isolate it in the northern house near 3358,2993, plane 0 |
| Bandit, combat level 56 | 55 | Manually isolate it in the same marked northern house |
| Menaphite Thug | 65 | Southern tent; automation lures a thug in and removes extras |

AUTO chooses from the loaded starting Thieving level: level-41 Bandit below 55, level-56 Bandit below 70, Thug from 70. It retains that choice for the session. Re-enable to choose again after levelling; explicit targets remain selectable at higher player levels.

Bandit automation does not lure NPCs. Isolate exactly the selected Bandit before starting. Its supported house covers x3357-3360, y2991-2995 on plane 0. The east curtain is secured before acquisition and when leaving/returning for wine. The southern Thug tent and its curtains are separate geometry.

## Supplies

Equip a blackjack and start with a full inventory to prevent coin-pouch slots. Carry coins, noted wine, unnoted wine and optionally an escape teleport replacing one wine. Wine is the supported healing supply; brews are not implemented. Five coins are needed per unnoted wine.

Healing takes priority. Empty jugs are retained inside and cleared only after safely leaving. The optional disarmed combat reset can temporarily drop one full wine, unequip, attempt Knock-Out, re-equip and recover that wine. The safespot fallback remains available.

## Breaks And Camera

Micro breaks default to every 9-11 minutes for 25-35 seconds; small breaks to every 20-30 minutes for 60-120 seconds. Configure minimum/maximum intervals in minutes and durations in seconds. Interval edits apply at the next scheduling event and duration edits at the next break start. Due breaks wait for the existing safe boundary, not a per-loop chance.

Preferred zoom is maintained only during blackjacking, with bounded temporary visibility recovery. Travel and luring can use different zoom. Zero in Return camera zoom captures the starting zoom rather than selecting a universal constant.

## Scope And Contributions

Menaphite Thugs are the primary workflow, developed and used through 99 Thieving: automatic luring, southern-tent setup, removal of extra thugs, healing, combat recovery and wine unnoting/return.

Bandits are supported but not feature-complete. Manually lure and isolate the selected NPC in the northern house; enable Highlight Bandit house to see its floor tiles. Healing, combat recovery and wine unnoting/return remain supported. Automatic Bandit luring and extra-NPC removal are not provided.

Both plugin toggles must be enabled. Start in Pollnivneach with a blackjack equipped, a full inventory, noted/unnoted wines and enough coins to restock. Contributions, bug reports and fixes are welcome, especially improvements to the Bandit workflow.

Scheduled breaks use the client's cooperative BreakPreparation API to secure the house and reach safety before the handler pauses or logs out. The client providing that API is required.
