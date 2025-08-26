package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
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
        "Uses Ardougne South Bank" +
        "--> Start at the bank &lt;--<br>" +
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

    @ConfigItem(
            name = "Pickup Satchels",
            keyName = "pickupSatchels",
            description = "Whether the bot should bother to pickup any satchel that drops",
            position = 1
    )
    default boolean PickupSatchels() { return false; }
}
