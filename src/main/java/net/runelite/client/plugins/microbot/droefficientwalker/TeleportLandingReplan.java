package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;

/** Require an observed landing, not a dispatched click or failed teleport. */
final class TeleportLandingReplan {
    private TeleportLandingReplan() { }
    static boolean shouldRecompare(WorldPoint departure, WorldPoint expected, WorldPoint actual, WorldPoint target) {
        return departure != null && expected != null && actual != null && target != null
                && actual.distanceTo(expected) <= 12 && departure.distanceTo(actual) > 16
                && actual.distanceTo(target) > 16;
    }
}
