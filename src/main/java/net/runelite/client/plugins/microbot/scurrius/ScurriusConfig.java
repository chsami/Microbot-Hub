package net.runelite.client.plugins.microbot.scurrius;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.scurrius.enums.PrayerMode;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup("Scurrius")
public interface ScurriusConfig extends Config {

    @ConfigSection(
            name = "General Settings",
            description = "General settings for the script",
            position = 0
    )
    String generalSettings = "generalSettings";

    @ConfigSection(
            name = "Food Settings",
            description = "Settings for food selection and usage",
            position = 1
    )
    String foodSettings = "foodSettings";

    @ConfigSection(
            name = "Prayer Settings",
            description = "Settings for prayer potions and prayer usage",
            position = 2
    )
    String prayerSettings = "prayerSettings";

    @ConfigSection(
            name = "Emergency teleport",
            description = "Break a Varrock teleport, bank and come back when food and health are low",
            position = 4
    )
    String emergencySettings = "emergencySettings";

    @ConfigSection(
            name = "Restock trips",
            description = "Regular bank trips, even when supplies are not out",
            position = 5
    )
    String restockSettings = "restockSettings";

    @ConfigSection(
            name = "Breaks",
            description = "Take a break after a number of bank trips (any bank visit after fighting)",
            position = 6
    )
    String breakSettings = "breakSettings";

    @ConfigSection(
            name = "Anti-ban",
            description = "Human-like behaviour",
            position = 7
    )
    String antibanSettings = "antibanSettings";

    @ConfigSection(
            name = "Special attack",
            description = "Use the special attack on Scurrius whenever it is available",
            position = 8
    )
    String specSettings = "specSettings";

    @ConfigSection(
            name = "Loot Settings",
            description = "Settings for looting items",
            position = 3
    )
    String lootSettings = "lootSettings";

    @ConfigItem(
            keyName = "bossRoomEntryType",
            name = "Public or Private",
            description = "Select whether to enter through Private or Normal entrance",
            position = 0,
            section = generalSettings
    )
    default BossRoomEntryType bossRoomEntryType() {
        return BossRoomEntryType.PRIVATE;
    }

    @ConfigItem(
            keyName = "prioritizeGiantRats",
            name = "Prioritize Giant Rats",
            description = "Prioritize Giant Rats over other targets",
            position = 1,
            section = generalSettings
    )
    default boolean prioritizeRats() {
        return false;
    }

    @ConfigItem(
            keyName = "teleportAmount",
            name = "Teleport Item Amount",
            description = "Amount of Teleports to withdraw",
            position = 2,
            section = generalSettings
    )
    default int teleportAmount() {
        return 5;
    }

