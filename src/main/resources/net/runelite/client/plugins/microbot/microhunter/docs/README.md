# AutoHunter

Builds and maintains a compact box-trap layout, filling the Hunter-level trap quota before normal maintenance. Traps are reset in place and their complete rebuild is confirmed before the next interaction. Fallen traps are recovered.

Gameplay testing has covered red chinchompas only. Other box trapping should work with the right setup: start in a suitable area with box traps in inventory and leave **Center on best spawn** off. Spawn centering recognizes red chinchompas only. The extra Wilderness trap is not supported.

The initial session tile remains the placement origin across login and world changes. Hunting radius bounds placement, spawn candidates, and competing-hunter scans. Optional occupied-world avoidance scans after login and prefers eligible Australian worlds.

Prepared trap hovering follows the trap during player movement. Config tooltips explain reaction delays and mouse behavior. Before a scheduled break, owned caught traps are checked, other deployed traps dismantled, and fallen traps taken before releasing the Break Handler lock.

Requires a Microbot client exposing the existing Hunter plugin through its public dependency module. The client companion change must ship before this Hub update is released.
