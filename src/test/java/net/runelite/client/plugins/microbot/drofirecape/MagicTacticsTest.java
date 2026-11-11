/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.drofirecape.core.MagicTactics.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Ice stops melees and blobs in the open; Blood only heals (below 90%, stopping at 95%) and only from safety. */
class MagicTacticsTest {
    private static final Tile PLAYER=new Tile(30,30);
    private static Mob mob(int index,Kind kind,int x,int y) {
        return new Mob(index,kind,new Tile(x,y),kind.size,10,10,-1,kind.protection,false);
    }
    private static Snapshot scene(Mob... mobs) {
        return new Snapshot(100,PLAYER,new CollisionGrid(new int[104][104]),List.of(mobs),100,false,7,Protection.NONE);
    }
    private static Cast decide(Snapshot s,boolean healing,Map<Integer,Integer> frozen){return MagicTactics.decide(s,true,true,healing,frozen);}

    @Test void healingStartsBelowNinetyPercentAndStopsAtNinetyFive() {
        assertFalse(MagicTactics.healing(false,70,77),"70/77 is above 90%");
        assertTrue(MagicTactics.healing(false,68,77),"Live: Blood at 68/77");
        assertTrue(MagicTactics.healing(true,72,77),"Once started it continues below 95%");
        assertFalse(MagicTactics.healing(true,74,77),"Live: back to range at 74/77");
    }
    @Test void noSpellDuringJadOrWithoutRunes() {
        assertNull(decide(scene(mob(1,Kind.JAD,20,30),mob(2,Kind.BABY,36,30),mob(3,Kind.BABY,37,30)),true,Map.of()));
        assertNull(MagicTactics.decide(scene(mob(1,Kind.MELEER,24,30)),false,false,true,Map.of()));
    }
    @Test void magersAreNeverSpellTargets() {
        assertNull(decide(scene(mob(1,Kind.MAGER,40,40)),true,Map.of()),"A lone mager is shot with the main weapon");
        assertEquals(2,decide(scene(mob(1,Kind.MAGER,22,30),mob(2,Kind.MELEER,24,30)),false,new HashMap<>()).target,
            "An attacking mager no longer blocks a freeze");
    }
    @Test void iceFreezesBlobsAndSplitBlobPairsInTheOpen() {
        Cast blob=decide(scene(mob(1,Kind.BLOB,36,30),mob(2,Kind.RANGER,30,38)),false,new HashMap<>());
        assertEquals(Element.ICE,blob.element);assertEquals(1,blob.target,"Live wave 10: the big blob in the open");
        Cast pair=decide(scene(mob(1,Kind.BABY,34,30),mob(2,Kind.BABY,34,32),mob(3,Kind.RANGER,10,50)),false,new HashMap<>());
        assertEquals(Element.ICE,pair.element,"Two split blobs beside each other while a Tok-Xil is in the wave");
        assertNull(decide(scene(mob(1,Kind.BABY,34,30),mob(3,Kind.RANGER,10,50)),false,Map.of()),"A lone split blob is shot");
        assertEquals(1,decide(scene(mob(1,Kind.MELEER,24,30),mob(2,Kind.BLOB,36,30)),false,new HashMap<>()).target,"Big melee first");
    }
    @Test void smallMonstersAloneAreNeverFrozen() {
        // Live 2026-11-06 Firecape test waves 1-6: 21 Ice Bursts on bats and blobs slowed the waves and wasted runes.
        assertNull(decide(scene(mob(1,Kind.BAT,34,30)),false,new HashMap<>()),"A bat alone is shot");
        assertNull(decide(scene(mob(1,Kind.BLOB,36,30)),false,new HashMap<>()),"A blob alone is shot");
        assertNull(decide(scene(mob(1,Kind.BABY,34,30),mob(2,Kind.BABY,34,32),mob(3,Kind.BAT,36,36)),false,new HashMap<>()));
        Cast bat=decide(scene(mob(1,Kind.BAT,34,30),mob(2,Kind.MAGER,10,10)),false,new HashMap<>());
        assertEquals(Element.ICE,bat.element,"With a Ket-Zek in the wave the bat is frozen first");
    }
    @Test void iceFreezesABigMeleeInTheOpenOnly() {
        Cast freeze=decide(scene(mob(1,Kind.MELEER,24,30)),false,new HashMap<>());
        assertEquals(Element.ICE,freeze.element);assertEquals(1,freeze.target);
        assertNull(decide(scene(mob(1,Kind.MELEER,24,30)),false,Map.of(1,120)),"Already frozen");
        assertNull(decide(scene(mob(1,Kind.MELEER,26,30)),false,Map.of()),"In contact: too late to freeze");
        Cast bat=decide(scene(mob(1,Kind.MELEER,24,30),mob(2,Kind.BAT,34,30)),false,new HashMap<>());
        assertEquals(Element.ICE,bat.element);assertEquals(2,bat.target,"A bat is always frozen first: its hits drain prayer");
    }
    @Test void bloodOnlyHealsAndPrefersAClump() {
        Snapshot s=scene(mob(1,Kind.BABY,36,30),mob(2,Kind.BABY,37,30),mob(3,Kind.RANGER,20,40));
        Map<Integer,Integer> frozen=Map.of(1,200,2,200); // Already frozen, so only Blood applies.
        assertNull(decide(s,false,frozen),"Not healing: no Blood, even on a clump");
        Cast heal=decide(s,true,frozen);
        assertEquals(Element.BLOOD,heal.element);assertNotEquals(3,heal.target,"A clump is preferred over the ranger");
    }
    @Test void bloodPrefersAMonsterThatCannotHitThePlayer() {
        // A melee two tiles away cannot hit; the Tok-Xil in sight can. The heal goes on the harmless one.
        Cast heal=decide(scene(mob(1,Kind.RANGER,30,37),mob(2,Kind.MELEER,33,30)),true,Map.of(2,200));
        assertEquals(Element.BLOOD,heal.element);assertEquals(2,heal.target);
    }
    @Test void bloodHealsOnARangerFirstWithNoSafespotNeeded() {
        Cast ranger=decide(scene(mob(2,Kind.RANGER,30,37)),true,Map.of());
        assertEquals(Element.BLOOD,ranger.element);assertEquals(2,ranger.target,"Live wave 40: heal on the Tok-Xil hitting you");
        assertEquals(Element.BLOOD,decide(scene(mob(1,Kind.MELEER,31,30)),true,Map.of()).element,"Contact no longer blocks a heal");
        assertNull(decide(scene(mob(1,Kind.BLOB,50,30)),true,Map.of()),"Beyond ten tiles");
    }

