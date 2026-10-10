package net.runelite.client.plugins.microbot.drozulrah;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahRecoveryBankRouteTest {
    private final List<String> calls = new ArrayList<>();
    private final ZulrahRecoveryBankRoute.Actions actions = new ZulrahRecoveryBankRoute.Actions() {
        public boolean walk(WorldPoint p) { calls.add("walk:" + p); return true; }
        public boolean interact(int id, WorldPoint p, String action) { calls.add(id + ":" + action + ":" + p); return true; }
    };

    @Test public void lumbridgeUsesRecordedSouthernStairsAndObservedPlaneChanges() {
        ZulrahRecoveryBankRoute route = new ZulrahRecoveryBankRoute();
        route.tick(ZulrahDeathRecovery.Spawn.LUMBRIDGE, new WorldPoint(3222,3217,0), false,0,actions);
        assertTrue(calls.get(0).contains("3214, y=3211"));
        route.tick(ZulrahDeathRecovery.Spawn.LUMBRIDGE, new WorldPoint(3214,3211,0), false,2000,actions);
        assertTrue(calls.get(1).startsWith("56230:Climb-up:"));
        route.tick(ZulrahDeathRecovery.Spawn.LUMBRIDGE, new WorldPoint(3206,3208,1), false,2100,actions);
        assertTrue(calls.get(2).startsWith("16672:Climb-up:"));
        route.tick(ZulrahDeathRecovery.Spawn.LUMBRIDGE, new WorldPoint(3206,3208,2), false,2200,actions);
        assertTrue(calls.get(3).startsWith("27291:Bank:"));
    }

    @Test public void outstandingMovementIsNotOverwrittenButStationaryFailureRetries() {
        ZulrahRecoveryBankRoute route = new ZulrahRecoveryBankRoute();
        WorldPoint spawn = new WorldPoint(3222,3217,0);
        route.tick(ZulrahDeathRecovery.Spawn.LUMBRIDGE,spawn,false,0,actions);
        route.tick(ZulrahDeathRecovery.Spawn.LUMBRIDGE,spawn,false,500,actions);
        route.tick(ZulrahDeathRecovery.Spawn.LUMBRIDGE,spawn,true,3000,actions);
        assertEquals(1,calls.size());
        route.tick(ZulrahDeathRecovery.Spawn.LUMBRIDGE,spawn,false,4000,actions);
        assertEquals(2,calls.size());
    }

    @Test public void edgevilleUsesRecordedBankWithoutCastleClimbs() {
        new ZulrahRecoveryBankRoute().tick(ZulrahDeathRecovery.Spawn.EDGEVILLE,
                new WorldPoint(3094,3470,0),false,0,actions);
        assertEquals(1,calls.size());
        assertTrue(calls.get(0).startsWith("10355:Bank:"));
    }
}
