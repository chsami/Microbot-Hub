package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.toweroflife_creaturecreation.enums.ToLCreature;

@ConfigGroup("TowerOfLifeCC")
@ConfigInformation("<center>" +
        "Requires:<br>" +
        "Items to summon selected creature stocked in bank.<br>" +
        "<br>" +
        "It will use your currently equipped gear to kill creatures with.<br>" +
        "Start with an empty inventory except your teleport runes/tab<br>" +
        "<br>" +
        "<b>! WARNING !</b><br>" +
        "This is not a particularly dangerous activity, I have not handled safety if you are really low level.<br>" +
        "<br>" +
        "Uses Ardougne South Bank<br>" +
        "--> Start at the bank &lt;--<br><br>" +
        "Ironmen rejoice and collect your secondaries!" +
        "</center>")
public interface TowerOfLifeCCConfig extends Config {
    @ConfigItem(
            name = "Selected Creature",
            keyName = "selectedCreature",
            description = "Which creature to create, kill, and loot.",
            position = 0
    )
    default ToLCreature SelectedCreature() { return ToLCreature.UNICOW; }

    @ConfigSection(
            name = "Restoring Health",
            description = "Settings related to healing HP while banking",
            position = 0,
            closedByDefault = true
    )
    String restoringHealth = "Restoring Health";

    @ConfigItem(
            name = "Eat to Full HP at Bank",
            keyName = "eatFoodAtBank",
            description = "Whether the bot will eat food to restore hp when banking",
            position = 0,
            section = restoringHealth
    )
    default boolean EatFoodAtBank() { return true; }

    @ConfigItem(
            name = "Prefer Lowest Healing Food First",
            keyName = "preferLowestHealingFood",
            description = "If TRUE: Bot will heal to full with lowest healing food first.<br>If FALSE: Bot will heal to full with highest healing food first.<br>" +
                    "It will eat food from your inventory if you somehow happen to have food in inventory, first.",
            position = 1,
            section = restoringHealth
    )
    default boolean PreferLowestHealingFood() { return false; }
}
