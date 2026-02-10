# Slayer Plugin

An automated slayer plugin that handles the full slayer loop: getting tasks from a master, banking for supplies, traveling to task locations, fighting monsters, looting, and returning for new tasks.

## Features

### Task Management
- **Auto Get Task**: Automatically walks to your chosen slayer master and requests a new assignment
- **Auto Skip Tasks**: Skip unwanted tasks from a configurable list (costs 30 slayer points)
- **Auto Block Tasks**: Permanently block tasks from a configurable list (costs 100 slayer points)
- **Point Safety**: Configurable minimum point reserves prevent accidentally spending all your points

### Combat
- **Auto Combat**: Automatically targets and attacks slayer task monsters within a configurable radius
- **Combat Styles**: Supports melee, ranged, magic, burst, and barrage via task profiles
- **Superior Priority**: Detects and prioritizes superior slayer monster spawns (requires "Bigger and Badder" unlock)
- **Prayer Management**: Multiple prayer flicking styles (Always On, Lazy Flick, Perfect Lazy Flick, Mixed Lazy Flick)
- **Offensive Prayers**: Activates Piety/Rigour/Augury based on combat style
- **AOE Dodging**: Automatically dodges AOE projectile attacks (e.g., dragon poison pools)
- **Combat Potions**: Automatically drinks stat-boosting potions
- **World Hopping**: Hops worlds when crashed (no targets found for 20 seconds)
- **Goading Potions**: Supports goading for burst/barrage AoE stacking

### Banking
- **Auto Banking**: Banks when food or prayer potions run low
- **Configurable Thresholds**: Set food count and prayer dose thresholds to trigger banking
- **Inventory Setups Integration**: Uses Microbot Inventory Setups to load correct gear per task (via task profiles)

### POH (Player Owned House)
- **Rejuvenation Pool**: Uses POH pool to restore HP, Prayer, and Run energy
- **Multiple Teleport Methods**: House Tab, Teleport Spell, Construction Cape, Max Cape
- **Flexible Timing**: Use before banking, after tasks, or when HP drops below a threshold
- **Spellbook Swap**: Handles spellbook swapping via lectern for tasks requiring Ancient spells

### Cannon
- **Auto Cannon**: Places, reloads, and picks up your dwarf multicannon
- **Predefined Spots**: 40+ optimized cannon placement spots for supported tasks
- **Cannonball Threshold**: Banks when cannonballs run low
- **Task Filtering**: Optionally restrict cannon use to specific tasks only

### Looting
- **Loot Styles**: Mixed (list + price), Item List only, or GE Price Range only
- **Price Filtering**: Set minimum and maximum GE value thresholds
- **Item Lists**: Include and exclude lists with wildcard support (e.g., `*clue scroll*`, `*bones`)
- **Coins & Stackables**: Configurable coin stack minimums, arrow stacks, rune stacks
- **Bones & Ashes**: Auto-pickup and bury bones or scatter demonic ashes
- **Untradables**: Loot clue scrolls, keys, and other untradable drops
- **Force Loot**: Pick up items even while in combat
- **Eat for Space**: Eat food to make room for valuable loot
- **Delayed Looting**: Wait to let items pile up before looting
- **High Alch**: Alch configured items from inventory while fighting

### Safety
- **Death Handling**: On player death, immediately disables prayers, stops all scripts, and logs out to pause the gravestone timer
- **Break Handler**: Integrates with Microbot break handler; only allows breaks during safe states (Idle, At Location)

### Overlay
- **Slayer Points**: Current point total
- **State**: Current plugin state with color-coded status
- **Task**: Active slayer task name
- **Remaining**: Monsters left to kill
- **Location**: Current task location name

## Configuration

### General Settings

| Setting | Default | Description |
|---------|---------|-------------|
| Enable Plugin | Enabled | Toggle the slayer plugin on/off |
| Auto Travel | Enabled | Automatically travel to slayer task location |
| Slayer Master | Duradel | Which slayer master to get tasks from |
| Get New Task | Enabled | Automatically get a new task when current task is complete |

### Task Management

