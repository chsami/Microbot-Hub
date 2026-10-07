package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

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
        "GE Flipper confirms Grand Exchange price warning popups. Your Copilot, game, and Microbot settings stay under your control.";
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

    @ConfigItem(
        keyName = "waitingMouseOffScreen",
        name = "Move mouse off screen while waiting",
        description = "Randomly move the game cursor outside the canvas while Copilot says Wait. "
            + "Adjust the Randomization slider below. "
            + "Uses Microbot's mouse movement without changing its shared antiban settings.",
        position = 4
    )
    default boolean waitingMouseOffScreen() {
        return false;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "waitingMouseChance",
        name = "Randomization",
        description = "Randomizes waiting mouse movement using one slider for chance and both automatic delay limits. "
            + "Move right for sooner, more frequent randomized movement; the far-left position disables movement.",
        position = 5
    )
    default int waitingMouseChance() {
        return 30;
    }

    @ConfigItem(
        keyName = "finish",
        name = "End / Finish",
        description = "Cancel buy offers, collect purchased items, and follow Copilot's sell/Modify suggestions. "
            + "Stop GE Flipper once all items are listed; existing sell offers remain on the exchange. "
            + "Uses Copilot's sell and modify suggestions. Available while GE Flipper is running.",
        position = 6
    )
    default ConfigButton finish() {
        return null;
    }

}
