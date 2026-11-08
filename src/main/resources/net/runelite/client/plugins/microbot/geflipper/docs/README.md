# GE Flipper

GE Flipper carries out the Grand Exchange actions that Flipping Copilot suggests. Choose your items and trading strategy in Copilot; GE Flipper clicks Copilot's highlighted controls, fills in its price and quantity prompts, and handles its Modify and Abort suggestions.

## Requirements and setup

- Microbot 2.6.53 or later.
- Flipping Copilot installed, enabled, signed in and giving suggestions.
- Access to the Grand Exchange and enough coins in your inventory or bank (1M+ recommended).

Set up Copilot first, then switch GE Flipper on. Start at the Grand Exchange or carry a teleport to it. Stop GE Flipper before you trade by hand. Your own mouse or keyboard input, Break Handler's pause and the pause button in the Microbot header all pause GE Flipper.

GE Flipper works every Grand Exchange slot the way Copilot suggests, including Copilot's suggestions to abort an offer. Don't keep offers of your own on the GE while it runs.

## Settings

**End / Finish** and **Show overlay** are always shown. Everything else is in folded sections: Trading, Mouse, AFK breaks, Muling and Help. Click a section to open it; the client remembers open sections until it restarts. Settings that are off, or that do nothing with your other choices, have grey text. That is display only and never changes a setting.

