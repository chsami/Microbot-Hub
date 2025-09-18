package net.runelite.client.plugins.microbot.MossKiller.Enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

public class GearEnums {

    public enum RangedAmulet {
        ACCURACY("Amulet of accuracy", ItemID.AMULET_OF_ACCURACY),
        POWER("Amulet of power", ItemID.AMULET_OF_POWER);

        private final String name;
        @Getter
        private final int itemId;

        RangedAmulet(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum RangedTorso {
        LEATHER("Leather body", ItemID.LEATHER_ARMOUR),
        HARDLEATHER("Hardleather body", ItemID.HARDLEATHER_BODY),
        STUDDED("Studded body", ItemID.STUDDED_BODY),
        GREEN_DHIDE("Green d'hide body", ItemID.DRAGONHIDE_BODY);

        private final String name;
        @Getter
        private final int itemId;

        RangedTorso(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum RangedChaps {
        LEATHER("Leather chaps", ItemID.LEATHER_CHAPS),
        STUDDED("Studded chaps", ItemID.STUDDED_CHAPS),
        GREEN_DHIDE("Green d'hide chaps", ItemID.DRAGONHIDE_CHAPS);

        private final String name;
        @Getter
        private final int itemId;

        RangedChaps(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Cape {
        BLACK_CAPE("Black cape", ItemID.BLACK_CAPE),
        BLUE_CAPE("Blue cape", ItemID.BLUE_CAPE),
        YELLOW_CAPE("Yellow cape", ItemID.YELLOW_CAPE),
        RED_CAPE("Red cape", ItemID.RED_CAPE),
        GREEN_CAPE("Green cape", ItemID.GREEN_CAPE),
        PURPLE_CAPE("Purple cape", ItemID.PURPLE_CAPE),
        ORANGE_CAPE("Orange cape", ItemID.ORANGE_CAPE),

        // Team capes (1-50)
        TEAM_CAPE_1("Team-1 Cape", ItemID.WILDERNESS_CAPE_1),
        TEAM_CAPE_2("Team-2 Cape", ItemID.WILDERNESS_CAPE_2),
        TEAM_CAPE_3("Team-3 Cape", ItemID.WILDERNESS_CAPE_3),
        TEAM_CAPE_4("Team-4 Cape", ItemID.WILDERNESS_CAPE_4),
        TEAM_CAPE_5("Team-5 Cape", ItemID.WILDERNESS_CAPE_5),
        TEAM_CAPE_6("Team-6 Cape", ItemID.WILDERNESS_CAPE_6),
        TEAM_CAPE_7("Team-7 Cape", ItemID.WILDERNESS_CAPE_7),
        TEAM_CAPE_8("Team-8 Cape", ItemID.WILDERNESS_CAPE_8),
        TEAM_CAPE_9("Team-9 Cape", ItemID.WILDERNESS_CAPE_9),
        TEAM_CAPE_10("Team-10 Cape", ItemID.WILDERNESS_CAPE_10),
        TEAM_CAPE_11("Team-11 Cape", ItemID.WILDERNESS_CAPE_11),
        TEAM_CAPE_12("Team-12 Cape", ItemID.WILDERNESS_CAPE_12),
        TEAM_CAPE_13("Team-13 Cape", ItemID.WILDERNESS_CAPE_13),
        TEAM_CAPE_14("Team-14 Cape", ItemID.WILDERNESS_CAPE_14),
        TEAM_CAPE_15("Team-15 Cape", ItemID.WILDERNESS_CAPE_15),
        TEAM_CAPE_16("Team-16 Cape", ItemID.WILDERNESS_CAPE_16),
        TEAM_CAPE_17("Team-17 Cape", ItemID.WILDERNESS_CAPE_17),
        TEAM_CAPE_18("Team-18 Cape", ItemID.WILDERNESS_CAPE_18),
        TEAM_CAPE_19("Team-19 Cape", ItemID.WILDERNESS_CAPE_19),
        TEAM_CAPE_20("Team-20 Cape", ItemID.WILDERNESS_CAPE_20),
        TEAM_CAPE_21("Team-21 Cape", ItemID.WILDERNESS_CAPE_21),
        TEAM_CAPE_22("Team-22 Cape", ItemID.WILDERNESS_CAPE_22),
        TEAM_CAPE_23("Team-23 Cape", ItemID.WILDERNESS_CAPE_23),
        TEAM_CAPE_24("Team-24 Cape", ItemID.WILDERNESS_CAPE_24),
        TEAM_CAPE_25("Team-25 Cape", ItemID.WILDERNESS_CAPE_25),
        TEAM_CAPE_26("Team-26 Cape", ItemID.WILDERNESS_CAPE_26),
        TEAM_CAPE_27("Team-27 Cape", ItemID.WILDERNESS_CAPE_27),
        TEAM_CAPE_28("Team-28 Cape", ItemID.WILDERNESS_CAPE_28),
        TEAM_CAPE_29("Team-29 Cape", ItemID.WILDERNESS_CAPE_29),
        TEAM_CAPE_30("Team-30 Cape", ItemID.WILDERNESS_CAPE_30),
        TEAM_CAPE_31("Team-31 Cape", ItemID.WILDERNESS_CAPE_31),
        TEAM_CAPE_32("Team-32 Cape", ItemID.WILDERNESS_CAPE_32),
        TEAM_CAPE_33("Team-33 Cape", ItemID.WILDERNESS_CAPE_33),
        TEAM_CAPE_34("Team-34 Cape", ItemID.WILDERNESS_CAPE_34),
        TEAM_CAPE_35("Team-35 Cape", ItemID.WILDERNESS_CAPE_35),
        TEAM_CAPE_36("Team-36 Cape", ItemID.WILDERNESS_CAPE_36),
        TEAM_CAPE_37("Team-37 Cape", ItemID.WILDERNESS_CAPE_37),
        TEAM_CAPE_38("Team-38 Cape", ItemID.WILDERNESS_CAPE_38),
        TEAM_CAPE_39("Team-39 Cape", ItemID.WILDERNESS_CAPE_39),
        TEAM_CAPE_40("Team-40 Cape", ItemID.WILDERNESS_CAPE_40),
        TEAM_CAPE_41("Team-41 Cape", ItemID.WILDERNESS_CAPE_41),
        TEAM_CAPE_42("Team-42 Cape", ItemID.WILDERNESS_CAPE_42),
        TEAM_CAPE_43("Team-43 Cape", ItemID.WILDERNESS_CAPE_43),
        TEAM_CAPE_44("Team-44 Cape", ItemID.WILDERNESS_CAPE_44),
        TEAM_CAPE_45("Team-45 Cape", ItemID.WILDERNESS_CAPE_45),
        TEAM_CAPE_46("Team-46 Cape", ItemID.WILDERNESS_CAPE_46),
        TEAM_CAPE_47("Team-47 Cape", ItemID.WILDERNESS_CAPE_47),
        TEAM_CAPE_48("Team-48 Cape", ItemID.WILDERNESS_CAPE_48),
        TEAM_CAPE_49("Team-49 Cape", ItemID.WILDERNESS_CAPE_49),
        TEAM_CAPE_50("Team-50 Cape", ItemID.WILDERNESS_CAPE_50),

        // Team capes (i, x, zero)
        TEAM_CAPE_I("Team cape i", ItemID.WILDERNESS_CAPE_I),
        TEAM_CAPE_X("Team cape x", ItemID.WILDERNESS_CAPE_X),
        TEAM_CAPE_ZERO("Team cape zero", ItemID.WILDERNESS_CAPE_ZERO);

        private final String name;
        @Getter
        private final int itemId;

        Cape(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
