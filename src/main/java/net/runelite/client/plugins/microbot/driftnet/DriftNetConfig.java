package net.runelite.client.plugins.microbot.driftnet;

import net.runelite.client.config.*;

@ConfigGroup(DriftNetConfig.GROUP)
@ConfigInformation(
        "<b>Drift Net Fishing</b><br />" +
        "<br />" +
        "<i>Drift net start next to Ceto, bring fishbowl helmet, diving apparatus, & trident. Auto pays 200 Numulite per day. " +
        "Selecting Travel to Drift Net will use Rs2Walker to take you out to sea, bank, use your pre-set InventorySetup, " +
        "and automatically start from anywhere in the game. Manually give noted nets to Annette.</i><br />"
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
            keyName = "driftBankFish",
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
            keyName = "driftTagTimeout",
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
            description = "Use Rs2Walker to House on the Hill, then the zip line, island bank and dive. Uses the north rowboat below 74 Agility. Requires a selected setup.",
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
            description = "Mouse profile. AFK favors complete off-screen parking while fishing; Balanced and Active remain more present.",
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
            description = "Use AFK/logout breaks.",
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
            description = "Quiet settle time after DriftNetProfile returns from a logout break.",
            position = 8,
            section = BREAKS_SECTION
    )
    default int postLoginSettle()
    {
        return 3;
    }
}