    @ConfigItem(
            keyName = "shutdownAfterDeath",
            name = "Shutdown after death",
            description = "If enabled, script will stop after dying",
            position = 2,
            section = generalSettings
    )
    default boolean shutdownAfterDeath() {
        return false;
    }
    
    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory setup",
            description = "Pick a saved Inventory Setup to load at the bank (gear and supplies). Leave empty to use the food, potion and teleport "
                    + "amounts below. With a setup, the food, prayer doses and teleport tabs needed come from the setup. "
                    + "Put Varrock teleport tablets in it: they are the only teleport the script uses to leave the boss room.",
            position = 3,
            section = generalSettings
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = "foodSelection",
            name = "Select Food",
            description = "Select the type of food you want to use",
            position = 0,
            section = foodSettings
    )
    default Rs2Food foodSelection() {
        return Rs2Food.SHARK;
    }

    @ConfigItem(
            keyName = "foodAmount",
            name = "Food Amount",
            description = "Number of food items to withdraw",
            position = 1,
            section = foodSettings
    )
    @Range(
            min = 1,
            max = 28
    )
    default int foodAmount() {
        return 20;
    }

    @ConfigItem(
            keyName = "minEatPercent",
            name = "Min Eat Percent",
            description = "The minimum health percent at which to eat",
            position = 2,
            section = foodSettings
    )
    @Range(
            min = 1,
            max = 99
    )
    default int minEatPercent() {
        return 30;
    }

    @ConfigItem(
            keyName = "maxEatPercent",
            name = "Max Eat Percent",
            description = "The maximum health percent at which to eat",
            position = 3,
            section = foodSettings
    )
    @Range(
            min = 2,
            max = 100
    )
    default int maxEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "potionSelection",
            name = "Select Prayer Potion",
            description = "Select whether to use Prayer Potion or Super Restore",
            position = 0,
            section = prayerSettings
    )
    default PotionSelection potionSelection() {
        return PotionSelection.PRAYERPOTION;
    }

    @ConfigItem(
            keyName = "prayerPotionAmount",
            name = "Prayer Potion Amount",
            description = "Number of prayer potions to withdraw",
            position = 1,
            section = prayerSettings
    )
    @Range(
            min = 1,
            max = 28
    )
    default int prayerPotionAmount() {
        return 2;
    }

    @ConfigItem(
            keyName = "minPrayerPercent",
            name = "Min Prayer Percent",
            description = "The minimum prayer percent at which to drink a prayer potion",
            position = 2,
            section = prayerSettings
    )
    @Range(
            min = 1,
            max = 99
    )
    default int minPrayerPercent() {
        return 10;
    }

    @ConfigItem(
            keyName = "maxPrayerPercent",
            name = "Max Prayer Percent",
            description = "The maximum prayer percent at which to drink a prayer potion",
            position = 3,
            section = prayerSettings
    )
    @Range(
            min = 2,
            max = 100
    )
    default int maxPrayerPercent() {
        return 30;
    }

    @ConfigItem(
            keyName = "prayerMode",
            name = "Prayer mode",
            description = "Always on: the protection prayer stays on. Lazy flick: on only for the tick before a hit lands and the tick it lands. "
                    + "One-tick flick: switched off and on every tick so it never drains. Flicking falls back to always on until the hit "
                    + "timing is learned, after an unprotected hit, and while giant rats attack you."
                    + " Mixed (per kill): each kill picks always on or lazy flick, by the two weights below.",
            position = 4,
            section = prayerSettings
    )
    default PrayerMode prayerMode() {
        return PrayerMode.ALWAYS_ON;
    }

    @ConfigItem(
            keyName = "emergencyFoodLeft",
            name = "Food left",
            description = "Teleport out when this much food or less is left in the inventory (and health is under the percent below)",
            position = 0,
            section = emergencySettings
    )
    @Range(
            min = 0,
            max = 28
    )
    default int emergencyFoodLeft() {
        return 2;
    }

    @ConfigItem(
            keyName = "emergencyHpPercent",
            name = "Hitpoints percent",
            description = "Teleport out when hitpoints are under this percent of your real hitpoints level (and food is low)",
            position = 1,
            section = emergencySettings
    )
    @Range(
            min = 1,
            max = 99
    )
    default int emergencyHpPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "lazyFlickHoldMin",
            name = "Lazy flick hold min (ticks)",
            description = "Lazy flick: fewest extra ticks the prayer stays on after a hit before it is switched off",
            position = 5,
            section = prayerSettings
    )
    @Range(
            min = 0,
            max = 5
    )
    default int lazyFlickHoldMin() {
        return 1;
    }

    @ConfigItem(
            keyName = "lazyFlickHoldMax",
            name = "Lazy flick hold max (ticks)",
            description = "Lazy flick: most extra ticks the prayer stays on after a hit before it is switched off (random per attack)",
            position = 6,
            section = prayerSettings
    )
    @Range(
            min = 0,
            max = 5
    )
    default int lazyFlickHoldMax() {
        return 2;
    }

    @ConfigItem(
            keyName = "lazyFlickVariation",
            name = "Lazy flick variation",
            description = "Lazy flick: vary when the prayer goes on (one or two ticks early, a short random delay) and now and then keep it "
                    + "on for a whole attack instead of flicking. Never leaves a hit unprotected.",
            position = 7,
            section = prayerSettings
    )
    default boolean lazyFlickVariation() {
        return true;
    }

    @ConfigItem(
            keyName = "restockEnabled",
            name = "Restock trips",
            description = "After a random number of kills (between the two numbers below), once Scurrius is dead and the loot is picked up "
                    + "and there is food left, teleport out (or walk), bank and come back",
            position = 0,
            section = restockSettings
    )
    default boolean restockEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "restockMinKills",
            name = "Restock after at least (kills)",
            description = "Fewest kills between restock trips",
            position = 1,
            section = restockSettings
    )
    @Range(
            min = 1,
            max = 50
    )
    default int restockMinKills() {
        return 2;
    }

    @ConfigItem(
            keyName = "restockMaxKills",
            name = "Restock after at most (kills)",
            description = "Most kills between restock trips (the target is random between the two; swapped values are fine)",
            position = 2,
            section = restockSettings
    )
    @Range(
            min = 1,
            max = 50
    )
    default int restockMaxKills() {
        return 5;
    }

    @ConfigItem(
            keyName = "breakEnabled",
            name = "Breaks",
            description = "Right after restocking at the bank, take a break after a random number of restock trips. "
                    + "Use this or Microbot's Break Handler, not both.",
            position = 0,
            section = breakSettings
    )
    default boolean breakEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "breakMinTrips",
            name = "Break after at least (bank trips)",
            description = "Fewest bank visits (planned or emergency) between breaks",
            position = 1,
            section = breakSettings
    )
    @Range(
            min = 1,
            max = 50
    )
    default int breakMinTrips() {
        return 2;
    }

    @ConfigItem(
            keyName = "breakMaxTrips",
            name = "Break after at most (bank trips)",
            description = "Most bank visits between breaks; the target is random between the two and rolled again after each break",
            position = 2,
            section = breakSettings
    )
    @Range(
            min = 1,
            max = 50
    )
    default int breakMaxTrips() {
        return 2;
    }

    @ConfigItem(
            keyName = "breakMinMinutes",
            name = "Break length at least (minutes)",
            description = "Shortest break",
            position = 3,
            section = breakSettings
    )
    @Range(
            min = 1,
            max = 240
    )
    default int breakMinMinutes() {
        return 5;
    }

    @ConfigItem(
            keyName = "breakMaxMinutes",
            name = "Break length at most (minutes)",
            description = "Longest break; the length is random to the second between the two",
            position = 4,
            section = breakSettings
    )
    @Range(
            min = 1,
            max = 240
    )
    default int breakMaxMinutes() {
        return 40;
    }

    @ConfigItem(
            keyName = "breakLogout",
            name = "Log out during break",
            description = "Log out for the break and log back in when it ends. Needs a saved login profile (Auto Login plugin or account selector); without one you stay logged out after the break.",
            position = 5,
            section = breakSettings
    )
    default boolean breakLogout() {
        return true;
    }

    @ConfigItem(
            keyName = "mixedAlwaysOnWeight",
            name = "Mixed: always on weight",
            description = "Prayer mode Mixed: relative chance that a kill uses always on",
            position = 8,
            section = prayerSettings
    )
    @Range(
            min = 0,
            max = 100
    )
    default int mixedAlwaysOnWeight() {
        return 50;
    }

    @ConfigItem(
            keyName = "mixedLazyWeight",
            name = "Mixed: lazy flick weight",
            description = "Prayer mode Mixed: relative chance that a kill uses lazy flick",
            position = 9,
            section = prayerSettings
    )
    @Range(
            min = 0,
            max = 100
    )
    default int mixedLazyWeight() {
        return 50;
    }

    @ConfigItem(
            keyName = "antibanMousePrayers",
            name = "Click prayers with the mouse",
            description = "Open the prayer tab and click the prayer icon like a player. When there is no time left in the tick it clicks "
                    + "instantly instead, so a hit is never left unprotected.",
            position = 0,
            section = antibanSettings
    )
    default boolean antibanMousePrayers() {
        return false;
    }

    @ConfigItem(
            keyName = "antibanFramework",
            name = "Use Microbot antiban",
            description = "Natural mouse, attention span and play style. Never pauses the fight: eating, praying, dodging and attacking are not delayed.",
            position = 1,
            section = antibanSettings
    )
    default boolean antibanFramework() {
        return true;
    }

    @ConfigItem(
            keyName = "habitOffScreen",
            name = "Mouse off-screen while waiting",
            description = "Sometimes move the mouse off the game window while waiting for Scurrius to respawn",
            position = 2,
            section = antibanSettings
    )
    default boolean habitOffScreen() {
        return false;
    }

    @ConfigItem(
            keyName = "habitCheckSkills",
            name = "Check the skills tab between kills",
            description = "Sometimes open the skills tab for a moment between kills",
            position = 3,
            section = antibanSettings
    )
    default boolean habitCheckSkills() {
        return true;
    }

    @ConfigItem(
            keyName = "habitCameraNudges",
            name = "Small camera turns between kills",
            description = "Sometimes turn the camera a little between kills",
            position = 4,
            section = antibanSettings
    )
    default boolean habitCameraNudges() {
        return true;
    }

    @ConfigItem(
            keyName = "habitPauses",
            name = "Short random pauses",
            description = "Sometimes pause 1-8 seconds after banking and before walking into the boss room",
            position = 5,
            section = antibanSettings
    )
    default boolean habitPauses() {
        return true;
    }

    @ConfigItem(
            keyName = "habitLateReclick",
            name = "Re-click Scurrius a little late",
            description = "Sometimes wait up to a tick before attacking again after the attack stopped",
            position = 6,
            section = antibanSettings
    )
    default boolean habitLateReclick() {
        return true;
    }

    @ConfigItem(
            keyName = "habitEatEarly",
            name = "Sometimes eat a bit early",
            description = "Now and then eat a few percent above the chosen threshold",
            position = 7,
            section = antibanSettings
    )
    default boolean habitEatEarly() {
        return true;
    }

    @ConfigItem(
            keyName = "specEnabled",
            name = "Use special attack",
            description = "Turn the special attack on whenever there is enough energy, while attacking Scurrius (never the rats)",
            position = 0,
            section = specSettings
    )
    default boolean specEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "specEnergyNeeded",
            name = "Spec energy needed (%)",
            description = "0 = the weapon's own cost when known (toxic blowpipe 50), else 50",
            position = 1,
            section = specSettings
    )
    @Range(
            min = 0,
            max = 100
    )
    default int specEnergyNeeded() {
        return 0;
    }

    @ConfigItem(
            keyName = "specWeapon",
            name = "Spec weapon",
            description = "Blank = the equipped weapon. Otherwise the name of a weapon in your inventory (with an Inventory Setup, put it in the setup). "
                    + "It is wielded for the spec and your main weapon and shield are put back afterwards.",
            position = 2,
            section = specSettings
    )
    default String specWeapon() {
        return "";
    }

    @ConfigItem(
            keyName = "specSkipBossHp",
            name = "Skip spec under boss HP (%)",
            description = "Do not spec when Scurrius is under this health percent; 0 = never skip",
            position = 3,
            section = specSettings
    )
    @Range(
            min = 0,
            max = 100
    )
    default int specSkipBossHp() {
        return 0;
    }

    @ConfigItem(
            keyName = "lootItems",
            name = "Loot Items",
            description = "Comma-separated list of item names to loot regardless of value",
            position = 0,
            section = lootSettings
    )
    default String lootItems() {
        return "";
    }

    @ConfigItem(
            keyName = "lootValueThreshold",
            name = "Loot Value Threshold",
            description = "Minimum value of items to loot if not specified in the loot list",
            position = 1,
            section = lootSettings
    )
    default int lootValueThreshold() {
        return 1000;
    }

    @Getter
    enum PotionSelection {
        PRAYERPOTION(ItemID._4DOSEPRAYERRESTORE),
        SUPERRESTORE(ItemID._4DOSE2RESTORE);

        private final int itemId;

        PotionSelection(int itemId) {
            this.itemId = itemId;
        }
    }

    @Getter
    enum BossRoomEntryType {
        PRIVATE("Climb-through (private)"),
        NORMAL("Climb-through (normal)");

        private final String interactionText;

        BossRoomEntryType(String interactionText) {
            this.interactionText = interactionText;
        }
    }
}
