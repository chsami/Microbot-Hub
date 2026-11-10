package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.client.config.*;

/**
 * Efficient Walker configuration.
 */
@ConfigGroup(EfficientWalkerConfig.GROUP)
@ConfigInformation("By droplugins.")
public interface EfficientWalkerConfig extends EfficientSailingConfig
{
    String GROUP = "efficientwalker";

    @ConfigItem(keyName = "namedDestination", name = "Named destination",
        description = "Choose a destination, then press Walk to named destination or its hotkey. Selecting alone leaves your route unchanged.",
        position = 1, section = generalSection)
    default WalkerDestination namedDestination() { return WalkerDestination.NONE; }

    @ConfigItem(keyName = "walkToNamedDestination", name = "Walk to named destination",
        description = "Start Efficient Walker to the selected named destination using your normal transport settings and account requirements.",
        position = 2, section = generalSection)
    default ConfigButton walkToNamedDestination() { return new ConfigButton(); }

    @ConfigItem(keyName = "walkToNamedDestinationHotkey", name = "Walk to named destination",
        description = "Start Efficient Walker to the selected named destination. Cancel walk stops travel.",
        position = 3, section = hotkeysSection)
    default Keybind walkToNamedDestinationHotkey() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = "useMicrobotMouse", name = "Use Microbot mouse",
        description = "Use Dro mouse timing and clickbox variation through Microbot input. Minimap targets stay exact. Cancelled or stale queued clicks are discarded.",
        position = 22, section = generalSection)
    default boolean useMicrobotMouse() { return false; }


    @ConfigItem(keyName = "walkerMouseActivity", name = "Mouse activity",
        description = "Dro mouse profile while travelling: Active parks 10–15%, Balanced 40%, AFK 75% of safe mouse idle opportunities. Requires Use Microbot mouse.",
        position = 23, section = generalSection)
    default WalkerMouseProfile.Activity walkerMouseActivity() { return WalkerMouseProfile.Activity.ACTIVE; }

    @ConfigItem(keyName = "walkerMouseParkSide", name = "Mouse parking edge",
        description = "Dro mouse parking edge during safe travel after a canvas click. Random chooses one edge per login. None keeps the mouse on screen. Minimap clicks never park. Requires Use Microbot mouse.",
        position = 24, section = generalSection)
    default WalkerMouseProfile.ParkSide walkerMouseParkSide() { return WalkerMouseProfile.ParkSide.NONE; }

    @ConfigItem(keyName = "showStatusOverlay", name = "Show walker status",
        description = "Show activity, elapsed time and approximate remaining travel time while the walker is active.",
        position = 21, section = generalSection)
    default boolean showStatusOverlay() { return true; }

    @ConfigItem(keyName = "drawRouteOnTiles", name = "Draw route on tiles",
        description = "Outline the remaining route and transport markers in the game scene.",
        position = 22, section = generalSection)
    default boolean drawRouteOnTiles() { return false; }

    @ConfigItem(keyName = "disableAutoRetaliateDuringTravel", name = "Turn off auto-retaliate during travel",
        description = "Turn auto-retaliate off before walking. Off leaves your current setting unchanged.",
        position = 20, section = generalSection)
    default boolean disableAutoRetaliateDuringTravel() { return false; }


    @ConfigSection(
        name = "General",
        description = "General walking and interaction settings",
        position = -2
    )
    String generalSection = "generalSection";

    @ConfigSection(
        name = "Hotkeys",
        description = "Keyboard shortcuts for walking",
        position = -1
    )
    String hotkeysSection = "hotkeysSection";

    @ConfigSection(
        name = "Route modification",
        description = "Choose which optional shortcuts Efficient Walker may include",
        position = 0
    )
    String routeModificationSection = "routeModificationSection";

    @ConfigSection(
        name = "Spirit tree teleports",
        description = "Select which player-grown spirit trees exist on this account. Built-in trees are gated automatically by quest requirements.",
        position = 1,
        closedByDefault = true
    )
    String spiritTreeTeleportsSection = "spiritTreeTeleportsSection";

    @ConfigSection(name = "Spellbook preparation", description = "Optional switching when a script requests a required spellbook",
        position = 5, closedByDefault = true)
    String spellbookSection = "spellbookSection";

    @ConfigItem(keyName = "autoSpellbook", name = "Switch spellbooks when requested",
        description = "When a script needs another spellbook, Walker tries in order: an owned Magic cape, your own POH altar, a world 330 hosted house, then the public altar (Arceuus, Tyss or Astral). Ordinary walks never switch.",
        position = 0, section = spellbookSection)
    default boolean autoSpellbook() { return false; }

    /** Folded into Switch spellbooks when requested; kept hidden for saved configuration compatibility. */
    @ConfigItem(keyName = "hostedHouseSpellbook", name = "Use world 330 hosted house", hidden = true,
        description = "For a requested spellbook switch, use the Rimmington house board on world 330 and a usable altar only after checking your personal POH and Magic cape. Confirm the spellbook and leave before handing control back. Requires Allow requested spellbook switches.",
        position = 1, section = spellbookSection)
    default boolean hostedHouseSpellbook() { return false; }

    @ConfigSection(
        name = "Debug",
        description = "Diagnostic options",
        position = 4,
        closedByDefault = true
    )
    String debugSection = "debugSection";

    @ConfigSection(
        name = "Wilderness travel",
        description = "Choose wilderness travel methods",
        position = 2,
        closedByDefault = true
    )
    String wildernessSection = "wildernessSection";

    @ConfigSection(
        name = "Sailing",
        description = "Efficient Sailing boat travel, recovery and navigation",
        position = 3,
        closedByDefault = true
    )
    String sailingSection = "sailingSection";

    @ConfigItem(keyName = "enableSailing", name = "Enable sailing",
        description = "Enable the imported Efficient Sailing navigator. Right-click the world map and choose Sail here. Walking keeps its existing engine and routes.",
        position = -1, section = sailingSection)
    default boolean enableSailing() { return false; }

    @ConfigItem(
        keyName = "allowWildernessTravel",
        name = "Allow Wilderness travel",
        description = "Explicitly allow routes through the Wilderness. Off by default; safe Ferox access remains available.",
        position = -1,
        section = wildernessSection
    )
    default boolean allowWildernessTravel()
    {
        return false;
    }

    @ConfigItem(
        keyName = "useWildernessTeleports",
        name = "Wilderness spells and items",
        description = "Use eligible wilderness teleport spells and items. Requires Use teleportations.",
        position = 0,
        section = wildernessSection
    )
    default boolean useWildernessTeleports()
    {
        return false;
    }

    @ConfigItem(
        keyName = "useFeroxTeleport",
        name = "Ferox Enclave",
        description = "Use a ring of dueling to reach Ferox Enclave. Requires Use teleportations.",
        position = 1,
        section = wildernessSection
    )
    default boolean useFeroxTeleport()
    {
        return true;
    }

    @ConfigItem(
        keyName = "useWildernessLevers",
        name = "Wilderness levers",
        description = "Include Ardougne, Edgeville and wilderness teleport levers.",
        position = 2,
        section = wildernessSection
    )
    default boolean useWildernessLevers()
    {
        return false;
    }

    @ConfigItem(
        keyName = "useWildernessObelisks",
        name = "Wilderness obelisks",
        description = "Use wilderness obelisks and replan from the actual landing.",
        position = 3,
        section = wildernessSection
    )
    default boolean useWildernessObelisks()
    {
        return false;
    }

    @ConfigItem(
        keyName = "wildernessThreatLogout",
        name = "Wilderness threat logout",
        description = "DroKBD-style safety: attempt logout when an attack-capable player is nearby in the Wilderness. If PvP combat has already begun, stop walking instead.",
        position = 4,
        section = wildernessSection
    )
    default boolean wildernessThreatLogout()
    {
        return true;
    }

    @Range(min = 1, max = 64)
    @ConfigItem(
        keyName = "wildernessThreatRadius",
        name = "Player safety radius",
        description = "Attempt logout when an attack-capable player is within this many tiles.",
        position = 5,
        section = wildernessSection
    )
    default int wildernessThreatRadius()
    {
        return 32;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(
        keyName = "wildernessUpperCombatBuffer",
        name = "Upper combat buffer",
        description = "Extra combat levels above the normal Wilderness attack range treated as threatening.",
        position = 6,
        section = wildernessSection
    )
    default int wildernessUpperCombatBuffer()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "handleInteractions",
        name = "Automate quest steps",
        description = "Press Walk to quest step once to follow quest targets, handle supported interactions and dialogue; Cancel walk stops automation",
        position = 0,
        section = generalSection,
        hidden = true
    )
    default boolean handleInteractions()
    {
        return false;
    }

    @ConfigItem(
        keyName = "walkToNearestBankHotkey",
        name = "Walk to nearest bank",
        description = "Walk to the nearest reachable bank",
        position = 0,
        section = hotkeysSection
    )
    default Keybind walkToNearestBankHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "walkToQuestStepHotkey",
        name = "Walk to quest step",
        description = "Walk to the current Quest Helper step; requires Quest Helper and a selected quest",
        position = 1,
        section = hotkeysSection
    )
    default Keybind walkToQuestStepHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "cancelWalkHotkey",
        name = "Cancel walk",
        description = "Cancel the current walk and clear its route",
        position = 2,
        section = hotkeysSection
    )
    default Keybind cancelWalkHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "useTeleportations",
        name = "Use teleportations",
        description = "Use supported standard spells and tablets when they improve a route",
        position = 0,
        section = routeModificationSection
    )
    default boolean useTeleportations()
    {
        return true;
    }

    @ConfigItem(keyName = "useMaxPoh", name = "Use POH",
        description = "Use your current house and its teleports as a travel hub through a Construction cape or house tablet. On entering the house, fills in the POH configuration panel automatically and reads the available teleports from it. Wilderness and network settings still apply.",
        position = 20, section = routeModificationSection)
    default boolean useMaxPoh() { return false; }

    @ConfigItem(
        keyName = "allowAgilityShortcuts",
        name = "Allow agility shortcuts",
        description = "Include eligible agility shortcuts when calculating routes",
        position = 1,
        section = routeModificationSection
    )
    default boolean allowAgilityShortcuts()
    {
        return true;
    }

    @ConfigItem(
        keyName = "allowGrappleShortcuts",
        name = "Allow grapple shortcuts",
        description = "Include eligible crossbow and Mith grapple shortcuts when calculating routes",
        position = 2,
        section = routeModificationSection
    )
    default boolean allowGrappleShortcuts()
    {
        return false;
    }

    @ConfigItem(
        keyName = "useFairyRings",
        name = "Use fairy rings",
        description = "Include available fairy rings when calculating routes",
        position = 3,
        section = routeModificationSection
    )
    default boolean useFairyRings()
    {
        return true;
    }

    @ConfigItem(
        keyName = "useSpiritTrees",
        name = "Use spirit trees",
        description = "Include available spirit-tree routes in Efficient Walker's own route calculation",
        position = 4,
        section = routeModificationSection
    )
    default boolean useSpiritTrees()
    {
        return true;
    }

    @ConfigItem(
        keyName = "spiritTreeEtceteria",
        name = "Etceteria",
        description = "Enable only if this account has a healthy, fully-grown Spirit Tree at Etceteria",
        position = 0,
        section = spiritTreeTeleportsSection
    )
    default boolean spiritTreeEtceteria()
    {
        return false;
    }

    @ConfigItem(
        keyName = "spiritTreeBrimhaven",
        name = "Brimhaven",
        description = "Enable only if this account has a healthy, fully-grown Spirit Tree at Brimhaven",
        position = 1,
        section = spiritTreeTeleportsSection
    )
    default boolean spiritTreeBrimhaven()
    {
        return false;
    }

    @ConfigItem(
        keyName = "spiritTreePortSarim",
        name = "Port Sarim",
        description = "Enable only if this account has a healthy, fully-grown Spirit Tree at Port Sarim",
        position = 2,
        section = spiritTreeTeleportsSection
    )
    default boolean spiritTreePortSarim()
    {
        return false;
    }

    @ConfigItem(
        keyName = "spiritTreeHosidius",
        name = "Hosidius",
        description = "Enable only if this account has a healthy, fully-grown Spirit Tree at Hosidius",
        position = 3,
        section = spiritTreeTeleportsSection
    )
    default boolean spiritTreeHosidius()
    {
        return false;
    }

    @ConfigItem(
        keyName = "spiritTreeFarmingGuild",
        name = "Farming Guild",
        description = "Enable only if this account has a healthy, fully-grown Spirit Tree at the Farming Guild",
        position = 4,
        section = spiritTreeTeleportsSection
    )
    default boolean spiritTreeFarmingGuild()
    {
        return false;
    }

    @ConfigItem(
        keyName = "useMinigameTeleports",
        name = "Use minigame teleports",
        description = "Include available Grouping/minigame teleports when their 20-minute cooldown is ready and they improve the route",
        position = 5,
        section = routeModificationSection
    )
    default boolean useMinigameTeleports()
    {
        return true;
    }

    @ConfigItem(
        keyName = "useCharterShips",
        name = "Use charter ships",
        description = "Include charter-ship routes when the account meets their quest/varbit requirements and the fare is already carried",
        position = 6,
        section = routeModificationSection
    )
    default boolean useCharterShips()
    {
        return true;
    }

    @ConfigItem(
        keyName = "useHotAirBalloons",
        name = "Use hot air balloons",
        description = "Include unlocked hot-air-balloon routes. When Use items from bank is enabled, fetch the required destination log before committing a balloon route.",
        position = 7,
        section = routeModificationSection
    )
    default boolean useHotAirBalloons()
    {
        return false;
    }

    @ConfigItem(
        keyName = "useCanoes",
        name = "Use canoes",
        description = "Include canoe routes. Wilderness destinations also require Allow Wilderness travel; safe destinations remain available when Wilderness travel is off.",
        position = 8,
        section = routeModificationSection
    )
    default boolean useCanoes()
    {
        return false;
    }

    @ConfigItem(
        keyName = "useItemsFromBank",
        name = "Use items from bank",
        description = "Fetch teleport items or missing spell runes when they reduce total travel time/distance; item value and GP cost are not considered",
        position = 9,
        section = routeModificationSection
    )
    default boolean useItemsFromBank()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableDebugging",
        name = "Enable debugging",
        description = "Writes route planning, movement, obstacle, and transition diagnostics to the client log",
        position = 0,
        section = debugSection
    )
    default boolean enableDebugging()
    {
        return false;
    }

    // Imported Sailing settings are declared here so defaults and Reset work in the Walker panel.
    @ConfigItem(keyName = "currentBoatPort", name = "Legacy boat location", description = "Saved compatibility value; Sailing chooses the closest port automatically.", section = JOURNEY, position = 0, hidden = true)
    default BoatPort currentBoatPort() { return BoatPort.UNKNOWN_LOST; }
    @ConfigItem(keyName = "launchPort", name = "Legacy launch port", description = "Saved compatibility value; Sailing chooses the closest port automatically.", section = JOURNEY, position = 1, hidden = true)
    default LaunchPort launchPort() { return LaunchPort.AUTOMATIC; }


    // Hidden legacy keys are retained only to migrate saved configurations.
    @ConfigItem(keyName = "portMode", name = "Port selection", description = "AUTO keeps nearest-by-Walker-cost selection. DESTINATION_NEAREST favors the target's nearest reachable launch port. TOTAL_JOURNEY compares estimated land and sea time; sea distance is only a lower bound. FIXED uses Fixed port. Preferred launch port overrides all modes.", section = JOURNEY, position = 0, hidden = true)
    default PortMode portMode() { return PortMode.AUTO; }
    @ConfigItem(keyName = "fixedPort", name = "Fixed port", description = "Microbot PortLocation enum name. Port Cazar is interpreted as PORT_KHAZARD.", section = JOURNEY, position = 1, hidden = true)
    default String fixedPort() { return "PORT_KHAZARD"; }
    @ConfigItem(keyName = "knownBoatPort", name = "Known boat location (optional)", description = "Where your existing boat is docked. Used for free boarding or when both relocation methods are disabled. It does not choose a tablet/recovery launch port. Old saved values are copied once to Preferred launch port to preserve prior behavior; clear that override to use this as location information only.", section = JOURNEY, position = 2, hidden = true)
    default String knownBoatPort() { return ""; }
    @ConfigItem(keyName = "boatName", name = "Exact boat name", description = "Copy the boat name exactly. Used to select your boat when boarding, summoning or recovering; never chooses an arbitrary boat.", section = JOURNEY, position = 0)
    default String boatName() { return ""; }
    @ConfigItem(keyName = "allowRecovery", name = "Legacy recovery switch", description = "Saved compatibility value; NPC recovery and payment are automatic when tablet recall is off.", section = JOURNEY, position = 4, hidden = true)
    default boolean allowRecovery() { return false; }
    @ConfigItem(keyName = "allowCargoLoss", name = "Accept recovery cargo loss", description = "Boat relocation can discard cargo. Off by default. Required before any automated recovery, even if the game shows no warning.", section = JOURNEY, position = 5, hidden = true)
    default boolean allowCargoLoss() { return false; }
    @Range(min = 0, max = 1000000)
    @ConfigItem(keyName = "maxRecoveryFee", name = "Maximum recovery fee", description = "Maximum COINS paid for one explicit NPC recovery quote. Recover missing boat authorizes payment up to this cap and CARGO LOSS; larger quotes stop the journey. Not used for tablet summoning.", section = TRAVEL, position = 0, hidden = true)
    default int maxRecoveryFee() { return 75000; }
    @Range(min = 0, max = 50000)
    @ConfigItem(keyName = "charterReserve", name = "Additional transport coins", description = "A reserve, not a claimed exact fare. Walker still selects and validates transports. Only a missing coin amount is withdrawn.", section = TRAVEL, position = 7, hidden = true)
    default int charterReserve() { return 5000; }
    @Range(min = 1, max = 12)
    @ConfigItem(keyName = "portCandidates", name = "Ports to compare", description = "Bounded geometric shortlist for the selected strategy, then Walker route comparison. TOTAL_JOURNEY's shortlist cannot account for every teleport; no strategy claims a global optimum or a verified sea voyage.", section = JOURNEY, position = 8, hidden = true)
    default int portCandidates() { return 5; }
    @ConfigItem(keyName = "excludedPorts", name = "Excluded ports", description = "Comma-separated PortLocation names. Use this for locked or not-yet-tested docks.", section = TRAVEL, position = 9, hidden = true)
    default String excludedPorts() { return ""; }
    @ConfigItem(keyName = "portOverrides", name = "Port approach overrides", description = "Optional tested land approaches: PORT_KHAZARD,x,y;CATHERBY,x,y. Otherwise query Walker's standable shore tiles.", section = TRAVEL, position = 10, hidden = true)
    default String portOverrides() { return ""; }

    @ConfigItem(keyName = "steering", name = "Steering input", description = "Minimap first, with native SET_HEADING fallback when projection/clipping is unavailable; neither is a land walk command.", section = SAIL, position = 0, hidden = true)
    default Steering steering() { return Steering.MINIMAP_THEN_HEADING; }
    @Range(min = 2, max = 12)
    @ConfigItem(keyName = "arrivalRadius", name = "Arrival radius (tiles)", description = "Arrival requires the hull to stop inside this radius for two observed ticks.", section = SAIL, position = 1, hidden = true)
    default int arrivalRadius() { return 6; }
    @Range(min = 6, max = 40)
    @ConfigItem(keyName = "lookAhead", name = "Steering look-ahead", description = "Every commanded segment must be clear in the currently loaded sea scene.", section = SAIL, position = 2, hidden = true)
    default int lookAhead() { return 28; }
    @Range(min = 300, max = 2000)
    @ConfigItem(keyName = "commandMillis", name = "Minimum command spacing (ms)", description = "Commands also require a fresh game-tick snapshot; this is not a click-spam speed control.", section = SAIL, position = 3, hidden = true)
    default int commandMillis() { return 550; }
    @Range(min = 4, max = 30)
    @ConfigItem(keyName = "stallSeconds", name = "No-movement timeout (seconds)", description = "Uses the hull's sea position, not walking or animations on the deck.", section = SAIL, position = 4, hidden = true)
    default int stallSeconds() { return 8; }
    @Range(min = 0, max = 8)
    @ConfigItem(keyName = "maxRecoveries", name = "Legacy reverse limit (unused)", description = "Retained only for configuration compatibility. The route-first pilot never performs automatic reverse or arbitrary escape movements.", section = SAIL, position = 5, hidden = true)
    default int maxRecoveries() { return 0; }
    @Range(min = 0, max = 2)
    @ConfigItem(keyName = "hullMargin", name = "Extra hull margin (tiles)", description = "Additional padding around the actual native collision bounds, not nominal boat size. Large margins may exclude narrow docks.", section = SAIL, position = 6, hidden = true)
    default int hullMargin() { return 0; }
    @Range(min = 10000, max = 200000)
    @ConfigItem(keyName = "maxSearchNodes", name = "Maximum sea search nodes", description = "Bounded A* expansion; budget exhaustion is logged separately from an unreachable target.", section = SAIL, position = 7, hidden = true)
    default int maxSearchNodes() { return 60000; }

    @ConfigItem(keyName = "hazardZones", name = "No-go zones", description = "One per line or semicolon: circle,label,x,y,radius OR rect,label,x1,y1,x2,y2. Bad syntax stops the journey.", section = SAFETY, position = 0, hidden = true)
    default String hazardZones() { return ""; }
    @ConfigItem(keyName = "dangerNames", name = "Avoid NPC name fragments", description = "Loaded top-level NPCs matching these comma-separated fragments get avoidance circles. This is not a complete sea-monster registry.", section = SAFETY, position = 1, hidden = true)
    default String dangerNames() { return "kraken,sea serpent,sea troll"; }
    @Range(min = 3, max = 30)
    @ConfigItem(keyName = "dangerRadius", name = "NPC avoidance radius", description = "Applied to matching NPC positions in the loaded sea scene.", section = SAFETY, position = 2, hidden = true)
    default int dangerRadius() { return 12; }
    @ConfigItem(keyName = "stopOnDamage", name = "Stop on player damage", description = "Stops and reports player hitpoint loss while sailing. Does not claim to monitor hull HP.", section = SAFETY, position = 3, hidden = true)
    default boolean stopOnDamage() { return true; }
    @Range(min = 30, max = 600)
    @ConfigItem(keyName = "noProgressSeconds", name = "Voyage no-progress limit", description = "Stops loops with no meaningful destination progress or newly charted water.", section = SAFETY, position = 4, hidden = true)
    default int noProgressSeconds() { return 180; }
    @ConfigItem(keyName = "persistAtlas", name = "Remember observed sea tiles", description = "Local bounded atlas. Live collision always overrides cached data. No uploads or account details are stored.", section = SAFETY, position = 5, hidden = true)
    default boolean persistAtlas() { return true; }
    @ConfigItem(keyName = "debug", name = "Detailed diagnostics", description = "Writes local voyage/decision logs. Use Export sailing diagnostics below for a snapshot.", section = SAFETY, position = 6, hidden = true)
    default boolean debug() { return true; }
    @ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Compact Walker-style phase, elapsed time and ETA. Hidden when idle, arrived, cancelled, blocked or paused.", section = SAFETY, position = 7, hidden = true)
    default boolean showOverlay() { return true; }
    @ConfigItem(keyName = "exportDiagnostics", name = "Export sailing diagnostics", description = "Save the current local voyage, chart and diagnostic snapshot.", section = JOURNEY, position = 6)
    default ConfigButton exportDiagnostics() { return new ConfigButton(); }

    @ConfigItem(keyName = "useSummonBoatTablet", name = "Recall boat with tablet", description = "On: recall your boat at the closest eligible port with a Summon boat tablet (teleport focus required). Lost-at-sea boats use a registered shipwright within the recovery fee cap. Off: always use shipwright recovery. Relocation can discard cargo.", section = JOURNEY, position = 1)
    default boolean useSummonBoatTablet() { return false; }
    @Range(min = 12, max = 64)
    @ConfigItem(keyName = "anticipationTiles", name = "Hazard look-ahead (tiles)", description = "Plan early around loaded coastlines/obstacles and configured hazards, using gradual validated turns with sails raised. Never treats unobserved water as safe.", section = SAIL, position = 8, hidden = true)
    default int anticipationTiles() { return 36; }

    @ConfigItem(keyName = "preferredLaunchPort", name = "Preferred launch port (optional)", description = "Explicit launch override, e.g. PORT_KHAZARD. Independent of Known boat location. Leave blank to use Port selection.", section = JOURNEY, position = 12, hidden = true)
    default String preferredLaunchPort() { return ""; }
    @ConfigItem(keyName = "startPauseHotkey", name = "Start / pause hotkey", description = "Pause or resume the current voyage without clearing its destination. Sail here starts a new voyage. Microbot's temporary input pause resumes automatically; this key requires another press.", section = JOURNEY, position = 5)
    default Keybind startPauseHotkey() { return new Keybind(java.awt.event.KeyEvent.VK_SLASH, 0); }
    @Range(min = 1, max = 8)
    @ConfigItem(keyName = "estimatedCruiseTilesPerTick", name = "Port-ranking cruise estimate", description = "Estimated sea tiles per 600ms tick for TOTAL_JOURNEY ranking. This is a planning assumption, not observed voyage speed or proof that a sea route exists.", section = JOURNEY, position = 13, hidden = true)
    default int estimatedCruiseTilesPerTick() { return 3; }
    @Range(min = 0, max = 120)
    @ConfigItem(keyName = "relocationOverheadSeconds", name = "Port-ranking relocation estimate", description = "Estimated extra seconds for tablet/NPC relocation when the boat is elsewhere. Used only by TOTAL_JOURNEY; not a measured interface duration.", section = JOURNEY, position = 14, hidden = true)
    default int relocationOverheadSeconds() { return 20; }
    @Range(min = 0, max = 6)
    @ConfigItem(keyName = "cruiseClearanceTiles", name = "Cruise shoreline clearance", description = "Soft route preference outside the actual hull. Zero disables the preference. Hard hull and live swept-turn checks still apply; the preference is reduced near arrival and does not enlarge the hull at docks.", section = SAIL, position = 9, hidden = true)
    default int cruiseClearanceTiles() { return 3; }

    @ConfigItem(keyName = "useReferenceChart", name = "Plan full sea voyage", description = "Plan the whole provisional voyage on the supplied reference chart and historical boat observations. Dashed map line is provisional; only current live hull clearance authorizes motion.", section = SAIL, position = 20)
    default boolean useReferenceChart() { return true; }
    @ConfigItem(keyName = "importChartPlotter", name = "Use local Chart Plotter data", description = "Read-only import from efficient-sailing/chart-plotter-collision.txt or chart-plotter/collision.bin under the RuneLite directory. No automatic network downloads. Live collision takes priority.", section = SAIL, position = 21)
    default boolean importChartPlotter() { return true; }
    @Range(min = 1, max = 4)
    @ConfigItem(keyName = "steeringLatencyTicks", name = "Steering forecast latency", description = "Maximum command delay modelled in server ticks. Default 2. Increasing it is more conservative; decreasing it is not a speed boost. Real boat physics and latency still require live testing.", section = SAIL, position = 22)
    default int steeringLatencyTicks() { return 2; }


}
