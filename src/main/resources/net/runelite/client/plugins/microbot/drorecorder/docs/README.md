# DroRecorder

Records gameplay for later analysis: player and NPC state, line of sight and pathing,
inventory, equipment and prayers, combat, projectiles, chat, mouse and camera input,
and the tactical markers you press.

## When it records

| Plugin | Recording |
| --- | --- |
| Enabled | Records everywhere, for as long as it stays enabled, including when the client turns it back on at startup. |
| Disabled | Records nothing. |

The plugin is disabled by default. Enabling it starts a new recording; disabling it
finishes and saves that recording. The pause hotkey (Ctrl+F10) pauses and resumes
recording without disabling the plugin.

## Where recordings go and how big they are

Each recording is a folder in `~/.runelite/dro-recorder/` holding `session.jsonl.gz`,
with discrete events mirrored to `events.jsonl.gz`. Use **Open Output Folder** in the
settings to open it.

Compression (gzip, lossless) is on by default. Compressed recordings take about
**100 MB per hour** of play (80-115 MB per hour measured). Stream readers can decompress them without loading a whole
recording into memory. Turning compression off writes plain `.jsonl` from the next
recording on, which uses more disk.

There is no size cap or automatic cleanup. Delete recordings you no longer need.

## Markers

| Hotkey | Marker | When to press |
| --- | --- | --- |
| Ctrl+F7 | Safe | After you reach the safespot or anchor and the situation is stable. |
| Ctrl+F8 | Mistake | Right after a mistake or bad position, so it is not learned as correct. |
| Ctrl+F9 | Important | Right after an important tactical decision or action. |
| Ctrl+F10 | Pause/Resume | Pauses or resumes recording without disabling the plugin. |

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
- `annotation`: Tactical markers via Ctrl+F7 (SAFESPOT), Ctrl+F8 (MISTAKE), Ctrl+F9 (IMPORTANT), and Ctrl+F10 (PAUSE/RESUME).
- Diagnostic records: `arena_command`, `arena_state_sent`, `arena_safety_net`, `arena_server_ping`, `arena_input_command`, `firecape_hit`, etc.
