# AIO Magic Plugin

The **AIO Magic Plugin** is an all-in-one automation tool for Old School RuneScape, designed to train Magic efficiently by supporting a wide range of spells and activities. Built for the Microbot RuneLite client, this plugin automates various Magic-related tasks, allowing for hands-free and optimized Magic training and resource processing.

---

## Features

- **Multiple Magic Activities:**  
  Supports a variety of Magic training methods and utility spells, including:
    - **High/Low Alchemy** (Alching)
    - **Teleportation** (various locations)
    - **Superheat Item** (smelting ores into bars)
    - **Stun Spells** (Stun, Stun-Alch, Stun-Tele-Alch)
    - **Splashing** (safe Magic training)
    - **Spin Flax** (Lunar spell for crafting bowstrings)
    - **Teleport-Alch** (combining teleports and alching for efficiency)

- **Automated Spell Casting:**  
  Automatically selects and casts the chosen spell(s) based on user configuration.

- **Banking and Inventory Management:**  
  Handles banking for required runes, items, and resources, and manages inventory to ensure smooth operation.

- **Overlay Display:**  
  Provides a real-time overlay showing current activity, spell being cast, experience gained, runtime, and other useful statistics.

- **Configurable Options:**  
  Users can select which Magic activity to perform, set spell preferences, enable/disable the overlay, and customize behavior.

- **Failsafes and Error Handling:**  
  Handles common issues such as running out of runes/items, full inventory, or unexpected in-game events.

---

## Supported Activities

- **Alching:**  
  Automatically casts High or Low Alchemy on items in your inventory.

- **Teleporting:**  
  Repeatedly casts teleport spells to various locations for fast experience.

- **Superheat Item:**  
  Smelts ores into bars using the Superheat Item spell.

- **Stun, Stun-Alch, Stun-Tele-Alch:**  
  Casts Stun on NPCs, optionally combining with Alchemy or Teleport spells for maximum XP rates.

- **Splashing:**  
  Casts combat spells on NPCs without dealing damage for safe, AFK Magic training.

- **Spin Flax:**  
  Uses the Lunar spell to convert flax into bowstrings.

- **Teleport-Alch:**  
  Combines teleporting and alching for efficient experience gain.

---

## How It Works

1. **Configuration:**  
   The user selects the desired Magic activity and configures spell options in the plugin panel.

2. **Startup:**  
   The plugin checks for required runes, items, and inventory space. If needed, it will bank for supplies.

3. **Automation Loop:**  
   The script performs the selected Magic activity, casting spells, managing inventory, and handling banking as needed.

4. **Overlay:**  
   Displays real-time information such as:
    - Current activity and spell
    - Experience gained
    - Number of casts performed
    - Runtime and efficiency stats

5. **Failsafes:**  
   Stops or pauses if requirements are not met, or if unexpected events occur.

---

## Configuration

The plugin provides a comprehensive configuration panel (`AIOMagicConfig`) where users can:

- Select the Magic activity (Alch, Teleport, Superheat, etc.)
- Choose specific spells (e.g., which teleport or alch item)
- Enable or disable the overlay
- Adjust advanced options (delays, anti-patterns, etc.)

---

## Requirements

- Microbot RuneLite client
- Sufficient Magic level for the selected spells
- Required runes, items, and resources in the bank/inventory

---

## Usage

1. **Enable the Plugin:**  
   Open the Microbot sidebar, find the AIO Magic Plugin, and enable it.

2. **Configure Settings:**  
   Select your desired Magic activity and adjust settings as needed.

3. **Start the Plugin:**  
   Click "Start" to begin automated Magic training.

4. **Monitor Progress:**  
   Watch the overlay for real-time updates on progress and status.

5. **Stop at Any Time:**  
   Click "Stop" to halt the automation.

---

## Limitations

- Only supports activities and spells defined in the script logic.
- Requires the player to have the necessary runes, items, and Magic level.
- May not handle all random events or interruptions (e.g., player death, aggressive NPCs).

---

## Source Files

- `AIOMagicPlugin.java` – Main plugin class, manages lifecycle and integration.
- `AIOMagicConfig.java` – User configuration options.
- `AIOMagicOverlay.java` – In-game overlay display.
- `enums/` – Enumerations for spells, activities, and states.
- `scripts/` – Individual scripts for each Magic activity.

---

**Automate your Magic training and maximize your experience gains with the AIO Magic Plugin!**
## Splashing (v1.3.0)

- **Splash spell**: Auto picks the highest standard-book combat spell your Magic level and runes allow and upgrades on level-up; or use the "Combat Spell" setting.
- **Runes**: restocks from the bank (`Casts per trip`, default 1000); optional GE buying capped by `Max gp per purchase`; wields an owned elemental staff when it keeps you at -65 or lower.
- **Auto splash gear**: wears only metal armour (owned first, else cheapest bought on the GE) until the magic attack bonus is -65 or lower. `Require -65 magic bonus` stops the script if that cannot be reached.
- **AFK**: re-attacks every 15-19 minutes, re-targets NPCs further than 10 tiles, returns if drifted over 8 tiles. With no NPC name set it picks the nearest safe low-level NPC (chicken, rat, man, ...).

## XP tracking and stop level (v1.4.0)

- **Overlay**: shows Magic XP gained, XP/hour, and the estimated time to the next Magic level plus XP to level (`--` until there is data, at 99, or at 0 XP/hr).
- **Stop at Magic level** (General Settings, 0 = disabled): once your real Magic level reaches it, the plugin stops for every activity.
