package net.runelite.client.plugins.microbot.drozulrah;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahDeathRecoveryTest {
    private static class Run {
        final ZulrahDeathRecovery recovery=new ZulrahDeathRecovery();
        final ZulrahDeathRecovery.Frame f=new ZulrahDeathRecovery.Frame();
        final List<String> calls=new ArrayList<>();
        boolean emptyPriestess,claimWorks=true,teleportWorks=true;
        final ZulrahDeathRecovery.Actions actions=new ZulrahDeathRecovery.Actions() {
            public void bank(ZulrahDeathRecovery.Spawn spawn){calls.add("bank:"+spawn);f.bankOpen=true;}
            public void withdrawRing(){calls.add("ring");f.ring=true;}
            public void withdrawTeleport(){calls.add("teleport-item");f.teleport=true;}
            public void closeBank(){calls.add("close");f.bankOpen=false;}
            public boolean teleport(){calls.add("shore");if(teleportWorks)f.here=new WorldPoint(2195,3055,0);f.teleport=false;return true;}
            public boolean collect(){calls.add("collect");f.nothingToCollect=emptyPriestess;f.hasContinue=emptyPriestess;
                f.retrievalOpen=!emptyPriestess;f.retrievalItems=emptyPriestess?0:7;return true;}
            public boolean reclaim(){calls.add("reclaim");if(claimWorks){f.retrievalOpen=false;f.retrievalItems=0;f.carriedSlots+=7;}return true;}
            public void clearInterface(){calls.add("clear");f.hasContinue=false;f.retrievalOpen=false;}
            public boolean ferox(){calls.add("ferox");f.here=new WorldPoint(3153,3635,0);return true;}
        };
        Run(WorldPoint spawn){f.here=spawn;f.bankReady=true;f.ringStock=true;f.teleportStock=true;}
        void pump(){for(long t=0;t<60000 && !recovery.done() && !recovery.stopped();t+=800)recovery.tick(f,t,actions);}
    }
    @Test public void bothRecordedSpawnsUseLocalBankThenSameReclaimAndFeroxRoute() {
        for(WorldPoint p:Arrays.asList(new WorldPoint(3222,3217,0),new WorldPoint(3094,3470,0))) {
            Run r=new Run(p);r.pump();assertTrue(r.recovery.done(),r.recovery.status());
            assertEquals(Arrays.asList("bank:"+ZulrahDeathRecovery.spawnAt(p),"ring","teleport-item","close","shore","collect","reclaim","ferox"),r.calls);
            List<String> previous=new ArrayList<>(r.calls);r.recovery.tick(r.f,70000,r.actions);assertEquals(previous,r.calls);
        }
    }
    @Test public void missingRingStopsBeforeWithdrawalOrTravel() {
        Run r=new Run(new WorldPoint(3222,3217,0));r.f.ringStock=false;r.pump();
        assertTrue(r.recovery.stopped());assertTrue(r.recovery.status().contains("no charged Ring"));
        assertEquals(Arrays.asList("bank:LUMBRIDGE"),r.calls);
    }
    @Test public void missingZulAndraTeleportStopsBeforeWithdrawalOrTravel() {
        Run r=new Run(new WorldPoint(3094,3470,0));r.f.teleportStock=false;r.pump();
        assertTrue(r.recovery.stopped());assertTrue(r.recovery.status().contains("no Zul-andra"));
        assertEquals(Arrays.asList("bank:EDGEVILLE"),r.calls);
    }
    @Test public void otherRespawnStopsWithoutAnyActions() {
        Run r=new Run(new WorldPoint(2966,3380,0));r.pump();
        assertTrue(r.recovery.stopped());assertTrue(r.recovery.status().contains("unsupported respawn"));assertTrue(r.calls.isEmpty());
    }
    @Test public void deathAnimationInArenaWaitsForSupportedRespawnBeforeBanking() {
        Run r=new Run(new WorldPoint(2268,3068,0));r.f.arena=true;
        r.recovery.tick(r.f,0,r.actions);r.recovery.tick(r.f,1000,r.actions);assertTrue(r.calls.isEmpty());
        r.f.arena=false;r.f.here=new WorldPoint(3222,3217,0);r.recovery.tick(r.f,2000,r.actions);
        assertEquals(ZulrahDeathRecovery.Stage.BANK,r.recovery.stage());
    }
    @Test public void recordedEmptyPriestessDialogueIsClearedBeforeFerox() {
        Run r=new Run(new WorldPoint(3222,3217,0));r.emptyPriestess=true;r.pump();
        assertTrue(r.recovery.done());assertFalse(r.calls.contains("reclaim"));
        assertTrue(r.calls.indexOf("clear")<r.calls.indexOf("ferox"));
    }
    @Test public void unobservedReclaimStopsRatherThanReturningToFight() {
        Run r=new Run(new WorldPoint(3094,3470,0));r.claimWorks=false;r.pump();
        assertTrue(r.recovery.stopped());assertTrue(r.recovery.status().contains("reclaim not completed"));assertFalse(r.calls.contains("ferox"));
    }
    @Test public void failedTeleportIsNotRepeatedAndNeverCollectsOrBoards() {
        Run r=new Run(new WorldPoint(3222,3217,0));r.teleportWorks=false;r.pump();
        assertTrue(r.recovery.stopped());assertTrue(r.recovery.status().contains("teleport did not arrive"));assertFalse(r.calls.contains("collect"));
    }
    @Test public void bankContentsMustBeFreshBeforeMissingSupplyStop() {
        Run r=new Run(new WorldPoint(3222,3217,0));
        r.f.bankReady=false;r.f.ringStock=false;r.f.teleportStock=false;
        for(long t=0;t<3000;t+=100)r.recovery.tick(r.f,t,r.actions);
        assertFalse(r.recovery.stopped());assertEquals(ZulrahDeathRecovery.Stage.SUPPLIES,r.recovery.stage());
        assertEquals(Arrays.asList("bank:LUMBRIDGE"),r.calls);
        r.f.bankReady=true;r.recovery.tick(r.f,3100,r.actions);
        assertTrue(r.recovery.stopped());
    }
    @Test public void observedBankOpeningAdvancesWithoutBlanketInputDelay() {
        Run r=new Run(new WorldPoint(3222,3217,0));
        r.recovery.tick(r.f,0,r.actions);
        r.recovery.tick(r.f,100,r.actions);
        r.recovery.tick(r.f,200,r.actions);
        assertEquals(ZulrahDeathRecovery.Stage.SUPPLIES,r.recovery.stage());
        r.recovery.tick(r.f,300,r.actions);
        assertEquals(Arrays.asList("bank:LUMBRIDGE"),r.calls);
        r.recovery.tick(r.f,800,r.actions);
        assertEquals(Arrays.asList("bank:LUMBRIDGE","ring"),r.calls);
    }
}
