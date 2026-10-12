# Monkey Madness II Caves Plugin

The **Monkey Madness II Caves Plugin** provides an automated way to **throw chins** or **cast AOE magic spells** in the Monkey Madness II caverns.

---

## 📍 Starting Position & Aggro reset Location

Hole 2 is the currently mapped route. The plugin now tries to webwalk to the
Jungle Grass entrance on Ape Atoll, follow the recorded middle passage, and
reach the tile marked **`2`**. This end-to-end trip still needs a live test.
The aggro reset area is highlighted with yellow lines.

![Maniacal_Monkey_Spot](./assets/Maniacal_Monkey_Spot.png)

---

## ✨ Features

- Supports **Chinning** and **Bursting/Barraging**, with optional **Custom Attack Delay**
- Ranged chinning defaults to stacking monkeys and letting auto-retaliate attack;
  **Click ranged attack targets** restores explicit target clicks. Magic still
  uses explicit attack/cast actions.
- Automatically resets aggro
- Attack delay handling:
    - **Custom delay** (user-defined)
    - **Mode-based defaults**:
        - Chinning → `1800ms` (~3 ticks)
        - Bursting → `3000ms` (~5 ticks)
- Auto-casting or manual spell casting depending on configuration

---

## ⚙️ Configuration

| Setting              | Description                                                                 |
|-----------------------|-----------------------------------------------------------------------------|
| **Use Custom Delay**  | Enable to set your own attack delay (ms).                                   |
| **Custom Delay**      | Milliseconds between attacks (e.g., `3100`).                               |
| **Mode**              | `MAGIC` or `RANGE` – sets the default delay.                               |
| **Auto Cast**         | If enabled, the plugin will cast the configured spell on the target.        |
| **Magic Spell**       | Spell to be used when auto casting.                                         |
| **Dungeon route**     | Numbered hole to use. Hole 2 is mapped; Holes 1, 3, 4 and 5 stop safely.  |
| **Click ranged attack targets** | Off by default for auto-retaliate chinning; on restores the earlier explicit ranged attack clicks. Magic is unaffected. |

---

## 🗺️ How It Works

1. Webwalk to the Jungle Grass entrance at `(2714,2788,0)` and investigate it.
2. Follow the selected recorded route to its numbered hole. Route 2 was recorded
   on one character; dungeon paths can differ between characters.
3. Check the cavern through the hole; world-hop if occupied, then enter.
4. Walk to the training area and attack using chinchompas or magic.
5. Move between the recorded tiles to stack monkeys. If required supplies run
   out, the plugin attempts to exit and log out.

### Adding another route

The selector lists all five numbered holes, but only `DungeonRoute.HOLE_2` has a
verified waypoint list and hole object ID. For another character/route, record
the Jungle Grass arrival, each safe turn/obstacle, and the target hole with the
Action Recorder. Add the observed path and hole ID to `DungeonRoute`, then
verify the lower-level approach and combat tiles rather than assuming Hole 2's
targets apply. Unmapped selections intentionally stop before moving.

---

## 📋 Requirements

- A **light source** in your inventory or equipped
- **Monkey Madness II** quest completed (access to caves)
- Proper gear equipped:
    - Chinchompas or runes for your spell
    - At least **1 prayer potion**
    - (No gear management is handled by the plugin)

---

## ⚠️ Notes

- Do **not** run this plugin unattended for extended periods.
- Always monitor for unexpected behavior or issues.  
