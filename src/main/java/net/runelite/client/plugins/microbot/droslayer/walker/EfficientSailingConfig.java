package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.client.config.*;

@ConfigGroup(EfficientSailingConfig.GROUP)
@ConfigInformation("Enter your exact boat name. Sailing always uses the closest eligible port to your destination. "
        + "With Recall boat with tablet selected, use any eligible port and summon your named boat there (teleport focus required). "
        + "With tablet recall off, or if the game reports the boat lost at sea, use a registered shipwright to recover the named boat within the configured fee cap. "
        + "Missing travel and recovery supplies are banked automatically. Boat relocation can discard cargo. "
        + "Right-click your sea destination and choose Sail here. Press / (or your hotkey) to pause/resume; Cancel sailing ends the voyage. Disable other boat controllers.")
public interface EfficientSailingConfig extends Config {
    String GROUP = "efficientwalker";
    // Walker declares the displayed Sailing section; all original items keep their keys and defaults.
    String JOURNEY = "sailingSection";
    String TRAVEL = JOURNEY;
    String SAIL = JOURNEY;
    String SAFETY = JOURNEY;

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


    enum PortMode { AUTO, FIXED, DESTINATION_NEAREST, TOTAL_JOURNEY }
    enum Steering { MINIMAP_THEN_HEADING, NATIVE_HEADING }
}
