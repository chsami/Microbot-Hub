# [Dro] AIO Slayer

Created by **Big L** and **DRO (droplugins)**.

AIO Slayer runs Slayer from start to finish. It gets a task from your chosen master, travels to the task with [Dro] Efficient Walker, fights with the gear, prayers and cannon you set for that monster, banks for supplies, and goes back for the next task.

## Requirements

- **[Dro] Efficient Walker**, downloaded from the Microbot Hub and enabled. AIO Slayer will not start without it, and shows a message telling you to install it.
- Microbot client version 2.6.55 or later.
- The **Inventory Setups** plugin with a setup named `slayerdefault`. Optional per-task setups: `melee`, `ven`, `ranged`, `basilisk`, `dragon`, `blue dragon`, `magic`, `stab`, `burst`, `burstwildy`, `wyvern`, `demon`, `kalphite`, `kurask`, `zygo`, `wyrm`.
- The Combat and Slayer levels your chosen master needs.

## Slayer masters

Turael, Spria, Mazchna, Vannaka, Chaeldar, Nieve, Steve, Duradel, Kuradal and Krystilia.

If your Combat or Slayer level is too low for the master you picked, the script stops and tells you which master works at your level.

**Krystilia gives Wilderness tasks and you can die.** Pick a Wilderness setup you can afford to lose, and look through the **Wilderness Slayer** settings (recovery food, revenant weapon charges, cannon use) before starting. **Cannon Wilderness tasks** is off by default: a cannon set up in the Wilderness can be lost if you die.

## Auto Travel off

With **Auto Travel** off, walk to the task yourself. The script starts fighting once you are at the task location or within Attack Radius of a task monster. If you start away from your cannon spot it fights where you stand without the cannon. Wilderness tasks need Auto Travel on.

## Usage

1. Make your `slayerdefault` Inventory Setup, plus any per-task setups you want.
2. In **General**, choose a **Slayer Master** and turn on **Auto Travel** and **Get New Task**.
3. Set **Banking**, **Combat**, **Cannon** and **Loot** the way you like. **Edit monster config** lets you choose prayers, gear and bracelets per monster.
4. Install and enable **[Dro] Efficient Walker** from the Microbot Hub.
5. Enable **[Dro] AIO Slayer** and start it.

## What it does

- **Tasks:** asks the master for a task, and can skip or block tasks from your lists once you have the points you set.
- **Travel:** [Dro] Efficient Walker plans routes with teleports, fairy rings, spirit trees and other transports. AIO Slayer handles the scripted entrances to Slayer dungeons.
- **Combat:** attacks task monsters within the attack radius and prioritises superiors. It eats and drinks at your thresholds, uses protection and offensive prayers, dodges area attacks and can hop when crashed.
- **Cannon:** sets up and refills a dwarf multicannon on the task you allow, and can buy cannonballs at the Grand Exchange.
- **Banking and POH:** banks for food, potions and cannonballs, and can restore at your house pool before or after tasks.
- **Loot:** by value or item list, with bones, ashes, runes, coins and high alchemy options.
- **Breaks:** optional smart breaks and a natural mouse profile.

## When it stops

If something it needs is missing, such as food or a required Slayer item, it logs out and shows what to restock.

When it is waiting for you instead (no Wilderness setup chosen, no escape item, an ordinary task still active, a missing skill level, blocked safety settings, missing bank supplies, or Auto Travel off), the overlay header shows "Waiting:" with the reason, even while collapsed, and one chat message says what to do.

## [BL] Slayer

This plugin is separate from [BL] Slayer, which is still in the Hub with its own settings. Run only one of the two at a time.

## Credits

Slayer by Big L and DRO (droplugins). Travel uses DRO's [Dro] Efficient Walker plugin.
