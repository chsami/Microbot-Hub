package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.Hull;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;
import org.junit.jupiter.api.Test;
import static net.runelite.client.plugins.microbot.droefficientwalker.EfficientSailingController.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SailingPortSelectionTest {
    private static final SeaPoint SOUTH_PEST=new SeaPoint(2633,2544);
    private static SailingSnapshot land(int tick,long now,WorldPoint player) {
        return land(tick,now,player,5000);
    }
    private static SailingSnapshot land(int tick,long now,WorldPoint player,int coins) {
        return new SailingSnapshot(now,tick,99,99,coins,20,-1,0,-1,true,false,false,false,false,false,
                player,null,"none",new Hull(1,3,0),null,Collections.emptyList(),-1,null,1,-1);
    }
    private static void tick(ControllerRecoveryIntegrationTest.Fixture f,SailingSnapshot s) throws Exception {
        f.now.set(s.capturedMillis);when(f.io.latest()).thenReturn(s);
        ControllerRecoveryIntegrationTest.call(f.controller,"tick",new Class<?>[0]);
    }
    private static void request(ControllerRecoveryIntegrationTest.Fixture f,LaunchPort launch) {
        when(f.config.currentBoatPort()).thenReturn(BoatPort.UNKNOWN_LOST);
        when(f.config.launchPort()).thenReturn(launch);
        when(f.config.useSummonBoatTablet()).thenReturn(true);
        when(f.config.boatName()).thenReturn("Giant Mind");
        when(f.walker.connect()).thenReturn(true);
        when(f.walker.walkTo(any())).thenReturn(true);
        NavigationSessions.Caller owner=f.controller.registerCaller(new Object(),()->true);
        assertTrue(f.controller.request(owner,SOUTH_PEST).accepted);
    }
    @Test public void closestPestSelectionIgnoresRetiredSelectorsAndReachesLandHandoff() throws Exception {
        for(LaunchPort launch:new LaunchPort[]{LaunchPort.AUTOMATIC,LaunchPort.VOID_KNIGHTS_OUTPOST}) {
            try(ControllerRecoveryIntegrationTest.Fixture f=new ControllerRecoveryIntegrationTest.Fixture()) {
                request(f,launch);
                when(f.walker.estimateNearestDock(any(),any(),any(),any())).thenAnswer(i->{
                    SeaPoint sea=i.getArgument(0);WorldPoint gateway=i.getArgument(2);
                    return new SailingWalkerJourney.Estimate(gateway==null?new WorldPoint(sea.x+1,sea.y,0):gateway,0,"standable approach");
                });
                WorldPoint start=new WorldPoint(3167,3487,0);
                tick(f,land(1,1000,start));assertEquals(COMPARING_PORTS,f.status());
                tick(f,land(2,1600,start));
                Future<?> future=(Future<?>)ControllerRecoveryIntegrationTest.get(f.controller,"portFuture");
                List<?> candidates=(List<?>)future.get(2,TimeUnit.SECONDS);
                assertEquals(3,candidates.size());
                tick(f,land(3,2200,start));assertEquals(WALKING_TO_PORT,f.status());
                PortCatalog.Candidate selected=(PortCatalog.Candidate)ControllerRecoveryIntegrationTest.get(f.controller,"selected");
                assertEquals("VOID_KNIGHTS_OUTPOST",selected.port.key);
                assertEquals(PortCatalog.landGateway(selected.port.key),selected.approach);
                verify(f.walker).walkTo(new WorldPoint(2658,2659,0));
                verify(f.walker,never()).walkTo(new WorldPoint(2659,2676,0));
                verify(f.walker,never()).estimate(any(),any(),any(),any());
            }
        }
    }
    @Test public void lostBoatAtDockGoesToTabletRecallBeforeBoarding() throws Exception {
        try(ControllerRecoveryIntegrationTest.Fixture f=new ControllerRecoveryIntegrationTest.Fixture()) {
            request(f,LaunchPort.VOID_KNIGHTS_OUTPOST);
            tick(f,land(1,1000,new WorldPoint(3167,3487,0)));
            WorldPoint dock=new WorldPoint(2659,2676,0);
            PortCatalog.Port port=PortCatalog.byKey("VOID_KNIGHTS_OUTPOST");
            ControllerRecoveryIntegrationTest.set(f.controller,"selected",new PortCatalog.Candidate(port,dock,0,139,"fixture",false));
            when(f.io.gangplankPoint(port.gangplankId)).thenReturn(dock);
            f.status(AT_PORT);
            tick(f,land(2,1600,dock));assertEquals(NEEDS_RECOVERY_SETTINGS,f.status());
            tick(f,land(3,2200,dock));assertEquals(SUMMONING_BOAT,f.status());
            verify(f.io,never()).board(anyInt());
            verify(f.walker,never()).walkToBank();
        }
    }
    @Test public void tabletOffAutomaticallyChoosesNearestShipwrightAndPaysOneFee() throws Exception {
        try(ControllerRecoveryIntegrationTest.Fixture f=new ControllerRecoveryIntegrationTest.Fixture()) {
            request(f,LaunchPort.VOID_KNIGHTS_OUTPOST); // Retired saved choice must have no effect.
            when(f.config.useSummonBoatTablet()).thenReturn(false);
            when(f.config.allowRecovery()).thenReturn(false); // Retired off value must not block automatic recovery.
            when(f.walker.estimateNearestDock(any(),any(),any(),any())).thenAnswer(i->{
                SeaPoint sea=i.getArgument(0);
                return new SailingWalkerJourney.Estimate(new WorldPoint(sea.x+1,sea.y,0),0,"standable approach");
            });
            WorldPoint start=new WorldPoint(3167,3487,0);
            tick(f,land(1,1000,start,80000));assertEquals(COMPARING_PORTS,f.status());
            tick(f,land(2,1600,start,80000));
            ((Future<?>)ControllerRecoveryIntegrationTest.get(f.controller,"portFuture")).get(2,TimeUnit.SECONDS);
            tick(f,land(3,2200,start,80000));
            PortCatalog.Candidate selected=(PortCatalog.Candidate)ControllerRecoveryIntegrationTest.get(f.controller,"selected");
            assertEquals("PORT_KHAZARD",selected.port.key);
            WorldPoint dock=selected.approach;
            when(f.io.gangplankPoint(selected.port.gangplankId)).thenReturn(dock);
            f.status(AT_PORT);tick(f,land(4,2800,dock,80000));assertEquals(NEEDS_RECOVERY_SETTINGS,f.status());
            when(f.io.beginRecovery(42)).thenReturn(true);
            SailingSnapshot atShipwright=new SailingSnapshot(3400,5,99,99,80000,20,-1,0,-1,
                    true,false,false,false,false,false,dock,null,"none",new Hull(1,3,0),null,
                    Collections.emptyList(),42,dock,1,-1);
            tick(f,atShipwright);assertEquals(RECOVERING_BOAT,f.status());
            when(f.io.chooseBoat(any(),eq(true))).thenReturn(true);
            when(f.io.payRecovery(any(),anyLong(),anyInt())).thenReturn(true);
            tick(f,land(6,4000,dock,80000));
            tick(f,land(7,4600,dock,80000)); // Interface pacing has not elapsed yet.
            tick(f,land(8,5200,dock,80000));
            tick(f,land(9,6400,dock,80000));
            verify(f.io).beginRecovery(42);
            long feeLimit=f.config.maxRecoveryFee();
            verify(f.io,times(1)).payRecovery(any(),eq(feeLimit),eq(80000));
            verify(f.io,never()).breakSummonTablet(anyInt());
        }
    }
}
