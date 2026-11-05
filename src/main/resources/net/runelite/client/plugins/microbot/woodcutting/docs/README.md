# Auto Woodcutting Plugin

The **Auto Woodcutting Plugin** is a RuneLite plugin for the Microbot framework that automates woodcutting activities in Old School RuneScape (OSRS). It is designed to efficiently chop trees, manage inventory, and handle forestry events, making woodcutting more productive and less repetitive.

---

## Features

- **Automated Woodcutting:** Automatically chops selected tree types and manages movement between trees.
- **Forestry Event Handling:** Supports and reacts to various forestry events (e.g., Entlings, Foxes, Rituals, Hives, and more) for enhanced XP and rewards.
- **Customizable Actions:** Configure primary and secondary actions, such as dropping logs, banking, or fletching.
- **Overlay Display:** Shows real-time status, current action, and progress through an in-game overlay.
- **Safe Start/Stop:** Easily start or stop the script from the sidebar panel.

---

## How It Works

1. **Configure:** Set your preferences in the plugin configuration (tree type, actions, event handling, etc.).
2. **Start:** Enable the plugin and start the script from the sidebar.
3. **Automate:** The script will:
    - Locate and chop the selected tree type
    - Handle forestry events as they occur
    - Perform configured actions with logs (drop, bank, fletch, etc.)
    - With **Burn logs**, light the whole inventory of logs in a line from where you stand, then walk back to the trees once after the last log. With **Burn logs at campfire**, burn them on a lit fire or Forester's Campfire within 6 tiles. If a fire has to be lit and no Tinderbox is in your inventory, the script tries to take one from a nearby bank and stops with a message if it cannot.
    - Display current status and progress in the overlay
4. **Stop:** You can stop the script at any time from the panel.

---

## Requirements

- Microbot RuneLite client
- Appropriate axe and required woodcutting level for your chosen tree
- Supplies for forestry events (if participating)

---

## Disclaimer

- This plugin is for educational and research purposes only.
- Use at your own risk. Automation may violate the OSRS game rules and could result in account penalties.
- The developers are not responsible for any consequences resulting from the use of this plugin.

---

## Feedback

For issues or suggestions, please open an issue or contribute via the Microbot Hub repository.