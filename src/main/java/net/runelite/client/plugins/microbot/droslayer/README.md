# Dro Slayer

With **Bank for Cannonballs** and Auto Banking enabled, **Auto Buy Cannonballs** (default on) buys steel cannonballs when bank ammunition stock is exhausted. The built-in walker (in `walker/`, ported from Efficient Walker 1.12.69) handles the GE trip. Purchases are 2,000–3,000 when affordable, or 1,000 with a smaller coin balance, at a market reference plus 20%. Insufficient funds and iron accounts retain the existing optional cannon fallback. Buying is skipped with fewer than ten task kills remaining. Only this visit's offer is collected; existing cannonball offers prevent a new purchase. Pending offers time out after three minutes and are left for manual collection, with another purchase attempt held for thirty minutes. Extra purchase coins are banked before resuming. GE interaction requires in-client verification.

Magic axes (Krystilia) routes to the user-verified hut tile `(3191, 3960, 0)`. The task requires one carried lockpick and 23 Thieving. A knife is added through the existing task-supply overlay when the equipped weapon has no slash attack bonus; the normal full-inventory space handling applies.

The overlay uses the Firecape card style with a silver grey border. Regular Slayer shows the selected master’s portrait and DRO’s Slayer with charcoal, grey and yellow-gold colours; Krystilia shows its portrait and DRO’s Wildy Slayer with the silver grey theme. The state appears beneath the title; the version is logged once on startup. The top-right minus/plus collapses the card to its logo, title and state header or expands it. Runtime, points, XP, XP/hr, walker, task, remaining and location are retained. Escapes/deaths appears only for Krystilia.

Black knights are non-cannon Wilderness tasks at every listed location, including when a task profile requests a cannon.

Preferred Wilderness spiders location: user-verified cannon tile `(3169, 3886, 0)` north-west of Lava Dragon Isle.

Krystilia portrait: [OSRS Wiki](https://oldschool.runescape.wiki/w/File:Krystilia_chathead.png), Jagex game imagery used by the wiki with permission.

Both overlay modes use a lighter translucent background, brighter labels and bold status text.

Krystilia loadout resolution never falls back to regular Slayer task profiles or the regular Default setup. Select a dedicated Wilderness setup. Enabling Wilderness Burst Dust devils uses only `burstwildy`; if missing, the selected Wilderness setup is used without bursting (or departure is held if that is unavailable). Failed Ancient spellbook preparation may use only the selected Wilderness setup, or holds if unavailable. Regular Slayer resolution is unchanged.

The expanded and minimized overlay portrait follows the selected Slayer master immediately. Portraits for every supported master are bundled locally from the OSRS Wiki (`/images/<Master>_chathead.png`); Jagex game imagery. All non-Wilderness masters retain the yellow-gold theme.

The collapse/expand button listens to real canvas events before the game input hooks, including consumed input, while consuming only its own button click sequence. It never enables blocked game input. Listeners detach on plugin shutdown. Fonts use regular RuneScape weight and the silver lettering is softened to grey.

The overlay labels Chaos druids by location: Chaos druid for ordinary locations and Elder Chaos druid for the elder location; assignment detection keeps the canonical task name.

Wilderness banking after a living escape preserves a known deployed cannon and its world. It replenishes ammunition toward 350 without trying to withdraw the deployed parts, then resumes cannoning on return.

Cannon pickup approaches the saved site, suppresses auto-retaliate while retrieving, and requires all four parts before reporting success. Bounded failed retrieval clears the usable deployment record and logs its old tile/world; later tasks cannot mistake that abandoned cannon for their own available supply.

### Missing cannon fallback (1.3.64)
Pickup retains priority over refill, combat and looting until all four parts are observed.
The saved deployment tile/world drives retrieval; ordinary pickup disables retaliation
and reserves space for the parts and returned ammunition. Player escapes retain priority.
If retrieval fails, a separate watchdog waits 36 minutes from that failure (25 minutes
until breakage, ten more to disappear, plus a margin). It starts only at the next open
bank, including an escape bank visit; the timer never initiates a banking trip.
After checking for parts and reserving five slots, it visits Nulodion through door 3
at (3015,3453,0), selecting only “I've lost my cannon.” Reclaim requires inventory
confirmation before normal task banking/restocking resumes. Attempts are limited to
five minutes, with ten-minute backoff and three attempts maximum. The pending watchdog
survives task changes, but resets when the script is restarted. Recorder-confirmed door,
NPC 1400 and dialogue do not constitute live verification of the automated route.
Timing reference: https://oldschool.runescape.wiki/w/Dwarf_multicannon#Decay

When the bank is out of Super combat potion(4), the runtime inventory overlay may substitute
(3), then (2), respecting available quantities. The saved setup is unchanged; (1) is not included.
