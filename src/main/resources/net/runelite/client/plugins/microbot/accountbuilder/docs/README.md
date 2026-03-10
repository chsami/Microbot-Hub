# AccountBuilder Plugin

A free, open-source automated account builder for OSRS via the Microbot RuneLite client.

## Features

- One-click account progression from a fresh account
- Per-account behavioral randomization (timing, idle chance, breaks)
- Ordered task pipeline: quests → melee → ranged → prayer → magic
- Clean overlay showing current task, progress, runtime, and skill levels

## Task Order

| # | Task | Target |
|---|------|--------|
| 1 | Waterfall Quest | Free Attack/Strength XP |
| 2 | Animal Magnetism | Ava's device reward |
| 3 | Train Attack | Level 30 |
| 4 | Train Strength | Level 40 |
| 5 | Train Defence | Level 40 |
| 6 | Train Ranged | Level 40 |
| 7 | Train Prayer | Level 43 |
| 8 | Train Magic | Level 55 |

## How to Use

1. Log in to your OSRS account
2. Enable the **[MB] AccountBuilder** plugin from the plugin panel
3. The script will load or create a behavioral profile for your account name
4. The overlay shows live progress — no configuration required to start

## Configuration

| Option | Description |
|--------|-------------|
| Combat Training Style | Preferred melee attack style (Attack / Strength / Defence / Controlled) |
| Enable AFK Breaks | Randomized AFK pauses to appear human-like |
| Log Level | How much to log to in-game chat (OFF / INFO / DEBUG) |

## Notes

- Quest tasks are currently stubs — melee training tasks are functional
- Do not run on an account you cannot afford to lose
- Mouse movement uses the Microbot built-in system (not reimplemented)
