package net.runelite.client.plugins.microbot.accountstarter.run;

import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;

public interface Activity {
    String describe();

    Outcome tick(AccountSnapshot s);

    default void abort() {
    }

    /** Read by the panel from another thread. */
    default boolean walking() {
        return false;
    }

    default String walkingTo() {
        return null;
    }
}
