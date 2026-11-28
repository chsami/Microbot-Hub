package net.runelite.client.plugins.microbot.accountstarter.logic;

/** The Ranged and Magic tutors give nothing: bows, arrows and runes come from Captain Harlan or the shops. */
public enum FreeSource {
    FISHING_TUTOR, WOODCUTTING_TUTOR, MINING_TUTOR, HARLAN,
    LOWE,
    AUBURY,
    CATCH_AND_COOK;

    public String tutorName() {
        switch (this) {
            case FISHING_TUTOR: return "Finlay";
            case WOODCUTTING_TUTOR: return "Wilfred";
            case MINING_TUTOR: return "Monlum";
            case HARLAN: return "Captain Harlan";
            case LOWE: return "Lowe";
            case AUBURY: return "Aubury";
            default: return "catch and cook";
        }
    }
}