    // Blood only from safety: at most one attack style can reach the player's tile, now or after the next step
    // (user, 2026-11-07; live 1.1.42 wave 28).
    private static final Mob CONTACT_BLOB=mob(11,Kind.BLOB,31,30),SHOOTING_RANGER=mob(12,Kind.RANGER,30,37),
        CASTING_MAGER=mob(13,Kind.MAGER,20,30);
    private static Cast heal(Snapshot s){return MagicTactics.decide(s,false,true,true,Map.of());}
    @Test void bloodHealsWhenOnePrayerCoversEverythingInReach() {
        assertNotNull(heal(scene(mob(1,Kind.MELEER,40,30))),"Nothing can reach the player");
        assertNotNull(heal(scene(SHOOTING_RANGER)),"One Tok-Xil: flick Range while healing");
        assertNotNull(heal(scene(CONTACT_BLOB,mob(2,Kind.MELEER,26,30))),"Several melees: flick Melee while healing");
        assertNotNull(heal(scene(CASTING_MAGER,mob(3,Kind.BAT,40,40))),"One Ket-Zek: flick Magic while healing");
    }
    @Test void bloodHealsOnATrappedMeleeWhileAKetZekCasts() {
        int[][] flags=new int[104][104];
        // Rocks south of a Yt-MejKot (x 30-33, y 33-36) hold it two tiles from the player.
        for(int x=31;x<=33;x++)for(int y=31;y<=32;y++)flags[x][y]=CollisionGrid.OBJECT;
        Mob trapped=mob(1,Kind.MELEER,30,33);
        Snapshot s=new Snapshot(100,PLAYER,new CollisionGrid(flags),List.of(trapped,CASTING_MAGER),100,false,7,Protection.NONE);
        assertTrue(CaveSafety.trapped(s,trapped),"Precondition: the rocks hold the Yt-MejKot");
        Cast cast=heal(s);
        assertNotNull(cast,"Only Magic can reach the player");assertEquals(1,cast.target);
    }
    @Test void bloodNeverHealsWhenTwoStylesCanReachThePlayer() {
        assertNull(heal(scene(CONTACT_BLOB,SHOOTING_RANGER)),"Melee and Range (live 1.1.42 wave 28)");
        assertNull(heal(scene(CONTACT_BLOB,CASTING_MAGER)),"Melee and Magic");
        assertNull(heal(scene(SHOOTING_RANGER,CASTING_MAGER)),"Range and Magic");
        assertNull(heal(scene(CONTACT_BLOB,SHOOTING_RANGER,CASTING_MAGER)),"All three");
    }
    @Test void aMeleeOneStepFromContactCountsWithAShooter() {
        assertNotNull(heal(scene(mob(1,Kind.MELEER,32,30))),"The Yt-MejKot alone: Melee only");
        assertNull(heal(scene(mob(1,Kind.MELEER,32,30),SHOOTING_RANGER)),"Its next step joins the Tok-Xil's Range");
    }
}