| Setting | Default | Description |
|---------|---------|-------------|
| Auto Skip Tasks | Disabled | Automatically skip tasks on the skip list (costs 30 points) |
| Skip Task List | Empty | Comma-separated list of task names to skip (e.g., `Black demons, Hellhounds`) |
| Min Points to Skip | 100 | Minimum slayer points required before skipping (keeps a reserve) |
| Auto Block Tasks | Disabled | Automatically block tasks on the block list (costs 100 points, permanent) |
| Block Task List | Empty | Comma-separated list of task names to permanently block (e.g., `Spiritual creatures, Drakes`) |
| Min Points to Block | 150 | Minimum slayer points required before blocking (keeps a reserve) |

### Banking

| Setting | Default | Description |
|---------|---------|-------------|
| Auto Banking | Enabled | Automatically bank when supplies are low |
| Food Threshold | 3 | Bank when food count drops below this amount (0 to disable) |
| Prayer Potion Threshold | 4 | Bank when prayer potion/super restore doses drop below this amount (0 to disable) |

### POH (House)

| Setting | Default | Description |
|---------|---------|-------------|
| Use POH Pool | Disabled | Use POH rejuvenation pool to restore HP/Prayer/Run energy |
| Teleport Method | House Tab | How to teleport to your house (House Tab, Spell, Construction Cape, Max Cape) |
| Use Before Banking | Enabled | Use POH pool to restore before going to bank (saves supplies) |
| Use After Task | Enabled | Use POH pool after completing a task (before getting new task) |
| Restore Below HP % | 50 | Use POH when HP drops below this percentage (0 to only restore at task end) |

### Combat

| Setting | Default | Description |
|---------|---------|-------------|
| Auto Combat | Enabled | Automatically attack slayer task monsters |
| Attack Radius | 10 | Maximum tile distance to search for monsters |
| Prioritize Superiors | Enabled | Always attack superior slayer monsters first when they spawn |
| Eat at HP % | 50 | Eat food when health drops below this percentage |
| Prayer Style | Off | Prayer management style: Off, Always On, Lazy Flick, Perfect Lazy Flick, Mixed Lazy Flick |
| Drink Prayer At | 20 | Drink prayer potion when prayer points drop below this amount |
| Use Combat Potions | Disabled | Drink combat potions (attack, strength, defence, ranging, magic) |
| Use Offensive Prayers | Disabled | Activate offensive prayers (Piety/Rigour/Augury) based on combat style |
| Dodge AOE Attacks | Disabled | Automatically dodge AOE projectile attacks |
| Hop When Crashed | Enabled | Hop worlds if no attackable targets found for 20 seconds |
| Hop World List | Empty | Comma-separated list of worlds to hop to (e.g., `390,391,392`). Leave empty for random members world. |

### Cannon

| Setting | Default | Description |
|---------|---------|-------------|
| Enable Cannon | Disabled | Use dwarf multicannon for tasks with predefined cannon spots |
| Cannonball Threshold | 100 | Bank when cannonball count drops below this amount |
| Cannon Tasks | Empty | Only use cannon for these tasks (comma-separated). Leave empty to cannon all supported tasks. |

### Loot

| Setting | Default | Description |
|---------|---------|-------------|
| Enable Looting | Enabled | Pick up loot from killed monsters |
| Loot Style | Mixed | How to determine what to loot: Mixed (list + price), Item List only, GE Price Range only |
| Item List | Empty | Comma-separated items to always loot. Supports `*` wildcards (e.g., `totem piece, *clue scroll*`) |
| Exclude List | Empty | Comma-separated items to NEVER loot. Supports `*` wildcards (e.g., `vial, jug, *bones`) |
| Min Loot Value | 1000 | Minimum GE value to loot (0 to disable price filtering) |
| Max Loot Value | 0 | Maximum GE value to loot (0 for no limit) |
| Loot Coins | Enabled | Pick up coin stacks |
| Min Coin Stack | 0 | Only loot coins if stack is at least this amount (0 = loot all) |
| Loot Arrows | Disabled | Pick up arrow stacks (10+ arrows) |
| Loot Runes | Disabled | Pick up rune stacks (2+ runes) |
| Loot Untradables | Enabled | Pick up untradable items (clue scrolls, keys, etc.) |
| Loot Bones | Disabled | Pick up bones from killed monsters |
| Bury Bones | Disabled | Automatically bury bones after picking them up |
| Scatter Ashes | Disabled | Pick up and scatter demonic/infernal ashes |
| Force Loot | Disabled | Loot items even while in combat |
| Only Loot My Items | Disabled | Only loot items dropped by/for you |
| Delayed Looting | Disabled | Wait before looting (lets items pile up) |
| Eat For Loot Space | Disabled | Eat food to make room for valuable loot |
| Enable High Alch | Disabled | High alch items from your inventory while fighting |
| High Alch Items | Empty | Comma-separated items to high alch. Supports `*` wildcards |
| High Alch Exclude | Empty | Comma-separated items to NEVER high alch. Supports `*` wildcards |

