package net.runelite.client.plugins.microbot.barrows;

import net.runelite.api.coords.WorldPoint;

/**
 * Pure decision helpers for Barrows tunnel combat and Ferox restock.
 * Kept free of client calls so unit tests can lock the rules.
 */
final class BarrowsTunnelRules {

    /** Approximate center of Ferox enclave / pool area (matches prior script check). */
    static final WorldPoint FEROX_CENTER = new WorldPoint(3130, 3631, 0);
    /** Walk target for the Pool of Refreshment (avoids pathing near the CW portal). */
    static final WorldPoint FEROX_POOL_POINT = new WorldPoint(3128, 3637, 0);
    static final int FEROX_ENCLAVE_RADIUS = 40;
    static final int FEROX_REFRESHMENT_POOL_ID = 39651;

    private BarrowsTunnelRules() {
    }

    static boolean isBarrowsBrotherName(String name) {
        if (name == null) {
            return false;
        }
        return name.contains("Dharok") || name.contains("Guthan") || name.contains("Karil")
                || name.contains("Torag") || name.contains("Verac") || name.contains("Ahrim");
    }

    static boolean isTunnelRpMonsterName(String name) {
        if (name == null || isBarrowsBrotherName(name)) {
            return false;
        }
        return name.equals("Skeleton")
                || name.contains("Bloodworm")
                || name.contains("Crypt rat")
                || name.contains("Crypt spider")
                || name.contains("Giant crypt");
    }

    /**
     * Door-spawn crypt trash that can lock the player in single-way combat.
     * Includes Skeleton — excluding it left the script attacking a hallway target
     * while a door-spawn skeleton held combat priority.
     */
    static boolean isCombatLockAggressorName(String name) {
        return isTunnelRpMonsterName(name);
    }

    /**
     * @param interactingWithCurrentTarget player is already attacking the chosen target
     * @param aggressorPresent a nearby crypt trash NPC has the player as its interacting target
     */
    static boolean shouldRetargetToCombatLock(
            boolean interactingWithCurrentTarget,
            boolean aggressorPresent) {
        return aggressorPresent && !interactingWithCurrentTarget;
    }

    static boolean isAtFeroxEnclave(WorldPoint loc) {
        if (loc == null) {
            return false;
        }
        return loc.distanceTo(FEROX_CENTER) < FEROX_ENCLAVE_RADIUS;
    }

    /** When true, restock must use {@code BankLocation.FEROX_ENCLAVE} — never nearest-bank. */
    static boolean shouldForceFeroxBank(WorldPoint loc) {
        return isAtFeroxEnclave(loc);
    }
}
