package net.runelite.client.plugins.microbot.drorecorder;

import net.runelite.client.config.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@ConfigGroup(DroRecorderConfig.GROUP)
@ConfigInformation("<b>DRO's Recorder</b> passively records gameplay for later AI/script analysis: player/NPC state, LOS/pathing, inventory/equipment/prayers, combat, graphics, ground items, dialogue, activity changes, mouse/camera activity, and tactical markers.<br><br>" +
        "<b>When it records:</b> plugin enabled = recording, everywhere, including when the client turns it back on at startup. Plugin disabled = nothing is recorded. Disable the plugin to finish and save the current recording; enabling it again starts a new one.<br><br>" +
        "<b>Disk use:</b> compressed recordings take about 100 MB per hour of play (measured 80-115 MB/h). There is no size cap or automatic cleanup, so delete old recordings yourself.<br><br>" +
        "Use <b>Open Output Folder</b> below to view recordings.<br><br>" +
        "<b>Marker timing:</b><br>" +
        "<b>Ctrl+F7 Safe</b> - press AFTER you have reached the safespot/anchor and the situation is stable.<br>" +
        "<b>Ctrl+F8 Mistake</b> - press immediately after a mistake/bad position so it is marked as something not to learn.<br>" +
        "<b>Ctrl+F9 Important</b> - press immediately after an important tactical decision/action.<br>" +
        "<b>Ctrl+F10 Pause/Resume</b> - temporarily stops/continues recording without disabling the plugin.<br><br>" +
        "<b>Output folder:</b> ~/.runelite/dro-recorder/<br>" +
        "If Open Output Folder does not work: Finder -> Go -> Go to Folder (Cmd+Shift+G), then paste that path.")
public interface DroRecorderConfig extends Config
{
    String GROUP = "drorecorder";

    @ConfigItem(
        keyName = "recordTickSnapshots",
        name = "Record tick snapshots",
        description = "Records player, NPC, inventory, equipment, prayer, camera and mouse state every game tick.",
        position = 0
    )
    default boolean recordTickSnapshots()
    {
        return true;
    }

    @ConfigItem(
        keyName = "recordMouseMovement",
        name = "Record mouse movement",
        description = "Samples the mouse path while also recording press/release and enter/exit events.",
        position = 1
    )
    default boolean recordMouseMovement()
    {
        return true;
    }

    @Range(min = 10, max = 250)
    @ConfigItem(
        keyName = "mouseSampleMs",
        name = "Mouse sample interval",
        description = "Minimum milliseconds between stored mouse-move samples. Press/release events are never sampled out.",
        position = 2
    )
    default int mouseSampleMs()
    {
        return 25;
    }

    @ConfigItem(
        keyName = "recordNpcEvents",
        name = "Record NPC spawn/despawn",
        description = "Records NPC creation and removal events.",
        position = 3
    )
    default boolean recordNpcEvents()
    {
        return true;
    }

    @ConfigItem(
        keyName = "recordObjectEvents",
        name = "Record scene objects",
        description = "Records game/wall/decorative/ground object spawn and despawn events with world tiles and object IDs.",
        position = 4
    )
    default boolean recordObjectEvents()
    {
        return true;
    }

    @ConfigItem(
        keyName = "recordCombatEvents",
        name = "Record combat events",
        description = "Records animation, interaction/target, hitsplat and projectile events.",
        position = 5
    )
    default boolean recordCombatEvents()
    {
        return true;
    }

