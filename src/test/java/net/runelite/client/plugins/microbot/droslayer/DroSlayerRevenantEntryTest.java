package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerRevenantEntryTest {
    private static final WorldPoint GOBLINS = new WorldPoint(3221, 10065, 0);
    private static final WorldPoint EDGEVILLE = new WorldPoint(3094, 3494, 0);

    @Test public void successfulEntryPersistsAcrossBankTripsUntilSessionReset() {
        DroSlayerRevenantEntry entry = new DroSlayerRevenantEntry();
        assertTrue(entry.needsCoins());
        entry.observeLocation(EDGEVILLE);
        entry.observeLocation(null);
        assertTrue(entry.needsCoins());
        entry.observeLocation(GOBLINS);
        entry.observeLocation(EDGEVILLE);
        entry.observeLocation(null); // Logout / world hop.
        assertFalse(entry.needsCoins());
        entry.reset();
        assertTrue(entry.needsCoins());
    }

    @Test public void caveDeathRequiresAnotherDepositOnlyAfterRespawnAndReentry() {
        DroSlayerRevenantEntry entry = new DroSlayerRevenantEntry();
        entry.observeLocation(GOBLINS);
        entry.onDeath(new WorldPoint(3180, 10209, 0)); // Northern cave route.
        assertTrue(entry.needsCoins());
        entry.observeLocation(GOBLINS); // Death animation is still in the old scene.
        entry.observeLocation(null);
        assertTrue(entry.needsCoins());
        entry.observeLocation(new WorldPoint(3222, 3218, 0));
        assertTrue(entry.needsCoins());
        entry.observeLocation(GOBLINS);
        assertFalse(entry.needsCoins());
    }

    @Test public void deathOutsideCavesKeepsConfirmation() {
        DroSlayerRevenantEntry entry = new DroSlayerRevenantEntry();
        entry.observeLocation(GOBLINS);
        for (WorldPoint location : new WorldPoint[] {EDGEVILLE,
                new WorldPoint(3363, 10120, 0), new WorldPoint(3221, 10065, 1), null}) {
            entry.onDeath(location);
            assertFalse(entry.needsCoins());
        }
    }
}
