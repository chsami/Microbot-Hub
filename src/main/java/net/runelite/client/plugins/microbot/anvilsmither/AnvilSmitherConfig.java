package net.runelite.client.plugins.microbot.anvilsmither;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.anvilsmither.enums.AnvilItem;
import net.runelite.client.plugins.microbot.anvilsmither.enums.AnvilLocation;
import net.runelite.client.plugins.microbot.anvilsmither.enums.Bars;

// keyNames are saved settings: reorder and relabel freely, never rename one
@ConfigGroup(AnvilSmitherConfig.GROUP)
@ConfigInformation(
        "<b>Anvil Smither " + AnvilSmitherPlugin.version + "</b><br>"
        + "Smiths bars into items at an anvil next to a bank, and banks each load.<br><br>"
        + "<b>You need</b><br>"
        + "&bull; The Smithing level for the item<br>"
        + "&bull; Bars in the bank (for Large keel parts: Keel parts of that metal)<br>"
        + "&bull; A hammer or Imcando hammer, in the bank, inventory or worn<br>"
        + "&bull; Song of the Elves for Prifddinas<br>"
        + "&bull; Pandemonium for keel parts<br>"
        + "&bull; For alching: nature runes, fire runes or a fire staff, and the game's Alchemy warning value set above your item<br><br>"
        + "<b>Recommended</b><br>"
        + "&bull; An Imcando hammer worn: one more bar each load<br><br>"
        + "<b>Where to start</b><br>"
        + "Stand at the bank of the location you pick, then turn the plugin on. It does not travel between cities.<br><br>"
        + "<b>Sailing keels</b><br>"
        + "Pick the metal as the bar, then Keel parts. Large keel parts are made from Keel parts of that metal,"
        + " not from bars."
)
public interface AnvilSmitherConfig extends Config {
    String GROUP = "AnvilSmither";
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

    @ConfigSection(
            name = "Setup",
            description = "Where to smith and what to make.",
            position = 0
    )
    String SETUP_SECTION = "setup";

    @ConfigSection(
            name = "Overlay",
            description = "The on-screen panel.",
            position = 1
    )
    String OVERLAY_SECTION = "overlay";

    @ConfigItem(keyName = "location", name = "Location",
            description = "Which anvil to work at. Start at its bank, the plugin does not travel there.",
            section = SETUP_SECTION, position = 0)
    default AnvilLocation sLocation() {
        return AnvilLocation.VARROCK_WEST;
    }

    @ConfigItem(keyName = "barType", name = "Bar",
            description = "The bar to smith with. For keel parts this picks the keel's metal.",
            section = SETUP_SECTION, position = 1)
    default Bars sBarType() {
        return Bars.BRONZE;
    }

    @ConfigItem(keyName = "smithObject", name = "Item",
            description = "What to make. Items named with a metal, such as Bronze wire or Iron spit, need that bar.",
            section = SETUP_SECTION, position = 2)
    default AnvilItem sAnvilItem() {
        return AnvilItem.SCIMITAR;
    }

    @ConfigItem(keyName = "alch", name = "Alch what was made",
            description = "When the bars run out, take the items of that kind out of the bank as notes and cast High Alchemy on them"
                    + " (Low Alchemy below 55 Magic). It takes every one in the bank, including any from earlier runs."
                    + " Runes can be in the bank or a rune pouch; a fire staff can be worn or in the bank. Keel parts and stackable items (nails, dart tips, arrowtips, knives, bolts) are never alched.",
            section = SETUP_SECTION, position = 3)
    default boolean sAlch() {
        return false;
    }

    @ConfigItem(keyName = "logout", name = "Log out when finished",
            description = "Log out when the plugin stops because the bars or the hammer ran out, or it could not recover. Off leaves you standing at the bank.",
            section = SETUP_SECTION, position = 4)
    default boolean sLogout() {
        return true;
    }

    @ConfigItem(keyName = "debug", name = "Detailed log",
            description = "Write every decision to the log, not only the milestones. Turn on for bug reports.",
            section = SETUP_SECTION, position = 5)
    default boolean sDebug() {
        return false;
    }

    @ConfigItem(keyName = INFO_PANEL_KEY, name = "Info panel",
            description = "The session panel: Full, Compact (one line) or Off. Right-click the panel to minimise or expand it;"
                    + " hold Alt to drag it.",
            section = OVERLAY_SECTION, position = 0)
    default InfoPanel infoPanel() {
        return InfoPanel.FULL;
    }
}
