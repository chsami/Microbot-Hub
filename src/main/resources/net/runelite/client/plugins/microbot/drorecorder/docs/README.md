# DroRecorder

The plugin is disabled by default. Manual recording starts when the plugin is enabled
with **Automatic arena recording** off. Output remains in
`~/.runelite/dro-recorder/<recording>/session.jsonl.gz` with discrete events mirrored
to `events.jsonl.gz`. Gzip compression is enabled by default and is lossless;
stream readers can decompress it without loading a whole recording into memory.
Disable compression in settings to use plain `.jsonl` on the next recording.

## Independent arena capture

Enable **Automatic arena recording** in the local Recorder settings to opt in.
Recorder can enable itself and wait for Inferno, Colosseum, or Fight Caves entry.
Enabling Recorder manually also arms it immediately.

Recorder detects the instance's template tiles independently of any arena scripts.
A session starts on entry, survives loading, human takeover, and script pause/stop,
and closes on local-player death, final-boss death (Zuk/Sol/Jad), two confirmed
outside game ticks, or logout. Zero HP on a reliable game tick is a death fallback.
Death/completion is captured before finalization; the same arena visit cannot
immediately create another recording. Recorder rearms after confirmed exit/logout,
ready for the next attempt.
Explicitly disabling Recorder also closes it. The Recorder pause hotkey remains
available for an intentional recording pause.
Automatic sessions always use full capture defaults and gzip, under
`inferno-YYYYMMDD-HHMMSS-SSS-<unique>/` or `colosseum-.../` or `firecape-.../`.
There is no size cap or automatic retention cleanup.

## Colosseum modifiers

Each change of a Colosseum modifier client varbit writes `colosseum_modifier_changed`
with the varbit, its modifier name, the new value and a `modifiers` snapshot of every
tracked value: `selected`, each modifier's tier (`blasphemy`, `solarflare`, `doom`, ...)
and `doomPoints`. Every `Wave: N` game message adds a `colosseum_modifiers` snapshot
for that wave while any value is non-zero, so Inferno recordings gain nothing.
`tools/colosseum_modifiers.py <recording>...` prints offered modifiers, Set picks
(server command or click), tiers, Solar Flare hits and peak Doom per wave. Recordings
made before these events derive offers and tiers from snapshot widgets and Set picks.

## Essential record reserve and queue

Version 1.2.0 / schema 1.4 reserves 1,024 of the 4,096 queued data slots for
essential records. Scene-object spawn/despawn and sampled mouse movement cannot
consume that reserve. A full queue evicts disposable noise before rejecting
combat, prayer, input, lifecycle or snapshot data. Admission never waits on disk;
memory remains bounded. Sustained essential-only overload can still lose data.
The writer emits `recording_gap` records with counts, types and affected tick/sequence
bounds without relying on space in the capture queue. Excessive range groups are
explicitly marked `rangesCompacted`; they are never presented as precise individual
gaps. `session_stop.essentialRecordsLost` distinguishes losses of essential evidence.

## Recorded data

Tick snapshots (`tick`) capture:
- Local player position (world, local, scene), animation, graphic, stance, interacting actor, orientation, health, run energy, spec, prayer points.
- Equipment slots with item IDs, quantities, names.
- Inventory slots with item IDs, quantities, names.
- Active prayers and quick-prayer state.
- Camera pitch, yaw, zoom, and viewport state.
- LOS matrices and tile visibility around the player.
- Looting bag banking context.

Event records:
- `combat_animation`: Casts, hits, and attacks attributed by `DroRecorderAttack`.
- `spotanim`: Graphic spawns and spots.
- `projectile`: Projectile launch, target, duration, height, and coordinates.
- `chat`: In-game messages, split chat, wave counters.
- `actor_death`: Exact actor death ticks.
- `menu_click`: Every menu action, opcode, widget ID, mouse click coordinate.
- `mouse_move`, `mouse_drag`, `mouse_press`, `mouse_release`, `mouse_wheel`: Cursor position, button modifiers, click count. While an arena server session is connected, mouse sampling captures every single move vector without throttling.
- `network_ping`: Round-trip latency to the game world every 5 seconds.
- `window_focus` / `canvas_focus`: Focus gained/lost transitions.
- `annotation`: Tactical markers via F7 (SAFESPOT), F8 (MISTAKE), F9 (IMPORTANT).
- Diagnostic records: `arena_command`, `arena_state_sent`, `arena_safety_net`, `arena_server_ping`, `arena_input_command`, `firecape_hit`, etc.
