# GE Flipper

GE Flipper automates the Grand Exchange actions suggested by Flipping Copilot. Configure the items and trading strategy in Copilot; GE Flipper handles its highlighted controls, price and quantity prompts, and supported Modify/Abort actions.

## Requirements and setup

- Microbot 2.6.26 or later.
- Flipping Copilot installed, enabled, signed in, and providing suggestions.
- Access to the Grand Exchange and sufficient coins in the inventory or bank.

Enable GE Flipper after configuring Copilot. Startup may bank inventory items and withdraw coins before opening the exchange. Stop GE Flipper before trading manually. Human input and the client's global pause suspend the script.

## Settings

- **Suggestion Selection:** Hotkey (E) or Mouse for Copilot's price and quantity prompts.
- **Copilot left-click swap:** On uses Copilot's supported swapped left-click action. Enable slot swap yourself in Copilot's settings. Off selects the supported Modify/Abort slot operation directly. GE Flipper never changes Copilot's setting.
- **Show Overlay:** display Copilot's existing profit and runtime labels, plus actionable errors. Cached labels are cleared when the overlay is hidden, the plugin stops, or the account logs out.
- **Verbose Logging:** detailed control-flow diagnostics for GE Flipper only. Suggested prices, quantities, and item identifiers are omitted.

If a Modify/Abort action is unavailable or the required swap setting is off, GE Flipper pauses that action with instructions. It does not repeatedly open View offer. Price warnings are handled within the GE popup, so disabling in-game price warnings is unnecessary.

## Updates and privacy

Version 1.2.63 removes automatic preference migration and shared mouse/antiban overrides. Startup and configuration events do not write user preferences or enable other plugins. Logging configuration is scoped to GE Flipper's package.

GE Flipper has no account or credential store, trade export, telemetry endpoint, or automatic updater. It reads Copilot's active suggestions and display labels in memory and clears cached references on shutdown. Diagnostics use the client's existing logging facility. GE Flipper's own messages omit suggested prices, quantities, and item identifiers. Copilot provides its own account, network, and storage functionality.

The fixes from 1.2.61 and 1.2.62 remain: warning detection is limited to the GE popup, money errors are limited to visible GE setup controls, and unavailable client-thread reads suspend the iteration rather than count as closed windows or successful confirmations.

## Limitations

GE Flipper follows Copilot's suggestions; it does not choose its own items or margin strategy. Shared client-thread stalls can interrupt trading, and this plugin does not resolve their underlying cause. After installing an update, test confirmation, price/quantity entry, and Modify/Abort with your selected settings before an extended run.
