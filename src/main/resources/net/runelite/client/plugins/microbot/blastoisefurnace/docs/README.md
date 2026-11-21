# Blastoise Furnace Plugin

The **Blastoise Furnace Plugin** is an automation tool for Old School RuneScape, designed to efficiently smelt bars at furnaces, such as the Blast Furnace minigame. Built for the Microbot RuneLite client, this plugin streamlines the process of smelting ores into bars, managing inventory, and banking, allowing for hands-free and optimized Smithing experience gains.

---

## Features

- **Automated Bar Smelting:**  
  Automatically smelts ores into bars at supported furnaces, including the Blast Furnace, for fast and efficient Smithing XP.

- **Banking and Inventory Management:**  
  Withdraws required ores from the bank, manages inventory space, and deposits finished bars as needed.

- **Overlay Display:**  
  Real-time overlay shows current status, bars smelted, experience gained, runtime, and other useful stats.

- **Configurable Options:**  
  Users can select which type of bar to smelt, enable/disable the overlay, and adjust advanced behaviors in the configuration panel.

- **Failsafes and Error Handling:**  
  Handles running out of ores, full inventory, or unexpected in-game events.

---

## How It Works

1. **Configuration:**  
   Select the type of bar to smelt and adjust settings in the plugin panel.

2. **Startup:**  
   The plugin checks for required ores in your inventory or bank. If needed, it will withdraw supplies.

3. **Automation Loop:**  
   The script performs the following:
    - Travels to the furnace (if not already there)
    - Smelts ores into bars
    - Banks for more ores or deposits bars when inventory is full
    - Repeats the process for continuous training

4. **Overlay:**  
   Displays real-time information such as:
    - Current action (e.g., "Smelting", "Banking")
    - Bars smelted
    - Experience gained
    - Runtime and efficiency stats

5. **Failsafes:**  
   Pauses or stops if requirements are not met, or if unexpected events occur.
    - If the conveyor belt opens an option prompt, the script picks the Yes option and writes the question and the chosen option to the log.
    - Stops after 3 trips in a row where ore did not go on the belt, logs out and shows a message with the reason.

---

## Configuration

The plugin provides a configuration panel (`BlastoiseFurnaceConfig`) where you can:

- Select the type of bar to smelt (see `enums/Bars.java` for supported bars)
- Enable or disable the overlay
- Adjust advanced options (delays, anti-patterns, etc.)

---

## Requirements

- Microbot RuneLite client
- Sufficient Smithing level for the selected bar
- Required ores in the bank/inventory
- Access to a supported furnace (e.g., Blast Furnace)

---

## Usage

1. **Enable the Plugin:**  
   Open the Microbot sidebar, find the Blastoise Furnace Plugin, and enable it.

2. **Configure Settings:**  
   Select your desired bar type and adjust settings as needed.

3. **Start the Plugin:**  
   Click "Start" to begin automated bar smelting.

4. **Monitor Progress:**  
   Watch the overlay for real-time updates on progress and status.

5. **Stop at Any Time:**  
   Click "Stop" to halt the automation.

---

## Limitations

- Only supports bars and activities defined in the script logic.
- Requires the player to have the necessary ores and Smithing level.
- May not handle all random events or interruptions (e.g., player death, aggressive NPCs).

---

## Source Files

- `BlastoiseFurnacePlugin.java` – Main plugin class, manages lifecycle and integration.
- `BlastoiseFurnaceScript.java` – Core automation logic for bar smelting.
- `BlastoiseFurnaceConfig.java` – User configuration options.
- `BlastoiseFurnaceOverlay.java` – In-game overlay display.

### Enums (`enums/`)
- `Bars.java` – Supported bar types.
- `State.java` – Script state management.

---

**Automate your Smithing training and maximize your experience gains with the Blastoise Furnace Plugin!**