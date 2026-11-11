package net.runelite.client.plugins.microbot.droslayer.walker.support;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.TeleportationItem;
import net.runelite.client.plugins.microbot.shortestpath.TileCounter;
import net.runelite.client.plugins.microbot.shortestpath.TileStyle;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@ConfigGroup(ShortestPathPlugin.CONFIG_GROUP)
@ConfigInformation("Toggle walking pauses/resumes manual walking. Clear current path removes the destination (default: Ctrl + X).")
public interface ShortestPathConfig extends Config {
    @ConfigSection(
            name = "Settings",
            description = "Options for the pathfinding",
            position = 0
    )
    String sectionSettings = "sectionSettings";

    @ConfigItem(
            keyName = "avoidWilderness",
            name = "Avoid wilderness",
            description = "Whether the wilderness should be avoided if possible<br>" +
                    "(otherwise, will e.g. use wilderness lever from Edgeville to Ardougne)",
            position = 0,
            section = sectionSettings
    )
    default boolean avoidWilderness() {
        return true;
    }

    @ConfigItem(
            keyName = "avoidDangerousNpcs",
            name = "Avoid dangerous NPCs",
            description = "Route around tiles next to aggressive NPCs that attack you while passing<br>" +
                    "(e.g. undead trees at Draynor Manor). A penalty, not a block, so chokepoints still work.",
            position = 1,
            section = sectionSettings
    )
    default boolean avoidDangerousNpcs() {
        return true;
    }

    @ConfigItem(
            keyName = "useAgilityShortcuts",
            name = "Use agility shortcuts",
            description = "Whether to include agility shortcuts in the path.<br>" +
                    "You must also have the required agility level",
            position = 2,
            section = sectionSettings
    )
    default boolean useAgilityShortcuts() {
        return true;
    }

    @ConfigItem(
            keyName = "useGrappleShortcuts",
            name = "Use grapple shortcuts",
            description = "Whether to include crossbow grapple agility shortcuts in the path.<br>" +
                    "You must also have the required agility, ranged and strength levels",
            position = 3,
            section = sectionSettings
    )
    default boolean useGrappleShortcuts() {
        return false;
    }

    @ConfigItem(
            keyName = "useBoats",
            name = "Use boats",
            description = "Whether to include small boats in the path<br>" +
                    "(e.g. the boat to Fishing Platform)",
            position = 4,
            section = sectionSettings
    )
    default boolean useBoats() {
        return true;
    }

    @ConfigItem(
            keyName = "useCharterShips",
            name = "Use charter ships",
            description = "Whether to include charter ships in the path",
            position = 6,
            section = sectionSettings
    )
    default boolean useCharterShips() {
        return true;
    }

    @ConfigItem(
            keyName = "useShips",
            name = "Use ships",
            description = "Whether to include passenger ships in the path<br>" +
                    "(e.g. the customs ships to Karamja)",
            position = 7,
            section = sectionSettings
    )
    default boolean useShips() {
        return true;
    }

    @ConfigItem(
            keyName = "useFairyRings",
            name = "Use fairy rings",
            description = "Whether to include fairy rings in the path.<br>" +
                    "You must also have completed the required quests or miniquests",
            position = 8,
            section = sectionSettings
    )
    default boolean useFairyRings() {
        return true;
    }

    @ConfigItem(
            keyName = "useGnomeGliders",
            name = "Use gnome gliders",
            description = "Whether to include gnome gliders in the path",
            position = 9,
            section = sectionSettings
    )
    default boolean useGnomeGliders() {
        return true;
    }

    @ConfigItem(
            keyName = "useMinecarts",
            name = "Use minecarts",
            description = "Whether to include minecarts in the path<br>" +
                    "(e.g. the Keldagrim and Lovakengj minecart networks)",
            position = 10,
            section = sectionSettings
    )
    default boolean useMinecarts() {
        return true;
    }

    @ConfigItem(
            keyName = "useSpiritTrees",
            name = "Use spirit trees",
            description = "Whether to include spirit trees in the path",
            position = 11,
            section = sectionSettings
    )
    default boolean useSpiritTrees() {
        return true;
    }

    @ConfigItem(
            keyName = "useTeleportationItems",
            name = "Use teleportation items",
            description = "Whether to include teleportation items from the player's inventory and equipment.<br>" +
                    "Options labelled (perm) only use permanent non-charge items.",
            position = 12,
            section = sectionSettings
    )
    default TeleportationItem useTeleportationItems() {
        return TeleportationItem.INVENTORY;
    }

