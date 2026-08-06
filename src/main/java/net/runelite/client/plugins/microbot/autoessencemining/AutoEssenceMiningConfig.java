package net.runelite.client.plugins.microbot.autoessencemining;

import net.runelite.client.config.*;

@ConfigGroup("EssenceMining")
public interface AutoEssenceMiningConfig extends Config {
    enum PickaxeOverride {
        AUTO("Auto"),
        BRONZE("Bronze pickaxe"),
        IRON("Iron pickaxe"),
        STEEL("Steel pickaxe"),
        BLACK("Black pickaxe"),
        MITHRIL("Mithril pickaxe"),
        ADAMANT("Adamant pickaxe"),
        RUNE("Rune pickaxe"),
        GILDED("Gilded pickaxe"),
        DRAGON("Dragon pickaxe"),
        DRAGON_OR("Dragon pickaxe (or)"),
        INFERNAL("Infernal pickaxe"),
        INFERNAL_EMPTY("Infernal pickaxe (uncharged)"),
        CRYSTAL("Crystal pickaxe"),
        THIRD_AGE("3rd age pickaxe"),
        TRAILBLAZER("Trailblazer pickaxe");

        private final String name;

        PickaxeOverride(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @ConfigItem(
            keyName = "Guide",
            name = "Usage guide",
            description = "Usage guide",
            position = 1
    )
    default String GUIDE() {
        return  "Begin anywhere with a pickaxe wielded or in your inventory...";
    }

    @ConfigItem(
            keyName = "manualPickaxeOverride",
            name = "Manual Pickaxe Override",
            description = "Choose a specific pickaxe, or Auto to use the best usable pickaxe found in your bank, inventory, or weapon slot.",
            position = 2
    )
    default PickaxeOverride manualPickaxeOverride() {
        return PickaxeOverride.AUTO;
    }
}