    @ConfigItem(
        keyName = "recordInventoryEvents",
        name = "Record inventory changes",
        description = "Records inventory/equipment container changes in addition to tick snapshots.",
        position = 6
    )
    default boolean recordInventoryEvents()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show recorder overlay",
        description = "Shows a small translucent strip with recording status and elapsed time. Alt-drag to move it.",
        position = 7
    )
    default boolean showOverlay()
    {
        return false;
    }

    @ConfigItem(
        keyName = "openOutputFolder",
        name = "Open Output Folder",
        description = "Open the DRO's Recorder output folder in Finder/File Explorer, even when the recorder is stopped.",
        position = 8
    )
    default ConfigButton openOutputFolder()
    {
        return new ConfigButton();
    }

    @ConfigItem(
        keyName = "compressRecordings", name = "Compress recordings",
        description = "Write lossless gzip JSONL files to reduce disk use. Applies to the next recording.", position = 9
    )
    default boolean compressRecordings() { return true; }

    @Range(min = 4, max = 32)
    @ConfigItem(
        keyName = "localCollisionRadius",
        name = "Local collision radius",
        description = "Dense collision radius captured around the player for annotations. Full-scene collision is captured separately.",
        position = 20
    )
    default int localCollisionRadius()
    {
        return 12;
    }

    @ConfigItem(
        keyName = "recordLineOfSight",
        name = "Analyze NPC line of sight",
        description = "Adds NPC-to-player and player-to-NPC line-of-sight state to NPC snapshots.",
        position = 21
    )
    default boolean recordLineOfSight()
    {
        return true;
    }

    @ConfigItem(
        keyName = "recordPathAnalysis",
        name = "Analyze NPC paths",
        description = "Checks whether each loaded NPC can reach melee distance through the current scene collision map.",
        position = 22
    )
    default boolean recordPathAnalysis()
    {
        return true;
    }

    @Range(min = 1000, max = 20000)
    @ConfigItem(
        keyName = "pathNodeLimit",
        name = "Path node limit",
        description = "Maximum scene positions explored per NPC path analysis. 12000 covers a normal 104x104 scene.",
        position = 23
    )
    default int pathNodeLimit()
    {
        return 12000;
    }

    @ConfigItem(
        keyName = "recordSceneCollision",
        name = "Record full scene collision",
        description = "Stores compressed scene-wide collision data when a scene loads/changes and on safespot/lure annotations.",
        position = 24
    )
    default boolean recordSceneCollision()
    {
        return true;
    }

    @ConfigItem(
        keyName = "recordSceneObjects",
        name = "Record full scene objects",
        description = "Stores all currently loaded game/wall/decorative/ground objects in scene snapshots.",
        position = 25
    )
    default boolean recordSceneObjects()
    {
        return true;
    }


    @ConfigItem(
        keyName = "recordCombatContext",
        name = "Record combat context",
        description = "Adds a compact per-tick combat view: combat NPC roles/targets/animations, LOS/pathability, attackers, protection prayer and incoming projectiles.",
        position = 26
    )
    default boolean recordCombatContext()
    {
        return true;
    }

    @ConfigItem(
        keyName = "recordPrayerTransitions",
        name = "Record prayer transitions",
        description = "Writes discrete prayer_state_changed events when active prayers change so attack/prayer timing can be reconstructed precisely.",
        position = 27
    )
    default boolean recordPrayerTransitions()
    {
        return true;
    }

    @ConfigItem(
        keyName = "fightCavesClassification",
        name = "Fight Caves NPC roles",
        description = "Adds stable Fight Caves role labels (bat, blob, ranger, melee, mager, Jad, healer) to relevant NPC records without replacing raw NPC IDs/names.",
        position = 28
    )
    default boolean fightCavesClassification()
    {
        return true;
    }

    @ConfigItem(
        keyName = "safespotHotkey",
        name = "Safespot marker",
        description = "Press AFTER reaching the intended safespot/anchor and the position is stable. Captures the resulting NPC/path/LOS state and a full scene snapshot.",
        position = 31
    )
    default Keybind safespotHotkey()
    {
        return new Keybind(KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "mistakeF8Hotkey",
        name = "Mistake marker",
        description = "Press immediately after a bad action/position so later analysis knows not to learn that state as correct behavior.",
        position = 32
    )
    default Keybind mistakeHotkey()
    {
        return new Keybind(KeyEvent.VK_F8, InputEvent.CTRL_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "importantF9Hotkey",
        name = "Important marker",
        description = "Press immediately after an important tactical decision/action you want highlighted for later analysis.",
        position = 33
    )
    default Keybind importantHotkey()
    {
        return new Keybind(KeyEvent.VK_F9, InputEvent.CTRL_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "pauseHotkey",
        name = "Pause/resume recorder",
        description = "Pauses or resumes recording without disabling the plugin.",
        position = 34
    )
    default Keybind pauseHotkey()
    {
        return new Keybind(KeyEvent.VK_F10, InputEvent.CTRL_DOWN_MASK);
    }
}