    @ConfigItem(
            keyName = "useTeleportationLevers",
            name = "Use teleportation levers",
            description = "Whether to include teleportation levers in the path<br>" +
                    "(e.g. the lever from Edgeville to Wilderness)",
            position = 13,
            section = sectionSettings
    )
    default boolean useTeleportationLevers() {
        return true;
    }

    @ConfigItem(
            keyName = "useTeleportationPortals",
            name = "Use teleportation portals",
            description = "Whether to include teleportation portals in the path<br>" +
                    "(e.g. the portal from Ferox Enclave to Castle Wars)",
            position = 14,
            section = sectionSettings
    )
    default boolean useTeleportationPortals() {
        return true;
    }

    @ConfigItem(
            keyName = "useTeleportationSpells",
            name = "Use teleportation spells",
            description = "Whether to include teleportation spells in the path",
            position = 15,
            section = sectionSettings
    )
    default boolean useTeleportationSpells() {
        return true;
    }

    @ConfigItem(
            keyName = "useTeleportationMinigames",
            name = "Use teleportation to minigames",
            description = "Whether to include teleportation to minigames/activities/grouping in the path<br>" +
                    "(e.g. the Nightmare Zone minigame teleport). These teleports share a 20 minute cooldown.",
            position = 16,
            section = sectionSettings
    )
    default boolean useTeleportationMinigames() {
        return false;
    }

    @ConfigItem(
            keyName = "useWildernessObelisks",
            name = "Use wilderness obelisks",
            description = "Whether to include wilderness obelisks in the path",
            position = 17,
            section = sectionSettings
    )
    default boolean useWildernessObelisks() {
        return true;
    }

    @ConfigItem(
            keyName = "useNpcs",
            name = "Use npcs",
            description = "Whether to include npc transports in the path<br>(e.g. Tree gnome village maze or Lumbridge cellar)",
            position = 18,
            section = sectionSettings
    )
    default boolean useNpcs() {
        return true;
    }

    @ConfigItem(
            keyName = "useQuetzals",
            name = "Use quetzals",
            description = "Whether to include quetzals in the path.<br>",
            position = 19,
            section = sectionSettings
    )
    default boolean useQuetzals() {
        return true;
    }

    @ConfigItem(
            keyName = "useMagicCarpets",
            name = "Use Magic Carpets",
            description = "Whether to include magic carpets in the path.<br>",
            position = 20,
            section = sectionSettings
    )
    default boolean useMagicCarpets() {
        return true;
    }

    @ConfigItem(
            keyName = "useHotAirBalloons",
            name = "Use Hot Air Balloons",
            description = "Whether to include hot air balloons in the path.",
            position = 21,
            section = sectionSettings
    )
    default boolean useHotAirBalloons() {
        return false;
    }

    @ConfigItem(
            keyName = "useMagicMushtrees",
            name = "Use Magic Mushtrees",
            description = "Whether to include magic mushtrees in the path.",
            position = 22,
            section = sectionSettings
    )
    default boolean useMagicMushtrees() {
        return true;
    }

    @ConfigItem(
            keyName = "useSeasonalTransports",
            name = "Use Seasonal Transports",
            description = "Whether to include seasonal League transports (e.g. Map of Alacrity) in the path. League worlds only.",
            position = 23,
            section = sectionSettings
    )
    default boolean useSeasonalTransports() {
        return false;
    }

    @ConfigItem(
            keyName = "usePoh",
            name = "Use Player-owned-house Teleports",
            description = "Whether to include teleportation through the PoH",
            position = 24,
            section = sectionSettings
    )
    default boolean usePoh() {
        return true;
    }

    @ConfigItem(
            keyName = "usePortalNexus",
            name = "Use Portal Nexus",
            description = "Include Portal Nexus destinations saved in PoH Web Config. Requires Player-owned-house Teleports.",
            position = 24,
            section = sectionSettings
    )
    default boolean usePortalNexus() {
        return true;
    }

    @ConfigItem(
            keyName = "distanceBeforeUsingTeleports",
            name = "Teleport distance",
            description = "Distance before using a teleport<br>" +
                    "(This is to avoid using teleports when you are to close",
            position = 27,
            section = sectionSettings
    )
    default int distanceBeforeUsingTeleport() {
        return 20;
    }

