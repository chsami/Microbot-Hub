package net.runelite.client.plugins.microbot.dropyramidplunder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup(PyramidPlunderConfig.GROUP)
@ConfigInformation(
        "Pyramid Plunder in Sophanem. Start in Sophanem with 21+ Thieving.<br/><br/>"
                + "Banking needs the quest <b>Contact!</b>; without it the script stops when food runs out.<br/>"
                + "A Prayer book with a holy or unholy symbol is recommended: it cures the urn snakes' poison.<br/><br/>"
                + "Urns are looted in the last two rooms your level allows, the Grand Gold Chest in every room.")
public interface PyramidPlunderConfig extends Config {
    String GROUP = "DroPyramidPlunder";

    @ConfigSection(name = "Pyramid Plunder", description = "World, looting and gear", position = 0)
    String generalSection = "general";

    @ConfigSection(name = "Food & Prayer", description = "Food, banking and Protect from Melee", position = 1)
    String foodSection = "food";

    @ConfigSection(name = "Dro Behavior Profile",
            description = "Shared BaseProfileDro input timing and mouse parking. Breaks stay with the break handler.", position = 2)
    String behaviorProfileSection = "behaviorProfileSection";


    @ConfigItem(keyName = "hopToPlunderWorld", name = "Hop to Pyramid Plunder world",
            description = "Hop to world 493, the official Pyramid Plunder world, before entering the pyramid. "
                    + "Players there use the same door, so the right entrance is found faster.",
            position = 0, section = generalSection)
    default boolean hopToPlunderWorld() { return true; }

    @ConfigItem(keyName = "betterXpMode", name = "Better XP mode",
            description = "Urns only in rooms 7 and 8, no sarcophagi, and the Grand Gold Chest in every room. "
                    + "Room 7 gets about 1 min 30 for its urns before moving on to room 8. "
                    + "Needs 81 Thieving for room 7; below that the normal rooms are used. Off: the normal plan below.",
            position = 1, section = generalSection)
    default boolean betterXpMode() { return true; }

    @ConfigItem(keyName = "openSarcophagi", name = "Open sarcophagi (rooms 4+)",
            description = "Open the sarcophagus from room 4 onward for a Pharaoh's sceptre chance of 1/950 to 1/650. "
                    + "They give Strength experience only and take about ten seconds each; turn off for the best Thieving rate. "
                    + "Not used in Better XP mode, which never opens them.",
            position = 2, section = generalSection)
    default boolean openSarcophagi() { return true; }

    @ConfigItem(keyName = "inventorySetup", name = "Inventory Setup",
            description = "Optional. When selected, its gear and inventory are loaded on the next food restock at the Sophanem bank "
                    + "(needs Contact!). Leave empty to use whatever the player is wearing and carrying.",
            position = 3, section = generalSection)
    default InventorySetup inventorySetup() { return null; }

    @ConfigItem(keyName = "food", name = "Food",
            description = "Food withdrawn at the Sophanem bank; any food already carried is eaten too.",
            position = 0, section = foodSection)
    default Rs2Food food() { return Rs2Food.KARAMBWAN; }

    @Range(min = 1, max = 26)
    @ConfigItem(keyName = "foodAmount", name = "Food amount",
            description = "Food carried after a bank trip. Free slots are needed for artefacts, which are dropped when the inventory fills.",
            position = 1, section = foodSection)
    default int foodAmount() { return 14; }

    @Range(min = 0, max = 26)
    @ConfigItem(keyName = "minFood", name = "Restock at or below",
            description = "Bank between runs when this many food items or fewer are left.",
            position = 2, section = foodSection)
    default int minFood() { return 3; }

    @Range(min = 10, max = 90)
    @ConfigItem(keyName = "eatBelow", name = "Eat below HP %",
            description = "Eat at about this health percentage; the exact point varies a little each time.",
            position = 3, section = foodSection)
    default int eatBelow() { return 55; }

    @ConfigItem(keyName = "protectFromMelee", name = "Protect from Melee",
            description = "Pray Protect from Melee while a scarab swarm or mummy is on you (43 Prayer). "
                    + "It is switched off again once they are gone, to save prayer points.",
            position = 4, section = foodSection)
    default boolean protectFromMelee() { return true; }

    @Range(min = 0, max = 99)
    @ConfigItem(keyName = "restorePrayerBelow", name = "Restore prayer below",
            description = "On a food restock trip, use the altar if prayer is below this. Low prayer alone never starts a trip.",
            position = 5, section = foodSection)
    default int restorePrayerBelow() { return 20; }

    @ConfigItem(keyName = "mouseActivity", name = "Mouse Activity",
            description = "How often the mouse leaves the client while waiting, through BaseProfileDro.",
            position = 0, section = behaviorProfileSection)
    default BaseProfileDro.MouseActivity mouseActivity() { return BaseProfileDro.MouseActivity.ACTIVE; }

    @ConfigItem(keyName = "parkSide", name = "Mouse park side",
            description = "Side of the client the mouse parks at while waiting.",
            position = 1, section = behaviorProfileSection)
    default BaseProfileDro.AfkParkSide parkSide() { return BaseProfileDro.AfkParkSide.RANDOM; }
}
