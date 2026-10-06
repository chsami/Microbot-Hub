# GE Flipper Plugin

The **GE Flipper Plugin** is an automation tool for Old School RuneScape, designed to help players efficiently flip items for profit at the Grand Exchange. Built for the Microbot RuneLite client, this plugin streamlines the process of buying and selling items, tracking margins, and managing offers, allowing for hands-free and optimized merchanting.

---

## Features

- **Automated Flipping:**  
  Automatically places buy and sell offers at the Grand Exchange based on user-defined or detected margins, maximizing profit potential.

- **Margin Checking:**  
  Checks item margins to determine the most profitable buy and sell prices.

- **Offer Management:**  
  Monitors active offers, collects completed trades, and re-lists items as needed for continuous flipping.

- **Configurable Options:**  
  Users can select which items to flip, set margin thresholds, and adjust advanced behaviors in the configuration panel.

- **Failsafes and Error Handling:**  
  Handles running out of coins, full inventory, or unexpected in-game events.

---

## How It Works

1. **Configuration:**  
   Select the items you want to flip and set your margin preferences in the plugin panel.

2. **Startup:**  
   The plugin checks your inventory and coin pouch, preparing to place offers at the Grand Exchange.

3. **Automation Loop:**  
   The script performs the following:
    - Checks item margins (if enabled)
    - Places buy offers at the lower margin
    - Collects purchased items and places sell offers at the higher margin
    - Monitors and manages offers for continuous flipping

4. **Failsafes:**  
   Pauses or stops if requirements are not met, or if unexpected events occur.

---

## Configuration

Set item preferences and flipping strategies in Flipping Copilot. In GE Flipper:

- **Suggestion Selection:** use Hotkey (E) or Mouse to accept Copilot's price and quantity prompts.
- **Copilot left-click swap:** directly below Suggestion Selection, choose **On** or **Off**. On uses Copilot's swapped left-click: enable slot swap in Copilot too. The mouse moves smoothly to the slot and waits for the suggested Modify/Abort action before clicking. Off selects the supported slot action directly. This controls GE Flipper without changing Copilot's own setting. Existing selections are preserved. If an action is unavailable, GE Flipper reports it instead of clicking View offer.
- **Show Overlay:** display profit, runtime and actionable errors. The permanent Slot Swap row is not shown.
- **Verbose Logging:** enable detailed GE Flipper logs without changing other plugins' logging.

A successful Modify action opens the GE's modify setup, where GE Flipper accepts the suggested price and confirms it. It should not open View offer and repeatedly back out. If the required left-click action is unavailable, GE Flipper waits and shows the reason in its overlay.

See [review validation](REVIEW_VALIDATION.md) for logging lifecycle and slot-action test coverage.

---

## Requirements

- Microbot RuneLite client
- Sufficient coins for flipping
- Access to the Grand Exchange

---

## Usage

1. **Enable the Plugin:**  
   Open the Microbot sidebar, find the GE Flipper Plugin, and enable it.

2. **Configure Settings:**  
   Select your desired items and margin preferences.

3. **Start the Plugin:**  
   Click "Start" to begin automated flipping.

4. **Monitor Progress:**  
   The plugin will handle offers and profits automatically.

5. **Stop at Any Time:**  
   Click "Stop" to halt the automation.

---

## Limitations

- Only supports flipping items and strategies defined in the script logic.
- Requires the player to have sufficient coins and access to the Grand Exchange.
- May not handle all random events or interruptions (e.g., player death, disconnections).

---

## Source Files

- `FlipperPlugin.java` – Main plugin class, manages lifecycle and integration.
- `FlipperScript.java` – Core automation logic for flipping.
- `FlipperConfig.java` – User configuration options.

---

**Automate your merchanting and maximize your profits with the GE Flipper Plugin!**