    @Units(
            value = Units.TICKS
    )
    @Range(
            min = 1,
            max = 30
    )
    @ConfigItem(
            keyName = "calculationCutoff",
            name = "Calculation cutoff",
            description = "The cutoff threshold in number of ticks (0.6 seconds) of no progress being<br>" +
                    "made towards the path target before the calculation will be stopped",
            position = 32,
            section = sectionSettings
    )
    default int calculationCutoff()
    {
        return 5;
    }

    @ConfigSection(
            name = "Display",
            description = "Options for displaying the path on the world map, minimap and scene tiles",
            position = 1
    )
    String sectionDisplay = "sectionDisplay";

    @ConfigSection(
            name = "Colours",
            description = "Colours for the path map, minimap and scene tiles",
            position = 2
    )
    String sectionColours = "sectionColours";

    @ConfigSection(
            name = "Debug Options",
            description = "Various options for debugging",
            position = 3,
            closedByDefault = true
    )
    String sectionDebug = "sectionDebug";

    @ConfigSection(
            name = "Advanced Options",
            description = "Advanced pathfinding and transport settings",
            position = 4,
            closedByDefault = false
    )
    String sectionAdvanced = "sectionAdvanced";

        @ConfigItem(
                keyName = "preferTransportToTarget",
                name = "Prefer transport to target",
                description = "Whether to prefer using transports to reach the target instead of walking.<br>" +
                        "This will only apply when 'Walk with banked transports' is enabled.",
                position = 4,
                section = sectionAdvanced
        )
        default boolean preferTransportToTarget() {
                return false;
        }

    @ConfigItem(
            keyName = "maxSimilarTransportDistance",
            name = "Max similar transport distance (tiles)",
            description = "Maximum distance between spell and consumable item teleport destinations to prefer spells over items.<br>" +
                    "Only applies when 'Walk with banked transports' is enabled. Set to 0 to disable filtering.",
            position = 5,
            section = sectionAdvanced
    )
    @Range(min = 0, max = 100)
    default int maxSimilarTransportDistance() {
        return 0;
    }

	@ConfigSection(
			name = "Spirit tree teleports",
			description = "Toggle which spirit tree destinations to use",
			position = 5,
			closedByDefault = true
	)
	String sectionSpiritTrees = "sectionSpiritTrees";

	@ConfigItem(
			keyName = "spiritTreeEtceteria",
			name = "Etceteria",
			description = "Use the spirit tree teleport to Etceteria",
			position = 0,
			section = sectionSpiritTrees
	)
	default boolean spiritTreeEtceteria() {
		return true;
	}

	@ConfigItem(
			keyName = "spiritTreeBrimhaven",
			name = "Brimhaven",
			description = "Use the spirit tree teleport to Brimhaven",
			position = 1,
			section = sectionSpiritTrees
	)
	default boolean spiritTreeBrimhaven() {
		return true;
	}

	@ConfigItem(
			keyName = "spiritTreePortSarim",
			name = "Port Sarim",
			description = "Use the spirit tree teleport to Port Sarim",
			position = 2,
			section = sectionSpiritTrees
	)
	default boolean spiritTreePortSarim() {
		return true;
	}

	@ConfigItem(
			keyName = "spiritTreeHosidius",
			name = "Hosidius",
			description = "Use the spirit tree teleport to Hosidius",
			position = 3,
			section = sectionSpiritTrees
	)
	default boolean spiritTreeHosidius() {
		return true;
	}

	@ConfigItem(
			keyName = "spiritTreeFarmingGuild",
			name = "Farming Guild",
			description = "Use the spirit tree teleport to the Farming Guild",
			position = 4,
			section = sectionSpiritTrees
	)
    default boolean spiritTreeFarmingGuild() {
        return true;
    }

    @ConfigSection(
            name = "Developer",
            description = "Optional — most users can ignore.",
            position = 100
    )
    String sectionDeveloper = "sectionDeveloper";

	@ConfigItem(
			keyName = "plannerSelectionMode",
			name = "Planner rollout mode",
			description = "Local is the production default. Shadow compares the pinned upstream planner. "
					+ "The F2P canary selects only semantically matching upstream routes and automatically "
					+ "falls back to local; members routes remain local.",
			position = 3,
			section = sectionDeveloper,
			hidden = true
	)
	default PlannerSelectionMode plannerSelectionMode() {
		return PlannerSelectionMode.LOCAL;
	}
}
