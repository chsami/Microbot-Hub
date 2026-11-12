package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.Hull;
import org.junit.jupiter.api.Test;
import static net.runelite.client.plugins.microbot.droefficientwalker.EfficientSailingController.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PestDockArrivalTest {
    private static SailingSnapshot land(int tick,WorldPoint player){
        return new SailingSnapshot(1000L+tick*600,tick,99,99,5000,20,-1,0,-1,true,false,false,false,false,false,
                player,null,"none",new Hull(1,3,0),null,Collections.emptyList(),-1,null,1,-1);
    }
    @Test void pestTeleportHandsOffToNativeGangplankInsteadOfTheReturnShip() throws Exception {
        try(var f=new ControllerRecoveryIntegrationTest.Fixture()){
            f.tabletRecovery();
            var port=PortCatalog.byKey("VOID_KNIGHTS_OUTPOST");
            WorldPoint landing=PortCatalog.landGateway(port.key);
            assertEquals(new WorldPoint(2658,2659,0),landing);
            ControllerRecoveryIntegrationTest.set(f.controller,"selected",new PortCatalog.Candidate(port,landing,0,140,"Pest teleport landing",false));
            ControllerRecoveryIntegrationTest.set(f.controller,"walkCompletion",AT_PORT);f.status(WALKING_TO_PORT);
            var arrived=new SailingWalkerJourney.JourneyState("ARRIVED","ARRIVED","NONE",null);
            when(f.walker.observe()).thenReturn(arrived);
            when(f.walker.arrivedOwned(arrived,new WorldPoint(2657,2658,0))).thenReturn(true);
            f.tick("tickWalk",land(1,new WorldPoint(2657,2658,0)),1600);
            assertEquals(AT_PORT,f.status());verify(f.walker).cancelOwned();

            // A loaded native dock supplies the local approach; the old Squire tile is unused.
            WorldPoint plank=new WorldPoint(2652,2682,0),approach=plank.dy(-1);
            when(f.io.gangplankPoint(port.gangplankId)).thenReturn(plank);
            when(f.walker.connect()).thenReturn(true);
            when(f.walker.dockApproach(new WorldPoint(2657,2658,0),plank)).thenReturn(approach);
            when(f.walker.walkTo(approach)).thenReturn(true);
            f.tick("tickDock",land(2,new WorldPoint(2657,2658,0)),2200);
            assertEquals(APPROACHING_GANGPLANK,f.status());verify(f.walker).walkTo(approach);
            verify(f.walker,never()).walkTo(new WorldPoint(2659,2676,0));
            f.status(AT_PORT);f.tick("tickDock",land(3,approach),2800);
            assertEquals(NEEDS_RECOVERY_SETTINGS,f.status());
            f.tick("prepareRecovery",land(4,approach),3400);
            assertEquals(SUMMONING_BOAT,f.status());verify(f.io,never()).board(anyInt());
        }
    }
}