## Supported Slayer Masters

| Master | Combat Level | Location |
|--------|-------------|----------|
| Turael | 1 | Burthorpe |
| Spria | 1 | Draynor Village |
| Mazchna | 20 | Canifis |
| Vannaka | 40 | Edgeville Dungeon |
| Chaeldar | 70 | Zanaris |
| Nieve / Steve | 85 | Tree Gnome Stronghold |
| Duradel / Kuradal | 100 | Shilo Village |

## Task Profiles (JSON)

The plugin uses a per-task JSON profile system to configure task-specific settings like gear, combat style, prayer, location, cannon usage, and potions.

### File Location

Profiles are stored at: `~/.runelite/slayer-profiles.json`

On first launch, the plugin generates a default file with 40+ preconfigured task profiles. You can edit this file to customize behavior for each task.

### Profile Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `setup` | string | - | Inventory setup name to load from Microbot Inventory Setups |
| `style` | string | `"melee"` | Combat style: `melee`, `ranged`, `magic`, `burst`, `barrage` |
| `prayer` | string | `"none"` | Protection prayer: `pmelee`, `pmage`, `prange`, `none` |
| `cannon` | boolean | `false` | Whether to use cannon for this task |
| `goading` | boolean | `false` | Use goading potions (auto-enabled for burst/barrage) |
| `antipoison` | boolean | `false` | Bring antipoison potions |
| `antivenom` | boolean | `false` | Bring antivenom potions |
| `superAntifire` | boolean | `false` | Bring super antifire potions (for dragon tasks) |
| `variant` | string | - | Specific monster to target (e.g., `"kalphite soldier"` for kalphite tasks) |
| `location` | string | - | Preferred location key (e.g., `"catacombs abyssal demons"`) |
| `cannonLocation` | string | - | Location to use when cannoning (overrides `location`) |
| `useSpecial` | boolean | `false` | Use special attack |
| `specialThreshold` | int | `50` | Min spec energy before using special (0-100) |
| `minStackSize` | int | `3` | Min monsters to stack before casting burst/barrage |

### Example Profile

```json
{
  "dust devils": {
    "setup": "burst",
    "style": "burst",
    "prayer": "pmage",
    "cannon": false,
    "goading": true,
    "minStackSize": 4,
    "location": "catacombs dust devils"
  },
  "black demons": {
    "setup": "melee",
    "style": "melee",
    "prayer": "pmelee",
    "cannon": true,
    "location": "catacombs black demons",
    "cannonLocation": "chasm black demons"
  },
  "frost dragons": {
    "setup": "dragon",
    "style": "melee",
    "prayer": "pmelee",
    "superAntifire": true,
    "location": "grimstone frost dragons"
  }
}
```

### Location Keys

Location keys map to specific WorldPoints. Examples include:
- Catacombs: `catacombs hellhounds`, `catacombs dust devils`, `catacombs nechryael`, `catacombs abyssal demons`
- Slayer Tower: `slayer tower gargoyles`, `slayer tower abyssal demons`, `slayer tower aberrant spectres`
- Stronghold Cave: `stronghold hellhounds`, `stronghold fire giants`, `stronghold abyssal demons`
- Karuulm: `karuulm wyrms`, `karuulm drakes`, `karuulm hydras`
- Fremennik: `fremennik kurask`, `fremennik turoth`, `fremennik basilisk`
- Other: `chasm black demons`, `smoke devil dungeon`, `lunar isle suqahs`, `death plateau`

