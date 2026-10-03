package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Flipper Config")
public interface FlipperConfig extends Config {

    enum SelectionMethod {
        HOTKEY("Hotkey (E)"),
        MOUSE("Mouse");

        private final String name;

        SelectionMethod(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** How a slot is worked when Copilot asks for a modify or an abort. */
    enum SlotAction {
        COPILOT_LEFT_CLICK("On", "Copilot left-click swap"),
        MENU_OPTION("Off", "Slot menu action");

        private final String label;
        final String actionDescription;

        SlotAction(String label, String actionDescription) {
            this.label = label;
            this.actionDescription = actionDescription;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigItem(
        keyName = "slotActionMode",
        name = "Copilot left-click swap",
        description = "On: use Copilot's swapped left-click for Modify/Abort (enable slot swap in Copilot too). "
            + "Off: select the supported Modify/Abort slot action directly. "
            + "This setting controls GE Flipper and does not change Copilot's own setting. "
            + "Hotkey/Mouse above controls price and quantity input.",
        position = 2
    )
    default SlotAction slotAction() {
        return SlotAction.COPILOT_LEFT_CLICK;
    }


    @ConfigItem(
        keyName = "verboseLogging",
        name = "Verbose Logging",
        description = "Log this plugin's own activity at INFO instead of WARN. Leave it off to keep "
            + "the in-game chat quiet; turn it on when you need a full trace of what the plugin did. "
            + "This affects only this plugin's own logger - no other script's logging is touched.",
        position = 90
    )
    default boolean verboseLogging() {
        return false;
    }

    @ConfigItem(
        keyName = "guide",
        name = "How to use",
        description = "How to use this plugin",
        position = 0
    )
    default String GUIDE() {
        return "Automates the Flipping copilot plugin from the plugin hub,  \n" +
        "1.Make sure to have to have the flipping copilot plugin downloaded from the plugin hub,"+
        "and make sure to make an account, log in, and resume suggestions from flipping copilot  \n" +
        "2.have gp in inventory or bank(1m+ starting is recommended)  \n" +
        "3.preferably start at the ge or have a tele close to the ge in your inventory  \n" +
        "~made by chocken   \n" +
        "Extra tip: In game settings, disable grand exchange warnings for offers with the price too low/high, otherwise the script will get stuck.";
    }

    @ConfigItem(
        keyName = "selectionMethod",
        name = "Suggestion Selection",
        description = "Choose whether to use the hotkey (E) or mouse clicks to select what Copilot suggests",
        position = 1
    )
    default SelectionMethod selectionMethod() {
        return SelectionMethod.HOTKEY;
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Overlay",
        description = "Display profit and GP/hr overlay on the top-left of the screen",
        position = 3
    )
    default boolean showOverlay() {
        return true;
    }

}
