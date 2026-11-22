package net.runelite.client.plugins.microbot.herbihunter;

import net.runelite.client.config.*;

import java.awt.*;

/** Key names are the saved settings: renaming one loses everyone's value. RuneLite's panel cannot hide an unused option. */
@ConfigInformation("<html><b>Herbi Hunter " + HerbiHunterPlugin.version + "</b><br>"
        + "Hunts herbiboars on Fossil Island: follows the trail, attacks the tunnel, harvests the herbs and banks at"
        + " the Museum Camp.<br><br>"
        + "<b>You need</b><br>"
        + "&bull; 80 Hunter (boosts work)<br>"
        + "&bull; 31 Herblore<br>"
        + "&bull; Bone Voyage (access to Fossil Island)<br><br>"
        + "<b>Recommended</b><br>"
        + "&bull; Stamina potions in the bank<br>"
        + "&bull; Herb sack, magic secateurs<br>"
        + "&bull; 70 Agility (hole shortcut)<br><br>"
        + "Start in the Mushroom Forest on Fossil Island.<br><br>"
        + "Created by Igor, with work from chsami and Kavraka91. Reworked by irkedMATT.</html>")
@ConfigGroup(HerbiboarConfig.GROUP)
public interface HerbiboarConfig extends Config {

    String GROUP = "HerbiHunter";
    String INFO_PANEL_KEY = "infoPanel";

    enum InfoPanel {
        FULL("Full"),
        COMPACT("Compact"),
        OFF("Off");

        private final String name;