See the `SlayerLocation` enum for the full list of 80+ supported location keys.

## How It Works

1. **Idle** - Checks if you have a slayer task. If yes, checks skip/block lists. If no task, walks to the selected slayer master.
2. **Getting Task** - Interacts with the slayer master and clicks through dialogue to receive an assignment.
3. **Skip/Block** - If the task is on your skip or block list and you have enough points, navigates the reward shop to skip (30 pts) or block (100 pts) the task.
4. **Detecting Task** - Reads the current task, loads the matching JSON profile, determines the task location and required gear.
5. **POH Restore** - If enabled, teleports to your POH and uses the rejuvenation pool. Handles spellbook swapping if needed.
6. **Banking** - Walks to a bank, loads the inventory setup from the profile, and withdraws food, potions, and cannonballs as needed.
7. **Traveling** - Uses the Microbot walker to navigate to the task location (from profile or auto-detected).
8. **Fighting** - Attacks task monsters, manages prayer, eats food, drinks potions, handles cannon, dodges AOE attacks, and loots drops.
9. **Task Complete** - Spends a few seconds looting remaining drops, then loops back to step 1 for the next task.

## Requirements

- Microbot client version 2.1.11 or higher
- Slayer skill unlocked
- Sufficient combat level for your chosen slayer master
- Food and prayer potions in your bank
- Microbot Inventory Setups plugin configured with gear setups matching your profile names

## Tips

- **Disable "Like a Boss"**: Turn off the "Like a Boss" slayer perk. Boss task amount input dialogues are not supported and will stall the plugin.
- **Inventory Setups**: Create named gear setups (e.g., "melee", "ranged", "burst") in the Microbot Inventory Setups plugin. Your JSON profile `setup` field should match these names.
- **Cannon Setups**: When cannon is enabled for a task, the plugin looks for a `{setup}-cannon` variant (e.g., "melee-cannon") to accommodate cannonballs in your inventory.
- **Start at Bank or POH**: Start the plugin while at a bank or your POH for the smoothest first-run experience.
- **Point Reserve**: Set your min points to skip/block higher than the cost (30/100) to keep a buffer for future skips.
- **Crash Detection**: With "Hop When Crashed" enabled, the plugin hops worlds after 20 seconds of finding no attackable targets. Configure your preferred world list or leave it empty for random members worlds.
- **Break Handler**: The plugin integrates with the Microbot break handler and only pauses during safe states (Idle, At Location) to avoid breaking mid-bank or mid-travel.
- **Profile Editing**: Edit `~/.runelite/slayer-profiles.json` while the client is closed, or restart the plugin to reload changes.

## Known Limitations

- **Boss tasks not supported**: Boss variants of slayer tasks are not handled. Disable "Like a Boss" in slayer rewards.
- **Konar not supported**: Konar quo Maten is excluded because her location-specific task constraints are not implemented.
- **No death recovery**: On death, the plugin logs out to pause your gravestone timer. You must recover your items manually.
- **Quest requirements**: Some task locations may require quest completion (e.g., Mourner Tunnels for Dark Beasts). Ensure you have the required access.
- **Spellbook requirements**: Burst/barrage tasks require the Ancient spellbook. The plugin can swap via POH lectern but needs a house with a lectern.

## Disclaimer

This plugin is intended for educational purposes and personal use only. Use of automation software may violate game rules. Use at your own risk.

## Version History

**1.0.0** - Initial release
- Full slayer loop automation (task, bank, travel, fight, loot)
- 8 supported slayer masters
- 40+ predefined cannon spots
- 80+ location keys
- Per-task JSON profile system
- Prayer flicking (lazy, perfect lazy, mixed)
- AOE projectile dodging
- Superior monster prioritization
- World hopping on crash detection
- POH pool restoration with spellbook swap
- Death safety (auto-logout)
- Break handler integration
