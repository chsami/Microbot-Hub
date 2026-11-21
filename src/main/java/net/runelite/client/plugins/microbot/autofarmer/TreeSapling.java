package net.runelite.client.plugins.microbot.autofarmer;

import net.runelite.api.gameval.ItemID;

public interface TreeSapling {
    int itemId();
    int level();
    int growthMinutes();

    enum Regular implements TreeSapling {
        Oak(ItemID.PLANTPOT_OAK_SAPLING, 15, 160),
        Willow(ItemID.PLANTPOT_WILLOW_SAPLING, 30, 240),
        Maple(ItemID.PLANTPOT_MAPLE_SAPLING, 45, 320),
        Yew(ItemID.PLANTPOT_YEW_SAPLING, 60, 400),
        Magic(ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 75, 480);

        private final int id;
        private final int requiredLevel;
        private final int minutes;

        Regular(int id, int requiredLevel, int minutes) {
            this.id = id;
            this.requiredLevel = requiredLevel;
            this.minutes = minutes;
        }

        public int itemId() { return id; }
        public int level() { return requiredLevel; }
        public int growthMinutes() { return minutes; }
    }

    enum Fruit implements TreeSapling {
        Apple(ItemID.PLANTPOT_APPLE_SAPLING, 27),
        Banana(ItemID.PLANTPOT_BANANA_SAPLING, 33),
        Orange(ItemID.PLANTPOT_ORANGE_SAPLING, 39),
        Curry(ItemID.PLANTPOT_CURRY_SAPLING, 42),
        Pineapple(ItemID.PLANTPOT_PINEAPPLE_SAPLING, 51),
        Papaya(ItemID.PLANTPOT_PAPAYA_SAPLING, 57),
        Palm(ItemID.PLANTPOT_PALM_SAPLING, 68),
        Dragonfruit(ItemID.PLANTPOT_DRAGONFRUIT_SAPLING, 81);

        private final int id;
        private final int requiredLevel;

        Fruit(int id, int requiredLevel) {
            this.id = id;
            this.requiredLevel = requiredLevel;
        }

        public int itemId() { return id; }
        public int level() { return requiredLevel; }
        public int growthMinutes() { return 960; }
    }
}
