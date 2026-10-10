# [Dro] Zulrah

**Author:** Dro

A full Zulrah trip script for Microbot. It manages the trip from gearing and travel through the encounter, looting, and return to Ferox for the next trip. It identifies magic and ranged weapons and equipment from a saved Microbot Inventory Setup and supports hybrid gear switches or mage-only trips.

## Copying the plugin to another client

Copy the complete `src/main/java/net/runelite/client/plugins/microbot/drozulrah` folder into the matching source location in a compatible Microbot client. The package includes its descriptor constants and the three original Zulrah overlay images as Java-embedded PNG data; running the plugin does not require copying its resources folder. The destination still needs compatible Microbot and Inventory Setups APIs. Documentation and catalog artwork remain in resources for Hub publishing.

## Getting started

1. Create and save a Microbot Inventory Setup containing the equipment and inventory items for your trip, then select it in the Zulrah plugin configuration.
2. Keep every required item and supply in your bank. The script loads the selected setup; it does not buy items or replenish supplies.
3. Start with the bank open or near a bank. The script supports starting from any bank, including Ferox Enclave. It also recognizes startup at Zul-Andra; when starting there, arrive with the trip gear and supplies already ready because bank re-gearing happens at a bank.
4. Include Zul-andra teleport scrolls and a charged Ring of dueling. They are required for travel to Zulrah and return to Ferox.
5. Make sure charged weapons and ammunition are ready. For thralls, use the Arceuus spellbook, carry a Book of the dead and the required runes, and keep enough runes for multiple trips.

The setup should include both styles' weapons and switch pieces for hybrid mode. Ranged is used against blue Zulrah; magic is used against green and red Zulrah. With only a magic weapon detected, the script stays mage-only. It identifies gear using equipment stats and item style names.

## What the script handles

- Loads and verifies the saved gear and inventory setup, builds magic/ranged equipment plans, and checks for the required teleport and ring before leaving the bank.
- Restores at Ferox between trips, banks and re-gears, teleports to Zul-Andra, finds and boards the sacrificial boat, and waits for the instance and encounter to be ready.
- Tracks Zulrah's rotation and phase from encounter events, selects protection and offensive prayers, switches gear by phase, and pre-positions for upcoming phases.
- Dodges melee attacks and routes around venom clouds while moving between safe stands.
- Eats and drinks supplies according to the configured health and prayer thresholds, including emergency combo healing when the required food, Saradomin brews, and super restores are available.
- Summons the best available non-melee thrall when enabled and when the spellbook, book, and runes allow it.
- Uses supported ranged and magic weapon special attacks when enabled and when the weapon's special-energy threshold is met. It does not switch weapons solely to use a special.
- Collects Zulrah loot, returns to Ferox when the loot window ends or supplies are low, and prepares for the next trip.
- Shows trip state and progress in the Zulrah overlay.

## Automatic death recovery (1.10.16)

After a death, the plugin waits for respawn and supports **Lumbridge and Edgeville**.
It uses the local bank to withdraw a charged Ring of dueling and a Zul-andra teleport
if they are not already carried, closes the bank, and teleports to Zul-andra.
It approaches Priestess Zul-Gwenwynig, selects **Collect**, clicks **Reclaim**, and
waits for the returned items to be observed. The game handles returning/equipping
reclaimed gear; the script does not add a manual equip sequence at the priestess.
It then uses the ring to return to Ferox, completes the pool/bank route, and loads
the selected Inventory Setup before the next trip. It does not board the boat
until recovery and normal trip preparation are complete.

Keep charged dueling rings, Zul-andra teleport scrolls, required setup supplies and
any applicable reclaim fee available. Recovery stops with a clear message for an
unsupported respawn location, missing ring or teleport, insufficient inventory
space, an unconfirmed teleport or reclaim, or a recovery step with no progress.
It does not walk from unsupported respawns or guess an alternative death route.
Starting the plugin at either supported spawn also starts this recovery sequence.

Lumbridge follows the southern castle stairs to the top-floor bank; Edgeville uses
the recorded local bank approach. Movement is observed before repeating inputs.
Bank supplies are checked from fresh contents without waiting separately for each
absent ring charge. Combat rotations, prayers, gear-switching and safety behavior
retain their existing implementation.

## Ferox return and boat travel

Each return uses the tested camera-to-pool route, with the original short walk as
fallback if the pool cannot be framed. After the pool click, arrival/restoration
settling precedes banking. Existing startup at Ferox still skips the return pool
sequence. Short walk clicks are not repeated while the character is already moving.

Each return independently has a **1-in-10** pool AFK chance: wait 250–300ms after
the pool click, move the mouse off the client canvas, then hold for 2.8–4.8 seconds.
It also has a **1-in-35** XP-check chance after opening the bank: close the bank,
open Skills, hover a randomly selected Ranged, Magic or Hitpoints skill for
3.7–4.2 seconds, return to Inventory, and reopen the bank before regear.
These are separate from Smart breaks and keep their selection during recovery.

