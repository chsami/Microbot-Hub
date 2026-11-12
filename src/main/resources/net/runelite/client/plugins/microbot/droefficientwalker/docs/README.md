# [Dro] Efficient Walker

Author: droplugins

Efficient Walker is a webwalker for Microbot. It plans routes with teleports, fairy rings, spirit trees, charter ships and other transports, handles dungeon entrances and obstacles, and can bank for the items a route needs. It also supports your POH, Sailing and Quest Helper steps.

## How to use it

1. Install **[Dro] Efficient Walker** from the Microbot Hub and enable it.
2. To walk somewhere, right-click a tile in the game or a spot on the world map and choose **Test walk**. **Dry-run walk** plans and previews the route without walking, so you can check it first. **Cancel walk** stops it.
3. You can also pick a **Named destination** in the settings and press **Walk to named destination**, or bind hotkeys for a named destination, the nearest bank and cancelling.
4. Under **Route modification**, choose which teleports and transports it may use, such as fairy rings, spirit trees, charters or your POH. It only uses what your account has unlocked and the items you carry or keep in the bank.

## Wilderness safety

The Wilderness is off by default. Routes avoid it unless you turn on **Allow Wilderness travel**, and Wilderness teleports, levers and obelisks each have their own switch. Ferox Enclave by ring of dueling stays available because it is safe.

While it walks in the Wilderness, **Wilderness threat logout** (on by default) watches for players who can attack you. If one comes within the **Player safety radius** (32 tiles), counting players up to the **Upper combat buffer** above your attack range, it tries to log out. You cannot log out during combat with a monster, so it keeps travelling until that combat ends and then logs out. If a player has already attacked you, it stops walking and leaves the fight to you.

Scripts that fight in the Wilderness can hand the escape to the walker. [Dro] AIO Slayer does this on Krystilia tasks. In that case:

- At level 30 or below, it teleports out with a royal seed pod, amulet of glory, ring of wealth, combat bracelet or skills necklace.
- Above level 30, it waits until you are out of combat, then logs out.
- If a player is attacking you, it runs south on the minimap until a teleport works.
- If you are Tele Blocked, it stops instead of repeating clicks.

## Quest Helper steps

With Quest Helper installed and a quest selected in it, bind the **Walk to quest step** hotkey. Pressing it walks you to the quest's current step: beside the NPC or object you need, or to the step's location. Press it again after each step to go to the next one.

## For script authors

Other scripts can walk through Efficient Walker. Find the enabled `EfficientWalkerPlugin` through the plugin manager and call `getWalker()`. The returned walker offers `walkTo(WorldPoint)`, `getStatus()`, `getPlanningFailure()` and `cancel()`. `SPELLBOOK.md` in the plugin package describes the optional spellbook preparation service.

[Dro] AIO Slayer uses it this way and needs it to be installed and enabled.

---

<sub>The Efficient Walker engine was originally created by RLitePlus and has been heavily rewritten by Dro.</sub>
