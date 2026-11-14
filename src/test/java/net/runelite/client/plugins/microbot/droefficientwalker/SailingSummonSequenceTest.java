package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.Hull;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static net.runelite.client.plugins.microbot.droefficientwalker.EfficientSailingController.Status.*;

class SailingSummonSequenceTest {
    private static SailingSnapshot dock(int tick,int tablets){
        return new SailingSnapshot(1000,tick,99,99,5000,4,100000,0,-1,true,false,false,false,false,false,
            new WorldPoint(2685,3162,0),null,"none",new Hull(3,8,0),null,Collections.emptyList(),-1,null,tablets,10);
    }
    @ParameterizedTest
    @ValueSource(ints = {350, 450, 550})
    void waits350To550MillisecondsAfterFreshSummonReceipts(int delay)throws Exception {
        try(var f=new ControllerRecoveryIntegrationTest.Fixture();var random=mockStatic(Rs2Random.class)){
            random.when(() -> Rs2Random.betweenInclusive(350,550)).thenReturn(delay);
            f.tabletRecovery();f.status(AT_PORT);
            var port=new PortCatalog.Candidate(PortCatalog.byKey("VOID_KNIGHTS_OUTPOST"),dock(1,1).player,20,20,"dock",false);
            ControllerRecoveryIntegrationTest.set(f.controller,"selected",port);
            when(f.io.gangplankPoint(port.port.gangplankId)).thenReturn(dock(1,1).player);
            when(f.io.atDock(port.port.gangplankId)).thenReturn(true);
            when(f.io.breakSummonTablet(port.port.gangplankId)).thenReturn(true);
            when(f.io.board(port.port.gangplankId)).thenReturn(true);
            f.tick("tickDock",dock(1,1),1000);assertEquals(NEEDS_RECOVERY_SETTINGS,f.status());
            f.tick("prepareRecovery",dock(2,1),2000);assertEquals(SUMMONING_BOAT,f.status());
            f.tick("tickSummon",dock(3,1),3000);
            f.tick("tickSummon",dock(4,1),5000);verify(f.io,never()).board(anyInt());
            when(f.io.summonCasting()).thenReturn(true);
            f.tick("tickSummon",dock(5,0),6000);assertEquals(SUMMONING_BOAT,f.status());
            f.tick("tickSummon",dock(6,0),7000);verify(f.io,never()).board(anyInt());
            when(f.io.summonCasting()).thenReturn(false);
            f.tick("tickSummon",dock(7,0),8000);assertEquals(SUMMONING_BOAT,f.status());
            f.tick("tickSummon",dock(8,0),9000);assertEquals(SUMMONING_BOAT,f.status());
            f.tick("tickSummon",dock(9,0),10000);assertEquals(SUMMONING_BOAT,f.status());
            long readyAt=10000L+delay;
            f.tick("tickSummon",dock(10,0),readyAt-1);assertEquals(SUMMONING_BOAT,f.status());
            verify(f.io,never()).board(anyInt());
            f.tick("tickSummon",dock(10,0),readyAt);assertEquals(AT_PORT,f.status());
            f.tick("tickDock",dock(11,0),readyAt+150);assertEquals(BOARDING,f.status());
            f.tick("tickBoarding",dock(12,0),readyAt+4000);
            f.tick("tickBoarding",dock(13,0),readyAt+8000);
            random.verify(() -> Rs2Random.betweenInclusive(350,550),times(1));
            verify(f.io,times(1)).breakSummonTablet(port.port.gangplankId);
            verify(f.io,times(1)).board(port.port.gangplankId);
        }
    }
    @Test void castingAgainRestartsTheWaitWithoutConsumingAnotherTablet()throws Exception {
        try(var f=new ControllerRecoveryIntegrationTest.Fixture();var random=mockStatic(Rs2Random.class)){
            random.when(() -> Rs2Random.betweenInclusive(350,550)).thenReturn(450);
            f.tabletRecovery();f.status(SUMMONING_BOAT);
            var port=new PortCatalog.Candidate(PortCatalog.byKey("VOID_KNIGHTS_OUTPOST"),dock(1,1).player,20,20,"dock",false);
            ControllerRecoveryIntegrationTest.set(f.controller,"selected",port);
            when(f.io.atDock(port.port.gangplankId)).thenReturn(true);
            when(f.io.gangplankPoint(port.port.gangplankId)).thenReturn(dock(1,1).player);
            when(f.io.breakSummonTablet(port.port.gangplankId)).thenReturn(true);
            when(f.io.board(port.port.gangplankId)).thenReturn(true);
            f.tick("tickSummon",dock(1,1),1000);
            f.tick("tickSummon",dock(2,0),3000);assertEquals(SUMMONING_BOAT,f.status());
            f.tick("tickSummon",dock(3,0),4000);assertEquals(SUMMONING_BOAT,f.status());
            f.tick("tickSummon",dock(4,0),5000);assertEquals(SUMMONING_BOAT,f.status());
            when(f.io.summonCasting()).thenReturn(true);
            f.tick("tickSummon",dock(5,0),5200);assertEquals(SUMMONING_BOAT,f.status());
            when(f.io.summonCasting()).thenReturn(false);
            f.tick("tickSummon",dock(6,0),6000);
            f.tick("tickSummon",dock(7,0),7000);assertEquals(SUMMONING_BOAT,f.status());
            f.tick("tickSummon",dock(8,0),8000);assertEquals(SUMMONING_BOAT,f.status());
            f.tick("tickSummon",dock(9,0),8449);assertEquals(SUMMONING_BOAT,f.status());
            verify(f.io,never()).board(anyInt());
            f.tick("tickSummon",dock(9,0),8450);assertEquals(AT_PORT,f.status());
            f.tick("tickDock",dock(10,0),8600);assertEquals(BOARDING,f.status());
            random.verify(() -> Rs2Random.betweenInclusive(350,550),times(2));
            verify(f.io,times(1)).breakSummonTablet(port.port.gangplankId);
            verify(f.io,times(1)).board(port.port.gangplankId);
        }
    }
    @Test void postSummonBoardIsNotClickedAgainWhileWaitingForReceipt()throws Exception{
        try(var f=new ControllerRecoveryIntegrationTest.Fixture()){
            f.tabletRecovery();f.status(BOARDING);
            var port=new PortCatalog.Candidate(PortCatalog.byKey("VOID_KNIGHTS_OUTPOST"),dock(1,0).player,20,20,"dock",false);
            ControllerRecoveryIntegrationTest.set(f.controller,"selected",port);
            ControllerRecoveryIntegrationTest.set(f.controller,"phaseStarted",1000L);
            ControllerRecoveryIntegrationTest.set(f.controller,"boardingRetryAt",4500L);
            ControllerRecoveryIntegrationTest.set(f.controller,"summonIssued",true);
            when(f.io.atDock(port.port.gangplankId)).thenReturn(true);
            when(f.io.ui()).thenReturn(new MicrobotSailingFacade.UiState(Collections.emptyList(),false,false));
            f.tick("tickBoarding",dock(2,0),4000);verify(f.io,never()).board(anyInt());
            f.tick("tickBoarding",dock(3,0),5000);verify(f.io,never()).board(anyInt());
            f.tick("tickBoarding",dock(4,0),6500);verify(f.io,never()).board(anyInt());
            f.tick("tickBoarding",dock(5,0),9000);verify(f.io,never()).board(anyInt());
            f.tick("tickBoarding",dock(6,0),12_000);assertEquals(STOPPING,f.status());assertEquals(BLOCKED,ControllerRecoveryIntegrationTest.get(f.controller,"stopResult"));
            verify(f.io,never()).breakSummonTablet(anyInt());
        }
    }
}