At Zul-andra, a visible sacrificial boat is Quick-boarded from the teleport spot.
Otherwise the script attempts a bounded, slightly variable camera turn and retains
the existing boat approach fallback. The ordinary one-click boarding latch stays
in place. Travel and recovery inputs use BaseProfileDro; loading and breaks pause
the route deadlines. Inventory setup loading uses the public client's standard
`Rs2InventorySetup` implementation directly. The Hub plugin does not depend on
private client mouse extensions.

## Configuration

| Setting | Default | Purpose |
| --- | ---: | --- |
| Eat at HP % | 58 | Normal food threshold. |
| Panic eat HP % | 38 | Emergency threshold; emergency healing takes priority over damage and gear switching. |
| Restore prayer at | 28 | Drinks prayer or super restore at or below this prayer level. |
| Offensive prayers | On | Uses the best available ranged or magic offensive prayer. |
| Use thralls | On | Summons the best available non-melee Arceuus thrall when setup, spellbook, and runes permit. |
| Pre-position lead | 3 ticks | Moves toward the next known stand before the current phase ends. Adjustable from 0 to 6 ticks. |
| Smart breaks | On | Enables the profile's breaks; in this plugin they begin at Ferox between trips. |
| Use special attacks | On | Uses supported equipped ranged or magic specials at their required energy; does not change weapons just to spec. |

Most foods and commonly used combat and prayer potions are supported. For emergency combo healing, include a main food such as manta rays, Saradomin brews, and super restores. The plugin cannot replace missing supplies or determine that your bank has been stocked correctly in advance.

## Automated and in-game validation

Focused automated tests cover boat approach selection, safe route movement around blocked tiles and venom clouds, rotation recognition, prayer sequencing, melee dodging, and supported special-weapon behavior.

More than 25 in-game test runs completed successfully across fights, travel, banking, looting, and re-gearing. The script was refined after each run based on the results.

## BaseProfileDro humanizer and behavior profile

`BaseProfileDro` is the customizable behavior template used by the plugin. It provides session-specific timing and mouse behavior, Microbot antiban configuration, smart breaks, optional login recovery, camera controls, and an optional live profile overlay. Zulrah supplies its own task actions and safe points; the profile adds behavior around those actions rather than choosing combat, pathing, banking, or safety decisions.

### Action timing and cadence

The host can wrap actions as active work, setup, or maintenance and mark time-sensitive actions urgent. Before an action, the profile adds a randomized reaction delay: active work is generally longest, setup is intermediate, maintenance is shortest, and urgent actions use a short delay and skip extra hesitation. The reason is to vary the timing around real task inputs while preserving quicker handling for urgent actions.

After a successful action, it adds a shorter settling delay. Active work can also trigger a rhythm pause, a small mouse nudge, or an optional subtle camera nudge. The Boolean action wrapper only applies its after-action behavior when the action reports success.

### Session personality

A fresh login gets one stable randomized personality, rerolled on the next login. It contains speed, mouse confidence, AFK tendency, hesitation, and check-habit traits. Speed scales reaction delays; confidence and hesitation adjust mouse/camera behavior; check habit scales on-screen attention chances. AFK tendency is stored and exposed for integrations but is not currently used by this class to make decisions. Keeping the traits stable for a login session gives that session a consistent behavior profile.

### Mouse activity and parking

Mouse activity has three modes:

- **AFK:** default parking chance 75% during a host-reported idle opportunity.
- **Balanced:** default parking chance 40%.
- **Active:** rolls a 10–15% parking chance once per login, then keeps that rate stable until the next login.

Parking can be disabled (`NONE`), tied to a selected edge, or set to `RANDOM`, which chooses an edge at login and keeps it for that session. The cursor is moved meaningfully outside the client canvas; the profile checks the result and retries if the platform clipped the movement. This centralizes off-screen behavior so another antiban template cannot change the selected edge.

The host reports when the task is naturally idle and safe for the cursor to leave the client. The profile makes one decision per idle stretch: park off-screen, leave the cursor alone, or perform a safe on-screen attention action. By default, attention moves to a random point within the visible inventory widget. A host may provide a different known-safe action. After about 8–12 successful parks, it attempts one on-screen check. During active work, it may make a small on-screen nudge, with at least 2.2 seconds between nudges.

The reusable point helper samples within a supplied safe rectangle, weighted toward its center with an inset. Movement and click helpers use that point; the click helper is only for bounds the host has already confirmed are safe.

### Native antiban settings

When enabled, the profile resets existing Microbot antiban settings, optionally applies a host-specific template, and then applies shared activity, intensity, play style, and adaptive settings. The shared profile enables fatigue and attention-span simulation, behavioral/contextual variability, nonlinear intervals, profile switching, time-of-day adjustment, dynamic activity/intensity, mistake simulation, and natural mouse behavior.

