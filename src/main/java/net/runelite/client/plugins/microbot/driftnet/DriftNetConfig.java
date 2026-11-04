package net.runelite.client.plugins.microbot.driftnet;

import net.runelite.client.config.*;

@ConfigGroup(DriftNetConfig.GROUP)
@ConfigInformation(
        "<b>Drift Net Fishing</b><br />" +
        "<br />" +
        "Wear a fishbowl helmet and diving apparatus, and bring a trident (or leave your weapon slot empty). " +
        "Start next to the underwater entrance, or turn on Travel to Drift Net to start from anywhere.<br /><br />" +
        "<b>Travel</b> walks to the Fossil Island bank, loads your chosen inventory setup, dives from the rowboat " +
        "and swims to the nets. Leave a free slot so your weapon and shield can be taken off.<br /><br />" +
        "<b>Entry fee:</b> the script pays Ceto's 200-numulite daily fee, so carry numulites.<br />" +
        "<b>Nets:</b> noted drift nets in your inventory are handed to Annette automatically.<br />" +
        "<b>Camera and run:</b> the script sets the camera and drinks stamina potions if you carry them.<br />" +
        "In-world net, shoal and Annette highlights are no longer drawn; use the RuneLite Drift Net plugin for those.<br />"
)
public interface DriftNetConfig extends Config
{
    String GROUP = "driftnet";

    @ConfigSection(
            name = "Drift Net",
            description = "Drift net fishing options",
            position = 0,
            closedByDefault = false
    )
    String DRIFT_SECTION = "drift";

    @ConfigSection(
            name = "Behavior",
            description = "Mouse behavior",
            position = 1,
            closedByDefault = false
    )
    String BEHAVIOR_SECTION = "behavior";

    @ConfigSection(
            name = "Smart Breaks",
            description = "AFK/logout break controls",
            position = 2,
            closedByDefault = false
    )
    String BREAKS_SECTION = "breaks";

    @ConfigItem(
            keyName = "bankFish",
            name = "Bank drift-net fish",
            description = "Use the drift-net reward interface to bank fish when looting a full net.",
            position = 0,
            section = DRIFT_SECTION
    )
    default boolean driftBankFish()
    {
        return false;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
            keyName = "timeoutDelay",
            name = "Tag timeout (ticks)",
            description = "How long a chased shoal stays tagged before it can be selected again.",
            position = 1,
            section = DRIFT_SECTION
    )
    default int driftTagTimeout()
    {
        return 60;
    }

    @ConfigItem(
            keyName = "driftAutoTravel",
            name = "Travel to Drift Net",
            description = "Walk to the Fossil Island bank from anywhere, load your setup and dive to the nets. Below 74 Agility the north rowboat is used. Needs a setup selected below.",
            position = 2,
            section = DRIFT_SECTION
    )
    default boolean driftAutoTravel()
    {
        return true;
    }

    @ConfigItem(
            keyName = "driftInventorySetup",
            name = "Drift Net inventory setup",
            description = "Include fishbowl helmet, diving apparatus, trident, numulites and nets. Leave room for unequipping weapon and shield before diving.",
            position = 3,
            section = DRIFT_SECTION
    )
    default net.runelite.client.plugins.microbot.inventorysetups.InventorySetup driftInventorySetup()
    {
        return null;
    }

    @ConfigItem(
            keyName = "mouseActivity",
            name = "Mouse activity",
            description = "How much the mouse moves while fishing. AFK moves it off the game screen most of the time; Balanced and Active keep it on screen and move it more often.",
            position = 0,
            section = BEHAVIOR_SECTION
    )
    default DriftNetProfile.MouseActivity mouseActivity()
    {
        return DriftNetProfile.MouseActivity.BALANCED;
    }

    @ConfigItem(
            keyName = "smartBreaks",
            name = "Enable smart breaks",
            description = "Take short AFK or logout breaks. This is separate from the Break Handler plugin; do not use both.",
            position = 0,
            section = BREAKS_SECTION
    )
    default boolean smartBreaks()
    {
        return false;
    }

    @Range(min = 1, max = 1440)
    @ConfigItem(
            keyName = "minBreakInterval",
            name = "Min break interval (minutes)",
            description = "Minimum time until the next smart break.",
            position = 1,
            section = BREAKS_SECTION
    )
    default int minBreakInterval()
    {
        return 20;
    }

    @Range(min = 1, max = 1440)
    @ConfigItem(
            keyName = "maxBreakInterval",
            name = "Max break interval (minutes)",
            description = "Maximum time until the next smart break.",
            position = 2,
            section = BREAKS_SECTION
    )
    default int maxBreakInterval()
    {
        return 140;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "logoutBreakChance",
            name = "Logout break chance (%)",
            description = "Chance that a scheduled smart break logs out instead of remaining logged in AFK.",
            position = 3,
            section = BREAKS_SECTION
    )
    default int logoutBreakChance()
    {
        return 40;
    }

    @Range(min = 1, max = 240)
    @ConfigItem(
            keyName = "afkBreakMin",
            name = "AFK break min (minutes)",
            description = "Minimum logged-in AFK break duration.",
            position = 4,
            section = BREAKS_SECTION
    )
    default int afkBreakMin()
    {
        return 2;
    }

    @Range(min = 1, max = 240)
    @ConfigItem(
            keyName = "afkBreakMax",
            name = "AFK break max (minutes)",
            description = "Maximum logged-in AFK break duration.",
            position = 5,
            section = BREAKS_SECTION
    )
    default int afkBreakMax()
    {
        return 6;
    }

    @Range(min = 1, max = 240)
    @ConfigItem(
            keyName = "logoutBreakMin",
            name = "Logout break min (minutes)",
            description = "Minimum logout-break duration.",
            position = 6,
            section = BREAKS_SECTION
    )
    default int logoutBreakMin()
    {
        return 5;
    }

    @Range(min = 1, max = 240)
    @ConfigItem(
            keyName = "logoutBreakMax",
            name = "Logout break max (minutes)",
            description = "Maximum logout-break duration.",
            position = 7,
            section = BREAKS_SECTION
    )
    default int logoutBreakMax()
    {
        return 40;
    }

    @Range(min = 0, max = 30)
    @ConfigItem(
            keyName = "postLoginSettle",
            name = "Post-login settle (seconds)",
            description = "Seconds to wait after logging back in from a break before the script acts.",
            position = 8,
            section = BREAKS_SECTION
    )
    default int postLoginSettle()
    {
        return 3;
    }
}
