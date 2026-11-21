package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.client.plugins.microbot.scurrius.enums.State;

/**
 * The state the script should be in for the situation it is in, as one pure table so no combination of room, boss,
 * food, potions and tab can leave it idle. Every call returns a state that does something.
 */
public final class SupplyRules {
    /** In a fight with no prayer potions left, keep fighting only while prayer points are at least this percent of the real level. */
    public static final int MIN_PRAYER_PERCENT_WITHOUT_POTIONS = 25;

    private SupplyRules() {
    }

    /**
     * @param inRoom        the player is inside the boss room
     * @param bossPresent   Scurrius is there to fight
     * @param lineOfSight   Scurrius can be seen from where the player stands
     * @param lootPending   the boss just died and loot may still be on the floor
     * @param foodLeft      food items in the inventory
     * @param hasPotions    a prayer potion or super restore is in the inventory
     * @param hasTab        a Varrock teleport tab is in the inventory
     * @param prayerPoints  current prayer points
     * @param maxPrayer     real prayer level
     * @param fullSupplies  food, potions and tabs are at the configured amounts
     */
    public static State nextState(boolean inRoom, boolean bossPresent, boolean lineOfSight, boolean lootPending, int foodLeft,
                                  boolean hasPotions, boolean hasTab, int prayerPoints, int maxPrayer, boolean fullSupplies) {
        State leave = hasTab ? State.TELEPORT_AWAY : State.BANKING; // no tab: walk out to the bank
        if (!inRoom) {
            if (!fullSupplies) {
                return State.BANKING; // below the configured amounts, including none at all: top up, never idle
            }
            return bossPresent && lineOfSight ? State.FIGHTING : State.WALK_TO_BOSS;
        }
        if (bossPresent) {
            if (foodLeft <= 0) {
                return leave; // out of food mid-fight: leave at once
            }
            if (!hasPotions && !prayerPointsEnough(prayerPoints, maxPrayer)) {
                return leave;
            }
            return State.FIGHTING;
        }
        if (lootPending && foodLeft > 0) {
            return State.WAITING_FOR_BOSS; // the boss is dead: pick the loot up before leaving
        }
        if (foodLeft <= 0 || !hasPotions) {
            return leave;
        }
        return State.WAITING_FOR_BOSS;
    }

    static boolean prayerPointsEnough(int prayerPoints, int maxPrayer) {
        return maxPrayer <= 0 || prayerPoints * 100 >= maxPrayer * MIN_PRAYER_PERCENT_WITHOUT_POTIONS;
    }
}
