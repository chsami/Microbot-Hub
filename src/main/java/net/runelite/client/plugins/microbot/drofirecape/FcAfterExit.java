package net.runelite.client.plugins.microbot.drofirecape;

/** Startup/configuration failures and supply exhaustion cannot create automatic retry loops. */
final class FcAfterExit {
    static boolean repeat(DroFirecapeConfig.AfterExitBehavior behavior, boolean enteredCave,
                          boolean missingSupplies, boolean observeOnly) {
        return behavior == DroFirecapeConfig.AfterExitBehavior.GET_ANOTHER_CAPE
                && enteredCave && !missingSupplies && !observeOnly;
    }
}
