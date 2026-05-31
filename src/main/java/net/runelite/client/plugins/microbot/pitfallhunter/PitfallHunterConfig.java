package net.runelite.client.plugins.microbot.pitfallhunter;

import net.runelite.api.ItemID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigInformation("<html>"
        + "Local Sunlight Antelope pitfall hunter MVP.<br/>"
        + "Starts near the pitfall area and runs only the local NPC lure and closest-pit loop. "
        + "Includes optional low-HP banking and return-to-start travel.<br/><br/>"
        + "Required: knife and teasing stick.<br/>"
        + "Required if fletching: chisel.<br/>"
        + "Optional: meat pouch.<br/>"
        + "Supported: Kandarin headgear."
        + "</html>")
@ConfigGroup("pitfallhunter")
public interface PitfallHunterConfig extends Config
{
    enum BigBonesMode
    {
        KEEP,
        DROP,
        BURY_AFTER_LOOT,
        BURY_WHEN_FULL
    }

    enum AntelopeDropThreshold
    {
        OFF(0),
        BELOW_3(3),
        BELOW_4(4);

        private final int emptySlots;

        AntelopeDropThreshold(int emptySlots)
        {
            this.emptySlots = emptySlots;
        }

        public int emptySlots()
        {
            return emptySlots;
        }
    }

    enum BankFood
    {
        TROUT("Trout", ItemID.TROUT, 7),
        SALMON("Salmon", ItemID.SALMON, 9),
        TUNA("Tuna", ItemID.TUNA, 10),
        LOBSTER("Lobster", ItemID.LOBSTER, 12),
        BASS("Bass", ItemID.BASS, 13),
        SWORDFISH("Swordfish", ItemID.SWORDFISH, 14),
        POTATO_WITH_CHEESE("Potato with cheese", ItemID.POTATO_WITH_CHEESE, 16),
        MONKFISH("Monkfish", ItemID.MONKFISH, 16),
        COOKED_KARAMBWAN("Cooked karambwan", ItemID.COOKED_KARAMBWAN, 18),
        SHARK("Shark", ItemID.SHARK, 20),
        SEA_TURTLE("Sea turtle", ItemID.SEA_TURTLE, 21),
        MANTA_RAY("Manta ray", ItemID.MANTA_RAY, 22),
        TUNA_POTATO("Tuna potato", ItemID.TUNA_POTATO, 22);

        private final String name;
        private final int itemId;
        private final int healAmount;

        BankFood(String name, int itemId, int healAmount)
        {
            this.name = name;
            this.itemId = itemId;
            this.healAmount = healAmount;
        }

        public int itemId()
        {
            return itemId;
        }

        public int healAmount()
        {
            return healAmount;
        }

        @Override
        public String toString()
        {
            return name + " (" + healAmount + " HP)";
        }
    }

    @ConfigItem(
            position = 0,
            keyName = "maxNpcDistanceFromPit",
            name = "Max NPC distance",
            description = "Maximum tile distance from a selected pit used by legacy pit/NPC checks."
    )
    default int maxNpcDistanceFromPit()
    {
        return 10;
    }

    @ConfigItem(
            position = 1,
            keyName = "pitObjectSearchRadius",
            name = "Pit object radius",
            description = "Tile radius around each configured pit tile used to detect pitfall objects."
    )
    default int pitObjectSearchRadius()
    {
        return 2;
    }

    @ConfigItem(
            position = 2,
            keyName = "captureTimeoutMs",
            name = "Capture timeout",
            description = "Maximum time to wait after jumping for the pit to collapse before rotating to another pit."
    )
    default int captureTimeoutMs()
    {
        return 5000;
    }

    @ConfigItem(
            position = 3,
            keyName = "bigBonesMode",
            name = "Big bones",
            description = "How to handle Big bones from collapsed traps."
    )
    default BigBonesMode bigBonesMode()
    {
        return BigBonesMode.KEEP;
    }

    @ConfigItem(
            position = 4,
            keyName = "antelopeDropThreshold",
            name = "Drop antelope loot",
            description = "Drop Sunlight antelope meat and fur when empty slots fall below the selected threshold."
    )
    default AntelopeDropThreshold antelopeDropThreshold()
    {
        return AntelopeDropThreshold.OFF;
    }

    @ConfigItem(
            position = 5,
            keyName = "fletchAntlers",
            name = "Fletch antlers",
            description = "Use a chisel on Sunlight antelope antlers and complete the bolts dialogue when possible."
    )
    default boolean fletchAntlers()
    {
        return false;
    }

    @ConfigItem(
            position = 6,
            keyName = "logsToPrepare",
            name = "Logs to prepare",
            description = "Number of logs to cut when preparing tools/logs with Kandarin headgear."
    )
    default int logsToPrepare()
    {
        return 1;
    }

    @ConfigItem(
            position = 7,
            keyName = "bankingEnabled",
            name = "Banking",
            description = "Walk to the manual bank tile, empty item containers, bank loot, heal to full HP, then return to the start tile."
    )
    default boolean bankingEnabled()
    {
        return false;
    }

    @ConfigItem(
            position = 8,
            keyName = "bankBelowHitpoints",
            name = "Bank below HP",
            description = "Bank when current hitpoints are at or below this value. Set to 0 to disable the HP trigger."
    )
    default int bankBelowHitpoints()
    {
        return 25;
    }

    @ConfigItem(
            position = 9,
            keyName = "bankFood",
            name = "Bank food",
            description = "Food to withdraw while healing at the bank."
    )
    default BankFood bankFood()
    {
        return BankFood.SHARK;
    }
}