- **End / Finish:** stop cleanly. See [Finishing](#finishing).
- **Show overlay:** status, profit, GP per hour, GE slots, the OSRS update countdown and mule progress on the game screen.

**Trading**

- **Select suggestions with:** enter Copilot's price and quantity with the hotkey (E) or with mouse clicks.
- **Copilot left-click swap:** On uses Copilot's swapped left-click for Modify and Abort; turn on slot swap in Copilot's own settings too. If the left-click isn't ready after the mouse arrives, GE Flipper uses the slot's menu option once instead. Off always uses the slot's menu option. GE Flipper never changes Copilot's settings.
- **Finish on OSRS update** (off by default): when an update countdown appears, run End / Finish automatically.

**Mouse**

- **Move mouse off screen** (off by default): while Copilot says Wait, now and then move the cursor off the game screen, like someone looking away. At most one move per wait; trading always comes first.
- **Preset:** AFK, Semi-AFK and Attentive Human start near 25, 50 and 75 on the slider, with a little variation each session. Time of day / Fatigue follows a daily curve (most active around 10:00, least around 22:00) and needs Randomize mouse speed. Custom uses your own slider value.
- **Randomization:** how often and how soon the idle mouse moves. Right is more often; fully left is never.
- **Fatigue clock:** where the Time of day / Fatigue day starts: Morning (09:00), Mid-day (13:00), Night (21:00), a time you type, or this computer's clock. Only used when that preset, Move mouse off screen and Randomize mouse speed are all on.
- **Start time (HH:mm):** the time for "Type a time", in 24-hour form such as 14:30. An invalid time pauses idle mouse movement until you fix it.
- **Randomize mouse speed** (off by default): vary the speed of idle moves and GE Flipper's own clicks between 80% and 120% of normal. Turning it on picks the Time of day / Fatigue preset. Walking and banking keep Microbot's normal speed, and Microbot's shared mouse and antiban settings are not changed.
- **Block manual mouse input** (off by default): ignore your physical mouse on the game screen while GE Flipper runs and you're logged in. The keyboard and the settings sidebar still work, so you can always turn it off.

**AFK breaks**

- **AFK mode** (off by default): like a player with the game on a second screen. GE Flipper finishes the current Copilot suggestions, then goes AFK before checking again. Stay logged in, or log out between check-ins.
- **Check in every (min):** average minutes between check-ins; each break varies by about 35%.

**Help**

- **Getting started**, **Stopping**, **Settings** and **Problems?**: short guides to setting up, stopping cleanly, what each section does and fixes for common problems.
- **Detailed logs:** log GE Flipper's own activity in detail for troubleshooting. Other plugins' logging is not changed. Suggested prices, quantities and item IDs are never logged.
- **About:** credits. GE Flipper was first made by chocken.

## Finishing

Press **End / Finish** while GE Flipper runs to stop cleanly:

1. Breaks are held off (through Break Handler's own lock) so a break can't log you out halfway.
2. Active buy offers are cancelled and the bought items are collected.
3. Copilot's temporary sell-only mode lists those items, following Copilot's sell and Modify suggestions. Sell offers already on the GE stay there; GE Flipper doesn't wait for them to sell.
4. When everything is listed and Copilot says Wait, GE Flipper stops all input and waits for the game to log you out for being idle. Meanwhile Break Handler and AutoLogin don't log you back in. Logging in by hand, or starting another script, ends that.
5. After the logout GE Flipper switches itself off and gives back the break lock. Break Handler stays on and keeps its settings.

If the game hasn't logged you out after 10 minutes (another plugin may be keeping you logged in), GE Flipper switches itself off anyway and tells you in chat.

Finish needs an active Copilot session. If offer or inventory data is missing, an action isn't available, or your inventory is full, it waits and says why on the overlay. Finish only handles your offers and inventory, not items in your bank. Switching GE Flipper off by hand still stops it at once.

## Muling

Muling is off by default. It moves GP from a flipping account to a Mule account, and both clients must run on this computer.

- On the flipping account pick **Flipper (sends GP)**, enter the Mule's in-game name, and set **Keep for flipping** and **Mule when over**. Keep for flipping counts GP, items and GE offers. Mule when over is the total that starts a transfer; `0` starts one whenever there's at least 1,000 GP above the keep amount. For example, keep `50M` and mule when over `75M`: nothing happens at 60M; at 75M GE Flipper frees up and sends the extra 25M.
- On the Mule account pick **Mule (receives GP)**. The Mule waits logged out and logs in only when a Flipper is ready to trade.
- **Mule before OSRS update** (on, only used while muling): when an update countdown appears, send everything above the keep amount before stopping. Only loose GP is sent, and it's skipped when less than 90 seconds remain.
- **Mule world:** `0` uses the Mule's world and hops back afterwards; or enter a world to always meet there.
- **Mule port:** the port the two clients use to talk. It must match on both.

To free up GP, the Flipper cancels its buy offers and follows Copilot's sell suggestions until the extra amount is loose GP, then meets the Mule and trades it. Both trade screens check the other player's name and the coins before accepting. The Mule only accepts a trade request from the Flipper it is expecting and never sends trade requests itself. If the amount, the Mule or the connection changes, the transfer is cancelled safely.

The two clients talk over this computer's loopback address only; nothing on your network can reach them and nothing is sent anywhere else. The Mule's overlay shows the link status, total held, GP received this session and all time; on the login screen it shows the same rows in a small window over the game area. The all-time total is saved in a small file under `.runelite/geflipper`.

## Privacy

GE Flipper has no account or password store, telemetry or updater, and sends nothing over the internet. It reads Copilot's suggestions in memory and clears them when it stops. Logs leave out suggested prices, quantities and item IDs.

## Limitations

GE Flipper follows Copilot; it doesn't pick items or prices itself. After an update, watch a short session (price and quantity entry, Modify and Abort, and End / Finish) before running it unattended.

## Version history

**1.3.0**

- Muling between a Flipper and a Mule account on the same computer.
- AFK breaks, Finish on OSRS update (off by default) and Block manual mouse input (off by default).
- End / Finish holds breaks while listing, then waits for the idle logout with auto-login held off, and switches GE Flipper off. It never switches Break Handler off.
- Simpler settings: two controls on top, folded sections, shorter names and tooltips, and grey text for settings that aren't in use.
- A bigger overlay with status, world, GE slots, the update countdown and mule progress.
- Honours the pause button in the Microbot header. Needs Microbot 2.6.53 or later.
- Copilot's swapped Abort/Modify left-click now counts once the cursor is anywhere on the slot button, not only on the exact pixel.
- Setting keys are unchanged, so saved settings carry over. The help is rewritten as short guides under new keys; old saved copies of the help text are removed once.

**1.2.81** fixed Abort and Modify clicks when Copilot highlights a slot. **1.2.80** added waiting mouse presets, the fatigue clock and optional mouse speed variation. **1.2.70** added End / Finish and the waiting mouse.