It allows native random on-screen mouse movement at a configurable chance (default 36%), but disables native off-screen movement so the profile retains control of the parking edge. It disables native microbreaks so they do not stack with smart breaks, and disables action cooldowns. Cooldowns are re-disabled on each tick in case another template enables them. Shutdown deactivates and resets antiban settings.

### Smart breaks and login recovery

Breaks are enabled by default. The next break is scheduled at a random interval between 20 and 140 minutes. A due break remains queued until the host says a break is safe. At that point, the profile chooses between an AFK break and a logout break according to its configured chance; the base default is a 100% logout-break chance.

AFK breaks last a random 2–6 minutes by default, park the mouse, then resume. Logout breaks last a random 5–40 minutes by default and try to log out up to three times. After the break, login recovery waits for the login screen and retries with increasing delays of approximately 5, 15, 60, and 300 seconds, with jitter. After a successful login it waits a 20-second settling period by default before resuming. If the client or login screen is not ready, it keeps waiting and exposes that state to the host. If breaks are disabled while the player is already logged out for a logout break, return handling remains active so login can still be attempted.

For Zulrah, the settings enable or disable smart breaks from the Zulrah config, use active mouse mode, disable profile overlay and global break-status writes, disable camera nudges, and set the parking side to `NONE`. The script calls the profile's safe-break path at Ferox between trips, so it does not start a break during combat.

### Camera behavior

The reusable profile can set startup zoom and choose a random pitch in a configured range after login. It can also nudge the camera subtly left or right at randomized intervals during active work. These options are disabled in the Zulrah integration so camera control remains with the task; Zulrah itself sets zoom to 100 during runtime initialization.

### Optional profile overlay

When enabled, the compact overlay shows session runtime, next-break countdown or state, mouse mode, parking edge, and optional host-provided rows. The host can set its title and status. It is optional so scripts can avoid duplicating or crowding their own overlays.

### Integration boundary

The host owns the scheduler and calls the profile's tick with two signals: whether the current moment is safe to begin a longer break and whether the task has a safe idle opportunity. The profile indicates when normal task work should pause for breaks or logout handling. Task-specific combat, pathing, target selection, banking, and safety policy remain in the plugin.


## Rotation overlay, prayer clicks and breaks (1.10.7)

Enable **Show Zulrah rotation helper** to display current/next phases, recommended stand tiles, prayer indicators, the phase countdown, instance timer and observed clouds. This checkbox is off by default. Overlay tiles are recommendations only; the existing combat movement logic remains unchanged. Helper graphics/models are adapted from Microbot-Hub Zulrah (Syntax; originally Owain van Brakel).

Prayer changes open the prayer book using the configured hotkey, falling back to a tab switch when no hotkey is bound. The script clicks the visible prayer button through BaseProfileDro and restores the previous tab. A missing, hidden or offscreen prayer button is retried without a generic screen-sized click fallback.

The complete BaseProfileDro remains in use. Mouse activity is ACTIVE and mouse speed is High. Parking and native offscreen/random movement are disabled; inventory glances are not added. Its own smart-break manager handles AFK and logout breaks, automatic login and post-login settling. Breaks begin only at Ferox between trips, never during a fight.

Expand **Smart breaks** in the DroZulrah config for these options:

| Option | Default |
| --- | --- |
| Enable smart breaks | On |
| Minimum/maximum interval (minutes) | 20 / 140 |
| Logout break chance (%) | 100 |
| Minimum/maximum AFK duration (minutes) | 2 / 6 |
| Minimum/maximum logout duration (minutes) | 5 / 40 |
| Post-login settle (seconds) | 20 |

A logout chance of 0 selects AFK breaks only; 100 selects logout breaks only; intermediate values mix both. Maximum intervals/durations are clamped to at least their respective minimum. Restart the plugin after changing break settings to apply them to BaseProfileDro. These defaults match the previous BaseProfileDro policy.

The overlay, visible prayer clicks and new break controls have compile/focused-test validation. They have not yet been confirmed in a live fight; earlier gameplay results apply to the working baseline.


## Hub lifecycle metadata (1.10.8)

DroZulrah is explicitly marked as an external Hub plugin and disabled by default, allowing installed-plugin refresh, update and uninstall to recognize the loaded instance. The displayed name remains **[Dro] Zulrah**, with its prefix defined in the plugin-local PluginConstants. Author and catalog-image metadata are included, with icon/card assets in this documentation directory. The startup build label comes from the descriptor version.

The minimum client version is **2.6.26**, matching the current verified client. Compilation, JAR packaging and all 41 focused DroZulrah tests passed in Microbot-Hub. This metadata update does not change combat, travel, mouse or break behavior. The known full-Hub CI issue in unrelated plugins is unchanged.