        InfoPanel(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum LogDetail {
        QUIET("Quiet"),
        NORMAL("Normal"),
        DETAILED("Detailed");

        private final String name;

        LogDetail(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum PlayStyle {
        RELAXED("Relaxed"),
        FOCUSED("Focused");

        private final String name;

        PlayStyle(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum RunEnergyOption {
        NONE("None"),
        STAMINA_POTION("Stamina potion"),
        EXTENDED_STAMINA_POTION("Extended stamina potion"),
        EXTREME_ENERGY_POTION("Extreme energy potion"),
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
            name = "Setup",
            description = "How the session runs: the run energy item, what each bank trip brings, and what happens if the"
                    + " character gets stuck. A bank trip happens when the inventory is full or a supply runs out.",
            position = 0
    )
    String SETUP_SECTION = "supplies";

    @ConfigItem(
            keyName = "playStyle",
            name = "Play style",
            description = "Relaxed: a natural pace that varies from session to session, with short pauses and glances."
                    + " Focused: fast and sharp, for when you are watching.",
            section = SETUP_SECTION,
            position = 0
    )
    default PlayStyle playStyle() {
        return PlayStyle.RELAXED;
    }

    @ConfigItem(
            keyName = "runEnergyOption",
            name = "Run energy item",
            description = "Withdrawn at the bank (6 potions or 6 strange fruit) and used to keep running. None: no item.",
            section = SETUP_SECTION,
            position = 1
    )
    default RunEnergyOption runEnergyOption() {
        return RunEnergyOption.NONE;
    }

    @ConfigItem(
            keyName = "thresholdEnergy",
            name = "Drink at run energy",
            description = "Use the run energy item once run energy is at or below this. Only with a run energy item chosen.",
            section = SETUP_SECTION,
            position = 2
    )
    @Units(Units.PERCENT)
    @Range(min = 1, max = 100)
    default int thresholdEnergy() {
        return 50;
    }

    @ConfigItem(
            keyName = "stamBuffAlwaysActive",
            name = "Keep stamina effect up",
            description = "Sip again as soon as the stamina effect ends, at any run energy. Only with Stamina potion chosen.",
            section = SETUP_SECTION,
            position = 3
    )
    default boolean stamBuffAlwaysActive() {
        return false;
    }

    @ConfigItem(
            keyName = "dropEmptyVials",
            name = "Drop empty vials",
            description = "Drop the vial a finished potion leaves behind. Only with a potion chosen.",
            section = SETUP_SECTION,
            position = 4
    )
    default boolean dropEmptyVials() {
        return false;
    }

    @ConfigItem(
            keyName = "useHerbSack",
            name = "Herb sack",
            description = "Bring a herb sack (or silk-lined one) and keep it open: harvested herbs go into it until it"
                    + " holds 30 of a type. It is emptied into the bank on every trip.",
            section = SETUP_SECTION,
            position = 5
    )
    default boolean useHerbSack() {
        return false;
    }

    @ConfigItem(
            keyName = "useMagicSecateurs",
            name = "Magic secateurs",
            description = "Bring magic secateurs and wear them: one extra herb per harvest.",
            section = SETUP_SECTION,
            position = 6
    )
    default boolean useMagicSecateurs() {
        return false;
    }

    @ConfigItem(
            keyName = "resetIfStuck",
            name = "Recover when stuck",
            description = "Standing still for a minute: pick the trail back up from the game's state. Three minutes in a"
                    + " row: log out and stop. Not checked during a bank visit or a restock trip.",
            section = SETUP_SECTION,
            position = 7
    )
    default boolean resetIfStuck() {
        return true;
    }

    @ConfigItem(
            keyName = "logoutAt99",
            name = "Log out at 99 Hunter",
            description = "Reaching level 99 Hunter during this session: finish the catch, then log out and stop."
                    + " Does nothing if the character already has 99 when the plugin starts.",
            section = SETUP_SECTION,
            position = 8
    )
    default boolean logoutAt99() {
        return false;
    }

    @ConfigItem(
            keyName = "logDetail",
            name = "Log detail",
            description = "How much Herbi Hunter writes to client.log. Normal: one line per catch plus banking and recoveries."
                    + " Detailed: everything, set this before reporting a bug. Quiet: problems, plus one line when a run starts and stops.",
            section = SETUP_SECTION,
            position = 9
    )
    default LogDetail logDetail() {
        return LogDetail.NORMAL;
    }

    @ConfigSection(
            name = "Restock",
            description = "Buy more of the run energy potion at the Grand Exchange when the bank runs out.",
            position = 1,
            closedByDefault = true
    )
    String RESTOCK_SECTION = "restockSection";

    @ConfigItem(
            keyName = "restock",
            name = "Buy potions when out",
            description = "When the bank has none of the run energy potion left: take coins to the Grand Exchange, buy"
                    + " the amount below at 10% over its price, bank them there and come back to hunt. Teleports are"
                    + " allowed on this trip only. Not enough coins, no price, or an offer still unfilled after 3"
                    + " minutes: logs out with the reason. Potions only, not strange fruit. Off: running out logs out.",
            section = RESTOCK_SECTION,
            position = 0
    )
    default boolean restock() {
        return false;
    }

    @ConfigItem(
            keyName = "restockAmount",
            name = "Potions to buy",
            description = "4-dose potions bought on each trip, from 1 to 2,000 (the Grand Exchange buy limit). Only with Buy potions when out on.",
            section = RESTOCK_SECTION,
            position = 1
    )
    @Range(min = 1, max = 2000)
    default int restockAmount() {
        return 100;
    }

    @ConfigSection(
            name = "Drop fossils",
            description = "Unidentified fossils from inspecting the trail, dropped instead of banked.",
            position = 2,
            closedByDefault = true
    )
    String FOSSILS_SECTION = "fossils";

    @ConfigItem(keyName = "dropSmallFossil", name = "Small fossil",
            description = "Drop unidentified small fossils instead of banking them.", section = FOSSILS_SECTION, position = 0)
    default boolean dropSmallFossil() { return false; }

    @ConfigItem(keyName = "dropMediumFossil", name = "Medium fossil",
            description = "Drop unidentified medium fossils instead of banking them.", section = FOSSILS_SECTION, position = 1)
    default boolean dropMediumFossil() { return false; }

    @ConfigItem(keyName = "dropLargeFossil", name = "Large fossil",
            description = "Drop unidentified large fossils instead of banking them.", section = FOSSILS_SECTION, position = 2)
    default boolean dropLargeFossil() { return false; }

    @ConfigItem(keyName = "dropRareFossil", name = "Rare fossil",
            description = "Drop unidentified rare fossils instead of banking them.", section = FOSSILS_SECTION, position = 3)
    default boolean dropRareFossil() { return false; }

    @ConfigSection(
            name = "Drop herbs",
            description = "Grimy herbs dropped instead of banked. Only herbs that land in the inventory: with the herb"
                    + " sack open, herbs go into the sack until it holds 30 of that type, and those are banked.",
            position = 3,
            closedByDefault = true
    )
    String HERBS_SECTION = "herbs";

    @ConfigItem(keyName = "dropGuam", name = "Guam",
            description = "Drop grimy guam instead of banking it.", section = HERBS_SECTION, position = 0)
    default boolean dropGuam() { return false; }

    @ConfigItem(keyName = "dropMarrentill", name = "Marrentill",
            description = "Drop grimy marrentill instead of banking it.", section = HERBS_SECTION, position = 1)
    default boolean dropMarrentill() { return false; }

    @ConfigItem(keyName = "dropTarromin", name = "Tarromin",
            description = "Drop grimy tarromin instead of banking it.", section = HERBS_SECTION, position = 2)
    default boolean dropTarromin() { return false; }

    @ConfigItem(keyName = "dropHarralander", name = "Harralander",
            description = "Drop grimy harralander instead of banking it.", section = HERBS_SECTION, position = 3)
    default boolean dropHarralander() { return false; }

    @ConfigItem(keyName = "dropRanarr", name = "Ranarr",
            description = "Drop grimy ranarr weed instead of banking it.", section = HERBS_SECTION, position = 4)
    default boolean dropRanarr() { return false; }

    @ConfigItem(keyName = "dropToadflax", name = "Toadflax",
            description = "Drop grimy toadflax instead of banking it.", section = HERBS_SECTION, position = 5)
    default boolean dropToadflax() { return false; }

    @ConfigItem(keyName = "dropIrit", name = "Irit",
            description = "Drop grimy irit leaf instead of banking it.", section = HERBS_SECTION, position = 6)
    default boolean dropIrit() { return false; }

    @ConfigItem(keyName = "dropAvantoe", name = "Avantoe",
            description = "Drop grimy avantoe instead of banking it.", section = HERBS_SECTION, position = 7)
    default boolean dropAvantoe() { return false; }

    @ConfigItem(keyName = "dropKwuarm", name = "Kwuarm",
            description = "Drop grimy kwuarm instead of banking it.", section = HERBS_SECTION, position = 8)
    default boolean dropKwuarm() { return false; }

    @ConfigItem(keyName = "dropSnapdragon", name = "Snapdragon",
            description = "Drop grimy snapdragon instead of banking it.", section = HERBS_SECTION, position = 9)
    default boolean dropSnapdragon() { return false; }

    @ConfigItem(keyName = "dropCadantine", name = "Cadantine",
            description = "Drop grimy cadantine instead of banking it.", section = HERBS_SECTION, position = 10)
    default boolean dropCadantine() { return false; }

    @ConfigItem(keyName = "dropLantadyme", name = "Lantadyme",
            description = "Drop grimy lantadyme instead of banking it.", section = HERBS_SECTION, position = 11)
    default boolean dropLantadyme() { return false; }

    @ConfigItem(keyName = "dropDwarfWeed", name = "Dwarf weed",
            description = "Drop grimy dwarf weed instead of banking it.", section = HERBS_SECTION, position = 12)
    default boolean dropDwarfWeed() { return false; }

    @ConfigItem(keyName = "dropTorstol", name = "Torstol",
            description = "Drop grimy torstol instead of banking it.", section = HERBS_SECTION, position = 13)
    default boolean dropTorstol() { return false; }

    @ConfigSection(
            name = "Overlay",
            description = "What is drawn in the game view and on the minimap.",
            position = 4
    )
    String OVERLAY_SECTION = "general";

    @ConfigItem(
            keyName = INFO_PANEL_KEY,
            name = "Info panel",
            description = "The session panel: Full, Compact (one line) or Off. Right-click the panel to minimise or expand it;"
                    + " hold Alt to drag it.",
            section = OVERLAY_SECTION,
            position = 0
    )
    default InfoPanel infoPanel() {
        return InfoPanel.FULL;
    }

    @ConfigItem(
            keyName = "showStart",
            name = "Start rocks",
            description = "Highlight the rocks a trail starts from, while no trail is running.",
            section = OVERLAY_SECTION,
            position = 1
    )
    default boolean isStartShown() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "colorStart",
            name = "Start rocks color",
            description = "Color of the start rock highlight. Only with Start rocks on.",
            section = OVERLAY_SECTION,
            position = 2
    )
    default Color getStartColor() {
        return Color.CYAN;
    }

    @ConfigItem(
            keyName = "showObject",
            name = "Next trail spot",
            description = "Highlight the mushroom, mud, seaweed or other spot the trail leads to next.",
            section = OVERLAY_SECTION,
            position = 3
    )
    default boolean isObjectShown() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "colorGameObject",
            name = "Next trail spot color",
            description = "Color of the next trail spot highlight. Only with Next trail spot on.",
            section = OVERLAY_SECTION,
            position = 4
    )
    default Color getObjectColor() {
        return Color.CYAN;
    }

    @ConfigItem(
            keyName = "showTunnel",
            name = "Tunnel",
            description = "Highlight the tunnel the trail ends at.",
            section = OVERLAY_SECTION,
            position = 5
    )
    default boolean isTunnelShown() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "colorTunnel",
            name = "Tunnel color",
            description = "Color of the tunnel highlight. Only with Tunnel on.",
            section = OVERLAY_SECTION,
            position = 6
    )
    default Color getTunnelColor() {
        return Color.GREEN;
    }

    @ConfigItem(
            keyName = "showRoute",
            name = "Walk route",
            description = "Draw the route being walked to the next target, on the ground and on the minimap.",
            section = OVERLAY_SECTION,
            position = 7
    )
    default boolean isRouteShown() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "colorRoute",
            name = "Walk route color",
            description = "Color of the walk route line. Only with Walk route on.",
            section = OVERLAY_SECTION,
            position = 8
    )
    default Color getRouteColor() {
        return new Color(255, 200, 0, 200);
    }

    @ConfigItem(
            keyName = "showClickboxes",
            name = "Outline instead of tile",
            description = "Draw the next trail spot and the tunnel as their clickable outline instead of their tile."
                    + " Start rocks always show as a tile.",
            section = OVERLAY_SECTION,
            position = 9
    )
    default boolean showClickBoxes() {
        return false;
    }
}
