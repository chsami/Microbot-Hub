# Dro KBD

Dro KBD automates King Black Dragon trips, including travel through the Lava Maze, combat, loot collection, wilderness safety checks, and returning to Ferox Enclave to restore and bank.

## Installing from the Hub

Install Dro KBD through the Microbot Hub; the Hub handles its plugin JAR and catalog assets. For manual source imports into a compatible client, the `drokbd` Java folder includes its descriptor constants and needs no separate runtime resources. The destination still requires compatible Microbot and Inventory Setups APIs. Documentation and catalog artwork remain in Hub resources for publishing.

## Requirements

- Microbot client version 2.6.25 or later. Older clients, including 2.2.24, are not supported by the current path and cache APIs.
- A saved Microbot Inventory Setup selected in the plugin configuration. The script uses that setup for each trip.
- Burning amulets for travel to the Lava Maze and a ring of dueling for the return to Ferox Enclave. Other travel methods are not supported.
- For crossbow modes, save the desired bolts in the setup's equipped ammunition slot. Ruby bolt modes replenish the equipped stack to the quantity saved in the selected Inventory Setup. Toxic blowpipe mode requires extended super antifire.

## Usage

1. Review the selected Inventory Setup and configure the weapon and ammunition mode, food, prayer threshold, loot value, and player safety radius.
2. Make sure the setup includes the supplies and travel items for the chosen mode.
3. Enable **[Dro] KBD** and start the script when ready.
4. Keep in mind that the route passes through the Wilderness. Configure a loadout you are willing to risk and stop the script when you want to end the trip.

Melee mode does not use ammunition handling or the ranged five-tile spacing rule. The plugin is marked beta and is limited to Inventory Setup driven loadouts.

## Wilderness safety

Nearby attackable players trigger a logout attempt before combat. Once another player is targeting you and animating, the script intentionally accepts death: it disables auto-retaliate and pauses eating, movement, and escape attempts. Following or trading alone does not trigger surrender. If no further attacker animation is observed for 10 game ticks (about 6 seconds), normal threat checks and travel resume. Leaving the Wilderness, logging out, dying, or stopping also clears surrender. This does not guarantee survival; death recovery prepares another trip.

The plugin uses the combat antiban template while running and restores the previous global antiban settings and activity on stop. If global settings are configured to override script settings, the plugin respects that choice.

If a future client changes the fields needed to capture the global antiban profile, KBD keeps that profile unchanged and skips its combat-template override rather than failing startup.
