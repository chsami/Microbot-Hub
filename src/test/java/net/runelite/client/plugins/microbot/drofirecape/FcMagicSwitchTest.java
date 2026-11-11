/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The carried magic switch follows Colosseum 1.0.82 gear labelling. */
class FcMagicSwitchTest {
    private static FcMagicSwitch.Piece piece(int id,int slot,int inv,int score,boolean twoHanded,String name) {
        return new FcMagicSwitch.Piece(id,slot,inv,score,twoHanded,false,name,slot==3?"Wield":"Wear");
    }
    private static FcMagicSwitch.Piece piece(int id,int slot,int inv,int score,boolean twoHanded) {return piece(id,slot,inv,score,twoHanded,"item "+id);}
    private static Map<Integer,FcMagicSwitch.Piece> worn(FcMagicSwitch.Piece... pieces) {
        Map<Integer,FcMagicSwitch.Piece> map=new TreeMap<>();for(FcMagicSwitch.Piece p:pieces)map.put(p.slot,p);return map;
    }
    private static List<Integer> ids(List<FcMagicSwitch.Piece> pieces){return pieces.stream().map(p->p.id).collect(Collectors.toList());}
    private static final FcMagicSwitch.Piece BLOWPIPE=piece(12926,3,-1,0,true,"Toxic blowpipe");

