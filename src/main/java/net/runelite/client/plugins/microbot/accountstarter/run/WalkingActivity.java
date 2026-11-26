package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** The walking flag the panel reads, and the one way to set it. */
abstract class WalkingActivity implements Activity {
    protected volatile boolean walking;
    protected volatile String walkPlace;

    /** abort() must be able to stop any walk made inside the body, including the ones Kit makes. */
    protected <T> T whileWalking(String place, Supplier<T> body) {
        walkPlace = place;
        walking = true;
        try {
            return body.get();
        } finally {
            walking = false;
        }
    }

    @Override
    public void abort() {
        if (walking) Rs2Walker.setTarget(null);
    }

    @Override
    public boolean walking() {
        return walking;
    }

    @Override
    public String walkingTo() {
        return walking ? walkPlace : null;
    }
}
