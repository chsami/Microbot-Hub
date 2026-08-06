# Leagues Toolkit

Quality-of-life utilities for OSRS Leagues.

## Features

### Anti-AFK
Prevents the idle-timeout logout during long AFK skilling sessions (e.g. mining with the auto-bank relic where inventory never fills and no interaction happens between rock respawns). Periodically triggers a configurable input right before the client's AFK threshold so the session stays alive indefinitely.

Configurable:
- **Input method** — random arrow key (default, most natural), backspace, or camera yaw rotation
- **Trigger buffer min/max** — how many ticks before the idle-timeout to fire input (randomized between min and max)

## Roadmap

More Leagues-focused utilities planned. Open an issue or PR with ideas.
