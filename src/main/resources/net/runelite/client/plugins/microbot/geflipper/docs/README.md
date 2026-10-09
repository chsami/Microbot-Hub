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
- **Move mouse off screen while waiting:** optional and off by default. Enable it in GE Flipper's settings.
- **Randomization:** one plain slider directly below the enable checkbox controls the random movement chance and both automatic delay limits together. It has no percentage readout or numbered scale. Unchecking **Move mouse off screen while waiting** dims and disables the slider. Move right for sooner, more frequent randomized movement; move fully left to disable movement. The far-right position attempts movement after a random 2–5 seconds of eligible waiting. There are no manual minimum/maximum delay controls.
- **End / Finish:** while GE Flipper is running, click once to stop buying, cancel active buy offers, and collect purchased items into inventory. It uses Copilot's temporary sell-only mode to list those items and carry out its current sell/Modify suggestions, then disables GE Flipper once a fresh Wait suggestion and verified inventory/offer state agree. Existing sell offers remain listed; it does not wait for them all to sell. The button dims while finishing. Ordinary plugin disable still stops immediately.
- **Verbose Logging:** detailed control-flow diagnostics for GE Flipper only. Suggested prices, quantities, and item identifiers are omitted.

While active Copilot explicitly says Wait on the GE overview, move at most once per continuous wait and skip a cursor already outside. Trading actions take priority; pauses, real input, prompts, errors, and unavailable UI cancel the timer. Empty GE popup scaffolding is allowed; actual visible dialog content blocks movement. This uses Microbot's shared natural mouse movement for the virtual game cursor, preserving your desktop pointer and shared antiban preferences. Natural movement follows the client's existing input handling; stopping an already started path is governed by Microbot.

If a Modify/Abort action is unavailable or the required swap setting is off, GE Flipper pauses that action with instructions. It does not repeatedly open View offer. Price warnings are handled within the GE popup, so disabling in-game price warnings is unnecessary.

## Updates and privacy

Version 1.2.70 adds **End / Finish** to the native settings panel. Settings bindings use panel events without periodic UI scans. Startup and configuration events do not write preferences or enable other plugins. The slider saves only explicit changes to GE Flipper's own frequency preference; opening, refreshing, or closing the settings saves nothing. Existing choices are preserved, and old delay preferences remain stored but are no longer used. GE Flipper does not migrate preferences or override shared mouse/antiban settings. Logging configuration is scoped to its own package.

Finish is an explicit, temporary session action. It does not store a command or change Copilot's saved preferences, pause state, or item strategy. It restores the previous in-memory sell-only mode when finishing ends or GE Flipper stops, while respecting a user who turns that mode off. Replayed settings, Reset, and profile changes cannot start finishing. A profile change pauses an active Finish request until the plugin is restarted. Only GE Flipper is disabled on completion. After restarting the plugin, press Finish again if needed.

GE Flipper has no account or credential store, trade export, telemetry endpoint, or automatic updater. It reads Copilot's active suggestions and display labels in memory and clears cached references on shutdown. Diagnostics use the client's existing logging facility. GE Flipper's own messages omit suggested prices, quantities, and item identifiers. Copilot provides its own account, network, and storage functionality.

Warning detection is limited to the GE popup, money errors are limited to visible GE setup controls, and unavailable client-thread reads suspend the iteration rather than count as closed windows or successful confirmations.

## Limitations

GE Flipper follows Copilot's suggestions; it does not choose its own items or margin strategy. Shared client-thread stalls can interrupt trading, and this plugin does not resolve their underlying cause. After installing an update, test confirmation, price/quantity entry, and Modify/Abort with your selected settings before an extended run.

Finish requires a supported, active Copilot session. Missing offer/inventory data, unavailable actions, paused suggestions, errors, or inventory items Copilot cannot sell leave it waiting with an overlay message rather than report success. Make inventory space manually if collection is blocked; Finish does not bank unrelated items or choose its own sale price. It handles offers and current inventory, not unrelated bank stock. Test this sequence before relying on unattended shutdown.

Microbot 2.6.26–2.6.29 normally renders numeric configuration as a spinner. While GE Flipper is enabled, it replaces only its own Randomization row with a slider. Disabling the whole plugin restores the native number field. If a future client changes the settings layout, that native field remains available. No other plugin's settings or client classes are modified.
