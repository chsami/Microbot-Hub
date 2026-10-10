package net.runelite.client.plugins.microbot.microhunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("AutoHunter")
@ConfigInformation("Builds and maintains box traps in a compact five-dot layout around the initial session tile. "
        + "Start in your chosen hunting area with box traps in inventory. For other box-trap creatures, leave Center on best spawn OFF; "
        + "spawn centering is specific to red chinchompas. Other areas have not been live-validated. Uses the normal Hunter-level trap limit; "
        + "the extra Wilderness trap is not supported.")
public interface AutoHunterConfig extends Config {
    @ConfigItem(
            position = 1,
            keyName = "huntingRadius",
            name = "Hunting radius",
            description = "Distance in tiles from the initial session tile used for placement, spawn selection, and competing-player/trap scans. Does not change five-dot trap spacing. Default: 6."
    )
    @Range(min = 2, max = 12)
    default int huntingRadius() {
        return 6;
    }

    @ConfigItem(
            position = 2,
            keyName = "useSpawnRing",
            name = "Center on best spawn",
            description = "Red chinchompas only: learn respawn tiles and center a new five-dot layout on the best candidate within the hunting radius. Enable in a red-chinchompa area. Waits for a verified candidate; does not move an established layout. Leave OFF for other box-trap creatures to use the initial session tile."
    )
    default boolean useSpawnRing() {
        return false;
    }

    @ConfigItem(
            position = 3,
            keyName = "humanizerEnabled",
            name = "Humanizer",
            description = "Use short varied reaction delays, randomized trap pre-hover points, small mouse corrections, and occasional idle wandering. Trap confirmation still waits for the observed action to finish."
    )
    default boolean humanizerEnabled() {
        return true;
    }

    @ConfigItem(
            position = 4,
            keyName = "avoidOccupiedWorlds",
            name = "Avoid occupied worlds",
            description = "After login or a world change, scan within the hunting radius for players and existing box traps. If occupied, try eligible Australian worlds of the same membership type, up to five attempts."
    )
    default boolean avoidOccupiedWorlds() {
        return true;
    }
}
