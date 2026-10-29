package net.runelite.client.plugins.microbot.FourtifyFishing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.movement.SpotPicker;
import net.runelite.client.plugins.microbot.FourtifyFishing.data.FishingMethod;

@ConfigGroup("fourtifyfishing")
public interface FourtifyFishingConfig extends Config {
    /**
     * Best available first, so it is the default and the top of the list; then the free-to-play methods by level, then
     * the members methods by level.
     */
    @Getter
    @RequiredArgsConstructor
    enum MethodChoice {
        BEST_AVAILABLE(null),
        NET(FishingMethod.NET),
        SEA_BAIT(FishingMethod.SEA_BAIT),
        FLY(FishingMethod.FLY),
        RIVER_BAIT(FishingMethod.RIVER_BAIT),
        HARPOON(FishingMethod.HARPOON),
        CAGE(FishingMethod.CAGE),
        KARAMBWANJI(FishingMethod.KARAMBWANJI),
        BIG_NET(FishingMethod.BIG_NET),
        BARBARIAN(FishingMethod.BARBARIAN),
        MONKFISH(FishingMethod.MONKFISH),
        SHARK(FishingMethod.SHARK),
        INFERNAL_EEL(FishingMethod.INFERNAL_EEL),
        ANGLERFISH(FishingMethod.ANGLERFISH);

        /** Null for the best available method. */
        private final FishingMethod method;

        @Override
        public String toString() {
            return method == null ? "Best available" : method.toString() + (method.isMembers() ? " (members)" : "");
        }
    }

    enum FullInventory {
        DROP,
        BANK
    }

    @ConfigItem(
            position = 0,
            keyName = "method",
            name = "Method",
            description = "How to fish. Best available: the method with the most experience an hour, counting every skill it"
                    + " trains (Fishing, and Strength and Agility for barbarian fishing, Strength for fishing barehanded), at your"
                    + " real levels, among the methods whose requirements, tool and bait you have (carried or in the bank); it"
                    + " moves on as your levels rise. Infernal eel fishing is never picked. Every other method runs"
                    + " only when chosen; members methods need a members world"
    )
    default MethodChoice method() {
        return MethodChoice.BEST_AVAILABLE;
    }

    @ConfigItem(
            position = 1,
            keyName = "spotChoice",
            name = "Spot",
            description = "Nearest: the closest safe spot, so the walk is short. Random: any safe spot on the map;"
                    + " only the spot is checked for aggressive monsters, not the way there"
    )
    default SpotPicker.Choice spotChoice() {
        return SpotPicker.Choice.NEAREST;
    }

    @ConfigItem(
            position = 2,
            keyName = "fullInventory",
            name = "Full inventory",
            description = "Drop the raw fish, or bank them at the nearest bank and walk back"
    )
    default FullInventory fullInventory() {
        return FullInventory.DROP;
    }

    @Range(min = 2, max = 99)
    @ConfigItem(
            position = 3,
            keyName = "targetLevel",
            name = "Target level",
            description = "Stop when Fishing reaches this level"
    )
    default int targetLevel() {
        return 30;
    }
}