    @Test void bestMagicWeaponByColosseumRankThenBestPiecePerSlotInClickOrder() {
        var loadout=new FcMagicSwitch.Loadout(List.of(piece(4675,3,1,150,false,"Ancient staff"),piece(21006,3,2,250,false,"Kodai wand"),
            piece(2,4,5,300,false),piece(3,4,6,200,false),piece(5,2,8,100,false)),worn(BLOWPIPE,piece(1,4,-1,-100,false)),3);
        assertEquals(List.of(21006,5,2),ids(FcMagicSwitch.mageSwitch(loadout)),"Kodai (80) over the ancient staff (20); amulet before body");
    }
    @Test void enteringInTheMagicSwitchMakesTheCarriedRangedSetMain() {
        // Live 2026-11-06 Firecape test: Master wand, Unholy book and Occult worn; Rosewood blowpipe and Amulet of fury carried.
        var wand=piece(6914,3,-1,520,false,"Master wand");var book=piece(3842,5,-1,83,false,"Unholy book");
        var occult=piece(12002,2,-1,270,false,"Occult necklace");var hood=piece(20595,0,-1,90,false,"Elder chaos hood");
        var rosewood=piece(28869,3,0,-80,true,"Rosewood blowpipe");var fury=piece(6585,2,1,103,false,"Amulet of fury");
        var loadout=new FcMagicSwitch.Loadout(List.of(rosewood,fury),worn(wand,book,occult,hood),1);
        Map<Integer,Integer> main=FcMagicSwitch.rangedMain(loadout);
        assertEquals(28869,main.get(3),"the blowpipe is the main weapon");
        assertEquals(6585,main.get(2),"fury replaces the occult");
        assertFalse(main.containsKey(5),"a two-handed main weapon empties the offhand");
        assertEquals(20595,main.get(0),"slots with nothing carried keep the worn piece");
        assertEquals(List.of(28869,6585),ids(FcMagicSwitch.restore(loadout,main)),"restore puts the blowpipe on first");
    }
    @Test void aMagicOnlyLoadoutOrARangedStartKeepsItsGear() {
        var wand=piece(6914,3,-1,520,false,"Master wand");
        assertTrue(FcMagicSwitch.rangedMain(new FcMagicSwitch.Loadout(List.of(),worn(wand),3)).isEmpty(),"no ranged weapon carried");
        assertTrue(FcMagicSwitch.rangedMain(new FcMagicSwitch.Loadout(List.of(wand),worn(BLOWPIPE),3)).isEmpty(),"already in the ranged set");
    }
    @Test void anyWeaponCanCastSoNoMagicWeaponIsRequired() {
        var loadout=new FcMagicSwitch.Loadout(List.of(piece(2,4,5,300,false)),worn(BLOWPIPE),3);
        assertEquals(List.of(2),ids(FcMagicSwitch.mageSwitch(loadout)));
    }
    @Test void twoHandedWeaponNeverTakesAnOffhand() {
        var book=piece(6889,5,9,150,false,"Mage's book");
        assertTrue(FcMagicSwitch.mageSwitch(new FcMagicSwitch.Loadout(List.of(piece(7,3,1,200,true,"Staff"),book),worn(),3))
            .stream().noneMatch(p->p.slot==5));
        assertTrue(FcMagicSwitch.mageSwitch(new FcMagicSwitch.Loadout(List.of(book),worn(BLOWPIPE),3)).isEmpty(),
            "A book alone would unequip the two-handed blowpipe");
        assertEquals(List.of(4675,6889),ids(FcMagicSwitch.mageSwitch(new FcMagicSwitch.Loadout(List.of(piece(4675,3,1,150,false,"Ancient staff"),book),worn(),3))));
    }
    @Test void gearOnlyComesOffIntoAFreeInventorySlot() {
        var staff=piece(7,3,1,200,true,"Staff");var shield=piece(8,5,-1,0,false,"Shield");
        var full=new FcMagicSwitch.Loadout(List.of(staff),worn(piece(9,3,-1,0,false,"Sword"),shield),0);
        assertTrue(FcMagicSwitch.mageSwitch(full).isEmpty(),"The worn shield has nowhere to go");
        var room=new FcMagicSwitch.Loadout(List.of(staff),worn(piece(9,3,-1,0,false,"Sword"),shield),1);
        assertEquals(List.of(7),ids(FcMagicSwitch.mageSwitch(room)));
        var restore=new FcMagicSwitch.Loadout(List.of(BLOWPIPE),worn(piece(4675,3,-1,150,false),piece(6889,5,-1,150,false)),0);
        assertTrue(FcMagicSwitch.needsRoom(restore,FcMagicSwitch.restore(restore,Map.of(3,12926)).get(0)),
            "The two-handed main weapon waits for room for the worn book");
    }
    @Test void voidMageHelmAndBlueMoonSetWinTheirSlots() {
        assertEquals(10_000,FcMagicSwitch.score(11663,"Void mage helm",null));
        assertEquals(0,FcMagicSwitch.score(11664,"Void ranger helm",null));
        var spear=piece(FcMagicSwitch.BLUE_MOON_SPEAR,3,1,FcMagicSwitch.score(FcMagicSwitch.BLUE_MOON_SPEAR,"Blue moon spear",null),true,"Blue moon spear");
        var moonHelm=piece(29013,0,2,50,false,"Blue moon helm");var ancestral=piece(21018,0,3,80,false,"Ancestral hat");
        var out=FcMagicSwitch.mageSwitch(new FcMagicSwitch.Loadout(List.of(spear,moonHelm,ancestral),worn(BLOWPIPE),3));
        assertEquals(List.of(FcMagicSwitch.BLUE_MOON_SPEAR,29013),ids(out));
    }
    @Test void missingStatsFallBackToTheColosseumTableThenTheName() {
        assertEquals(5,FcMagicSwitch.slot(6889,"Mage's book",null));
        assertEquals(150,FcMagicSwitch.score(6889,"Mage's book",null));
        assertEquals((12+15)*10,FcMagicSwitch.score(12002,"Occult necklace",null));
        assertEquals(1000,FcMagicSwitch.score(1,"Ahrim's robeskirt",null));
        assertEquals(-1,FcMagicSwitch.slot(2,"Saradomin brew(4)",null));
    }
    @Test void roomDropTakesTheBrewWithTheFewestDoses() {
        var inv=List.of(new FcFrame.ItemSlot(3,6685,1,"Saradomin brew(4)",List.of("Drink","Drop")),
            new FcFrame.ItemSlot(7,6691,1,"Saradomin brew(2)",List.of("Drink","Drop")),
            new FcFrame.ItemSlot(9,3024,1,"Super restore(1)",List.of("Drink","Drop")));
        assertEquals(7,FcMagicSwitch.roomBrew(inv).slot());
        assertNull(FcMagicSwitch.roomBrew(List.of(new FcFrame.ItemSlot(1,3024,1,"Super restore(4)",List.of()))),"Only brews are dropped");
    }
    @Test void restorePutsBackOnlyTheMainSetWeaponFirst() {
        var loadout=new FcMagicSwitch.Loadout(List.of(piece(1,4,3,-10,false),piece(12926,3,4,0,true),piece(99,0,5,0,false)),
            worn(piece(4,3,-1,15,false),piece(2,4,-1,30,false)),1);
        Map<Integer,Integer> main=Map.of(3,12926,4,1);
        assertEquals(List.of(12926,1),ids(FcMagicSwitch.restore(loadout,main)));
        assertTrue(FcMagicSwitch.restore(new FcMagicSwitch.Loadout(List.of(),worn(piece(12926,3,-1,0,true),piece(1,4,-1,0,false))),main).isEmpty());
    }
    @Test void attemptsBlockThenRetryAndDeclineAPieceThatNeverGoesOnWithTheWeapon() {
        var attempts=new FcMagicSwitch.Attempts();
        var book=piece(6889,5,9,150,false);var staff=piece(4675,3,1,150,false);
        var noWeapon=new FcMagicSwitch.Loadout(List.of(book),worn(),3);
        for(int t=0;t<4;t++){attempts.observe(noWeapon,t);attempts.requested(List.of(book),noWeapon,t);}
        assertTrue(attempts.excluded().contains(6889),"Set aside after four requests");
        attempts.observe(noWeapon,4+FcMagicSwitch.Attempts.BLOCK_TICKS);
        assertFalse(attempts.excluded().contains(6889),"Retried after the block");
        var withStaff=new FcMagicSwitch.Loadout(List.of(book),worn(staff),3);
        for(int t=100;t<104;t++){attempts.observe(withStaff,t);attempts.requested(List.of(book),withStaff,t);}
        attempts.observe(withStaff,200);
        assertTrue(attempts.excluded().contains(6889),"Declined for the run: input works, only the book stays off");
    }
    @Test void reclickGuardHoldsForOnePointTwoSeconds() {
        var attempts=new FcMagicSwitch.Attempts();
        attempts.clicked(1,0);
        assertTrue(attempts.recentlyClicked(1,1_199_000_000L));
        assertFalse(attempts.recentlyClicked(1,1_200_000_000L));
    }
}
