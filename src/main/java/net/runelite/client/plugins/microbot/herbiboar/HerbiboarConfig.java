package net.runelite.client.plugins.microbot.herbiboar;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("AutoHerbiboar")
public interface HerbiboarConfig extends Config {
    
    enum RunEnergyOption {
        NONE("None"),
        STAMINA_POTION("Stamina potion"),
        SUPER_ENERGY_POTION("Super energy potion"),
        ENERGY_POTION("Energy potion"),
        STRANGE_FRUIT("Strange fruit");
        
        private final String name;
        
        RunEnergyOption(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    @ConfigSection(
            name = "Optionals",
            description = "Optionals",
            position = 0
    )
    String OPTIONALS_SECTION = "optionals";

    @ConfigItem(
            keyName = "useHerbSack",
            name = "Herb sack",
            description = "",
            section = OPTIONALS_SECTION,
            position = 0
    )
    default boolean useHerbSack() {
        return false;
    }

    @ConfigItem(
            keyName = "useMagicSecateurs",
            name = "Magic secateurs",
            description = "",
            section = OPTIONALS_SECTION,
            position = 1
    )
    default boolean useMagicSecateurs() {
        return false;
    }

    @ConfigItem(
            keyName = "resetIfStuck",
            name = "Reset if stuck?",
            description = "Fallback behavior to try getting unstuck if stuck for more than 1m in the same place.",
            section = OPTIONALS_SECTION,
            position = 2,
            hidden = true
    )
    default boolean resetIfStuck() {
        return false;
    }

    @ConfigItem(
            keyName = "interactionDistance",
            name = "Interaction distance",
            description = "Minimum distance to start interacting with objects like tunnel, rock, etc. (lower = will move closer to object before clicking on it)",
            section = OPTIONALS_SECTION,
            position = 3
    )
    @Range (min = 1, max = 50)
    default int interactionDistance() {
        return 25;
    }


    @ConfigSection(
        name = "Run energy management",
        description = "Run energy management",
        position = 1
    )
    String RUN_ENERGY_SECTION = "runenergy";

    @ConfigItem(
        keyName = "runEnergyOption",
        name = "Restore with",
        description = "Select which item to use to restore run energy",
        section = RUN_ENERGY_SECTION,
        position = 0
    )
    default RunEnergyOption runEnergyOption() {
        return RunEnergyOption.NONE;
    }

    @ConfigItem(
            keyName = "stamBuffAlwaysActive",
            name = "Always keep stam buff?",
            description = "If using stamina potions, keep the buff active at all times",
            section = RUN_ENERGY_SECTION,
            position = 1
    )
    default boolean stamBuffAlwaysActive() {
        return false;
    }

    @ConfigItem(
            keyName = "thresholdEnergy",
            name = "Use energy potions below (%)",
            description = "Use energy potions below this percentage of run energy",
            section = RUN_ENERGY_SECTION,
            position = 2
    )
    @Range(min = 1, max = 100)
    default int thresholdEnergy() {
        return 50;
    }

    @ConfigItem(
        keyName = "dropEmptyVials",
        name = "Drop empty vials",
        description = "Drop empty vials from drinking potions",
        section = RUN_ENERGY_SECTION,
        position = 3
    )
    default boolean dropEmptyVials() {
        return false;
    }

    @ConfigSection(
        name = "Fossils to drop",
        description = "Fossils you don't want to keep",
        position = 2,
        closedByDefault = true
    )
    String FOSSILS_SECTION = "fossils";

    @ConfigItem(
        keyName = "dropSmallFossil",
        name = "Small fossil",
        description = "",
        section = FOSSILS_SECTION,
        position = 0
    )
    default boolean dropSmallFossil() {
        return false;
    }

    @ConfigItem(
        keyName = "dropMediumFossil",
        name = "Medium fossil",
        description = "",
        section = FOSSILS_SECTION,
        position = 1
    )
    default boolean dropMediumFossil() {
        return false;
    }

    @ConfigItem(
        keyName = "dropLargeFossil",
        name = "Large fossil",
        description = "",
        section = FOSSILS_SECTION,
        position = 2
    )
    default boolean dropLargeFossil() {
        return false;
    }

    @ConfigItem(
        keyName = "dropRareFossil",
        name = "Rare fossil",
        description = "",
        section = FOSSILS_SECTION,
        position = 3
    )
    default boolean dropRareFossil() {
        return false;
    }

    @ConfigSection(
        name = "Herbs to drop",
        description = "Herbs you don't want to keep",
        position = 3,
        closedByDefault = true
    )
    String HERBS_SECTION = "herbs";

    @ConfigItem(
        keyName = "dropGuam",
        name = "Guam",
        description = "",
        section = HERBS_SECTION,
        position = 0
    )
    default boolean dropGuam() { return false; }

    @ConfigItem(
        keyName = "dropMarrentill",
        name = "Marrentill",
        description = "",
        section = HERBS_SECTION,
        position = 1
    )
    default boolean dropMarrentill() { return false; }

    @ConfigItem(
        keyName = "dropTarromin",
        name = "Tarromin",
        description = "",
        section = HERBS_SECTION,
        position = 2
    )
    default boolean dropTarromin() { return false; }

    @ConfigItem(
        keyName = "dropHarralander",
        name = "Harralander",
        description = "",
        section = HERBS_SECTION,
        position = 3
    )
    default boolean dropHarralander() { return false; }

    @ConfigItem(
        keyName = "dropRanarr",
        name = "Ranarr",
        description = "",
        section = HERBS_SECTION,
        position = 4
    )
    default boolean dropRanarr() { return false; }

    @ConfigItem(
        keyName = "dropToadflax",
        name = "Toadflax",
        description = "",
        section = HERBS_SECTION,
        position = 5
    )
    default boolean dropToadflax() { return false; }

    @ConfigItem(
        keyName = "dropIrit",
        name = "Irit",
        description = "",
        section = HERBS_SECTION,
        position = 6
    )
    default boolean dropIrit() { return false; }

    @ConfigItem(
        keyName = "dropAvantoe",
        name = "Avantoe",
        description = "",
        section = HERBS_SECTION,
        position = 7
    )
    default boolean dropAvantoe() { return false; }

    @ConfigItem(
        keyName = "dropKwuarm",
        name = "Kwuarm",
        description = "",
        section = HERBS_SECTION,
        position = 8
    )
    default boolean dropKwuarm() { return false; }

    @ConfigItem(
        keyName = "dropSnapdragon",
        name = "Snapdragon",
        description = "",
        section = HERBS_SECTION,
        position = 9
    )
    default boolean dropSnapdragon() { return false; }

    @ConfigItem(
        keyName = "dropCadantine",
        name = "Cadantine",
        description = "",
        section = HERBS_SECTION,
        position = 10
    )
    default boolean dropCadantine() { return false; }

    @ConfigItem(
        keyName = "dropLantadyme",
        name = "Lantadyme",
        description = "",
        section = HERBS_SECTION,
        position = 11
    )
    default boolean dropLantadyme() { return false; }

    @ConfigItem(
        keyName = "dropDwarfWeed",
        name = "Dwarf weed",
        description = "",
        section = HERBS_SECTION,
        position = 12
    )
    default boolean dropDwarfWeed() { return false; }

    @ConfigItem(
        keyName = "dropTorstol",
        name = "Torstol",
        description = "",
        section = HERBS_SECTION,
        position = 13
    )
    default boolean dropTorstol() { return false; }

    @ConfigSection(
            name = "General",
            description = "General Plugin settings",
            position = 4,
            closedByDefault = false
    )
    String GENERAL_SECTION = "general";

    @ConfigItem(
            position = 0,
            keyName = "showStart",
            name = "Show start objects",
            description = "Show highlights for starting rocks and logs.",
            section = GENERAL_SECTION
    )
    default boolean isStartShown()
    {
        return true;
    }

    @ConfigItem(
            position = 1,
            keyName = "showClickboxes",
            name = "Show clickboxes",
            description = "Show clickboxes on trail objects and tunnels instead of tiles.",
            section = GENERAL_SECTION
    )
    default boolean showClickBoxes()
    {
        return false;
    }

    @Alpha
    @ConfigItem(
            position = 2,
            keyName = "colorStart",
            name = "Start color",
            description = "Color for rocks that start the trails.",
            section = GENERAL_SECTION
    )
    default Color getStartColor()
    {
        return Color.CYAN;
    }

    @ConfigItem(
            position = 3,
            keyName = "showTunnel",
            name = "Show end tunnels",
            description = "Show highlights for tunnels with herbiboars.",
            section = GENERAL_SECTION
    )
    default boolean isTunnelShown()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
            position = 4,
            keyName = "colorTunnel",
            name = "Tunnel color",
            description = "Color for tunnels with herbiboars.",
            section = GENERAL_SECTION
    )
    default Color getTunnelColor()
    {
        return Color.GREEN;
    }

    @ConfigItem(
            position = 5,
            keyName = "showObject",
            name = "Show trail objects",
            description = "Show highlights for mushrooms, mud, seaweed, etc.",
            section = GENERAL_SECTION
    )
    default boolean isObjectShown()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
            position = 6,
            keyName = "colorGameObject",
            name = "Trail object color",
            description = "Color for mushrooms, mud, seaweed, etc.",
            section = GENERAL_SECTION
    )
    default Color getObjectColor()
    {
        return Color.CYAN;
    }

    @ConfigItem(
            position = 7,
            keyName = "showTrail",
            name = "Show trail",
            description = "Show highlights for trail prints.",
            section = GENERAL_SECTION
    )
    default boolean isTrailShown()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
            position = 8,
            keyName = "colorTrail",
            name = "Trail color",
            description = "Color for mushrooms, mud, seaweed, etc.",
            section = GENERAL_SECTION
    )
    default Color getTrailColor()
    {
        return Color.WHITE;
    }
}
