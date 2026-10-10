package net.runelite.client.plugins.microbot.drofirecape.optional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;

public class PureTrapRetentionTest {
    private CollisionGrid recordedGrid() throws Exception {return recordedGrid("pure-wave29-trap-collision.csv");}
    private CollisionGrid recordedGrid(String name) throws Exception {
        int[][] flags=new int[104][104];
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream(name),StandardCharsets.UTF_8))) {
            reader.readLine();String line;
            while((line=reader.readLine())!=null) {
                String[] row=line.split(",");flags[Integer.parseInt(row[0])][Integer.parseInt(row[1])]=Integer.parseInt(row[2]);
            }
        }
        return new CollisionGrid(flags);
    }
    private Snapshot frame(int tick,CollisionGrid grid,Tile player,Tile melee) {
        Mob mob=new Mob(33896,Kind.MELEER,melee,4,10,10,105,Protection.MELEE,true);
        return new Snapshot(tick,player,grid,List.of(mob),100,true,5,Protection.NONE);
    }
    private Plan decide(PureLureController lure,Snapshot scene) {
        Tile italy=new Tile(62,60);
        return lure.decide(scene,new PureCombatPlanner(),-1,List.of(italy),0,
            italy,new Tile(70,77),null,italy.add(-1,0));
    }
    @Test public void wave29ReturnDoesNotContinueWestAfterRockTrapSettles() throws Exception {
        CollisionGrid grid=recordedGrid();PureLureController lure=new PureLureController();
        Plan returning=decide(lure,frame(100,grid,new Tile(58,48),new Tile(58,50)));
        assertEquals(new Tile(62,60),returning.destination());
        Snapshot arrival=frame(110,grid,new Tile(62,60),new Tile(58,55));
        Plan settle=decide(lure,arrival);
        assertEquals(arrival.player(),settle.nextStep());
        assertEquals(arrival.player(),settle.destination());
        assertFalse(lure.hasPendingReturn());
        Snapshot trapped=frame(111,grid,new Tile(62,60),new Tile(58,56));
        Plan shot=decide(lure,trapped);
        assertEquals(trapped.player(),shot.nextStep());assertEquals(33896,shot.targetIndex());
        for(int tick=112;tick<116;tick++) {
            Plan retained=decide(lure,frame(tick,grid,trapped.player(),new Tile(58,56)));
            assertEquals(trapped.player(),retained.destination());assertEquals(33896,retained.targetIndex());
        }
    }
    @Test public void openGroundFollowerIsNotTreatedAsPermanentCover() {
        CollisionGrid grid=new CollisionGrid(new int[104][104]);
        Snapshot scene=frame(110,grid,new Tile(62,60),new Tile(58,55));
        assertNull(PureCombatPolicy.settlingWallShot(scene));
    }
    @Test public void wave21TinyBlobDoesNotRestartCompletedNorthernTrap() throws Exception {
        CollisionGrid grid=recordedGrid("pure-wave21-trap-collision.csv");
        PureLureController lure=new PureLureController();
        List<Mob> mobs=List.of(
            new Mob(1,Kind.MELEER,new Tile(61,60),4,10,10,-1,Protection.MELEE,true),
            new Mob(2,Kind.BLOB,new Tile(61,64),2,10,10,-1,Protection.MELEE,true),
            new Mob(3,Kind.BABY,new Tile(61,52),1,10,10,-1,Protection.MELEE,true));
        Snapshot s=new Snapshot(163,new Tile(61,54),grid,mobs,100,true,5,Protection.NONE);
        assertNotNull(PureCombatPolicy.establishedMeleeTrap(s));
        Plan p=decide(lure,s);
        assertEquals(s.player(),p.destination());assertEquals(3,p.targetIndex());
        assertFalse(lure.hasPendingReturn());
        Plan recovery=lure.recoverRecorded(s,new Tile(62,60),new Tile(61,60));
        assertEquals(s.player(),recovery.destination());assertEquals(3,recovery.targetIndex());
    }

    private Snapshot recordedWave28(CollisionGrid grid,Tile ranger) {
        return new Snapshot(1019,new Tile(61,57),grid,List.of(
            new Mob(1,Kind.MELEER,new Tile(63,60),4,10,10,-1,Protection.MELEE,true),
            new Mob(2,Kind.BLOB,new Tile(61,60),2,10,10,-1,Protection.MELEE,true),
            new Mob(3,Kind.BLOB,new Tile(61,48),2,10,10,-1,Protection.MELEE,true),
            new Mob(4,Kind.RANGER,ranger,3,10,10,-1,Protection.RANGE,true)),
            100,true,5,Protection.NONE).atWave(28);
    }
    @Test public void recordedWave28KeepsWallShotWhileRangerSheltered() throws Exception {
        CollisionGrid grid=recordedGrid("pure-wave21-trap-collision.csv");
        Snapshot s=recordedWave28(grid,new Tile(63,47));
        assertNotNull(PureCombatPolicy.establishedMeleeTrap(s));
        assertTrue(PureCombatPolicy.shelteredRangedAtWall(s));
        PureLureController lure=new PureLureController();Plan chosen=decide(lure,s);
        assertEquals(s.player(),chosen.destination());assertTrue(chosen.targetIndex()>0);
        assertNotEquals(4,chosen.targetIndex());assertFalse(lure.hasPendingReturn());
        assertEquals(s.player(),lure.recoverRecorded(s,new Tile(62,60),new Tile(61,60)).destination());
    }
    @Test public void exposedRangerAndLateWavePriorityReleaseShelteredHold() throws Exception {
        CollisionGrid grid=recordedGrid("pure-wave21-trap-collision.csv");
        Snapshot exposed=recordedWave28(grid,new Tile(58,54));
        assertFalse(PureCombatPolicy.shelteredRangedAtWall(exposed));
        Snapshot covered=recordedWave28(grid,new Tile(63,47));
        assertFalse(PureCombatPolicy.shelteredRangedAtWall(covered.atWave(53)));
        // Once the body shielding that shot disappears, the hold must release.
        Snapshot cleared=new Snapshot(1020,covered.player(),grid,List.of(covered.mobs().get(3)),
            100,true,5,Protection.NONE).atWave(28);
        assertFalse(PureCombatPolicy.shelteredRangedAtWall(cleared));
    }

    private Snapshot wave34Approach(int tick,CollisionGrid grid,Tile player,Tile mage,Tile blob) {
        return new Snapshot(tick,player,grid,List.of(
            new Mob(46900,Kind.MAGER,mage,5,10,10,-1,Protection.MAGIC,true),
            new Mob(46901,Kind.BLOB,blob,2,10,10,-1,Protection.MELEE,true)),
            100,true,5,Protection.NONE).atWave(34);
    }
    @Test public void wave34ApproachingBlobDoesNotCancelCommittedNorthRun() throws Exception {
        CollisionGrid grid=recordedGrid("pure-wave21-trap-collision.csv");
        PureLureController lure=new PureLureController();
        Snapshot opening=wave34Approach(92,grid,new Tile(59,58),new Tile(26,31),new Tile(51,31));
        assertEquals(new Tile(70,77),decide(lure,opening).destination());
        for(int tick=93;tick<=96;tick++) {
            Snapshot approaching=wave34Approach(tick,grid,tick==93?new Tile(61,60):new Tile(62,61),
                new Tile(27+tick-93,32+tick-93),new Tile(52+tick-93,32+tick-93));
            assertNull(PureCombatPolicy.establishedMeleeTrap(approaching));
            assertEquals(new Tile(70,77),decide(lure,approaching).destination());
            assertTrue(lure.hasPendingReturn());
        }
    }
    @Test public void crowdedFightCanAcquireSelectedStationaryShotWithoutBypassingLure() throws Exception {
        CollisionGrid grid=recordedGrid("pure-wave21-trap-collision.csv");
        Snapshot contact=wave34Approach(108,grid,new Tile(57,48),new Tile(38,43),new Tile(57,46));
        PureLureController lure=new PureLureController();Mob blob=contact.mobs().get(1);
        Plan shot=new Plan(contact.player(),contact.player(),Protection.MAGIC,blob.index(),false,0,0,7,"Selected protected shot");
        assertFalse(lure.allowsImmediateShot(contact,blob));
        assertTrue(lure.allowsImmediateShot(contact,blob,shot));
        assertFalse(lure.allowsImmediateShot(contact,contact.mobs().get(0),shot));
        Plan route=new Plan(new Tile(70,77),new Tile(64,61),Protection.MAGIC,blob.index(),false,0,0,7,"Continue lure");
        assertFalse(lure.allowsImmediateShot(contact,blob,route));
        PureLureController committedNorth=new PureLureController();
        decide(committedNorth,wave34Approach(92,grid,new Tile(59,58),new Tile(26,31),new Tile(51,31)));
        assertFalse(committedNorth.allowsImmediateShot(contact,blob,shot));
    }

}
