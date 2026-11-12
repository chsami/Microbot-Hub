package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EdgevilleDungeonAccessTest {
    private final WorldPoint outside = new WorldPoint(3115, 3449, 0);
    @Test public void cabinCannotBePlannedWithoutKeyButOtherLaddersRemainUsable() {
        assertFalse(EdgevilleDungeonAccess.allowed(1804, new WorldPoint(3115,3450,0), outside, false));
        assertFalse(EdgevilleDungeonAccess.allowed(17384, new WorldPoint(3115,3452,0), outside, false));
        assertTrue(EdgevilleDungeonAccess.allowed(17384, new WorldPoint(3100,3500,0), outside, false));
        assertTrue(EdgevilleDungeonAccess.allowed(17384, new WorldPoint(3115,3452,0), outside, true));
        assertTrue(EdgevilleDungeonAccess.allowed(17384, new WorldPoint(3115,3452,0), new WorldPoint(3115,3451,0), false));
    }
    @Test public void confirmedApproachReplacesLegacyTrapdoorEdge() {
        var trapdoor = SlayerDungeonTransports.create().stream()
            .filter(EdgevilleDungeonAccess::isTrapdoor).findFirst().orElseThrow();
        assertEquals(new WorldPoint(3094,3469,0), trapdoor.getOrigin());
        assertEquals(new WorldPoint(3096,9867,0), trapdoor.getDestination());
        assertTrue(SlayerDungeonTransports.isSupersededTransport(1581, new WorldPoint(3096,3468,0)));
        assertFalse(SlayerDungeonTransports.isSupersededTransport(1581, new WorldPoint(3405,3506,0)));
    }
    @Test public void repeatedDoorDispatchesAreSpacedBoundedAndDoNotResetOnReplan() {
        WalkerDoorAttempts attempts = new WalkerDoorAttempts();
        WorldPoint door = new WorldPoint(3115,3450,0);
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(1804,door,outside,337,true,0));
        assertEquals(WalkerDoorAttempts.Decision.WAIT, attempts.request(1804,door,outside,337,true,600));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(1804,door,outside,337,true,2500));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(1804,door,outside,337,true,5000));
        assertEquals(WalkerDoorAttempts.Decision.BLOCK, attempts.request(1804,door,outside,337,true,7500));
        assertNotNull(attempts.takeFailure());
        assertEquals(WalkerDoorAttempts.Decision.BLOCK, attempts.request(1804,door,outside,337,true,15000));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(1804,door,outside,338,true,16000));
    }
    @Test public void observedCrossingAllowsReturnThroughSameDoor() {
        WalkerDoorAttempts attempts = new WalkerDoorAttempts();
        WorldPoint door = new WorldPoint(3115,3450,0);
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(1804,door,outside,337,true,0));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(1804,door,new WorldPoint(3115,3451,0),337,true,600));
    }
    @Test public void missingKeyNeverDispatchesAndAcquiringItPermitsDoor() {
        WalkerDoorAttempts attempts = new WalkerDoorAttempts();
        WorldPoint door = new WorldPoint(3115,3450,0);
        assertEquals(WalkerDoorAttempts.Decision.BLOCK, attempts.request(1804,door,outside,337,false,0));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(1804,door,outside,337,true,600));
    }
}
