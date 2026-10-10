/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;
import net.runelite.client.plugins.microbot.drofirecape.DroFirecapeConfig;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

public class OptionalPureRepositionWatchdogTest {
    private final Tile player=new Tile(62,60);
    private Mob melee(int hp,Tile tile){return new Mob(1,Kind.MELEER,tile,4,hp,10,428,Protection.MELEE,true);}
    private Snapshot room(int tick,Tile p,Mob... mobs){return new Snapshot(tick,p,new CollisionGrid(new int[104][104]),
        List.of(mobs),100,true,5,Protection.NONE);}
    private Plan approach(){return new Plan(new Tile(59,44),new Tile(61,46),Protection.NONE,-1,false,0,0,38,"Approach blocked ranger");}
    @Test public void wave24PrayerPrearmLoopIsBoundedWithoutAnyMovementDispatch(){
        PureRepositionWatchdog guard=new PureRepositionWatchdog();
        Mob melee=melee(-1,new Tile(62,46));Mob ranger=new Mob(2,Kind.RANGER,new Tile(62,43),3,-1,-1,428,Protection.RANGE,true);
        for(int tick=430;tick<460;tick++)assertFalse(guard.stalled(room(tick,player,melee,ranger),approach(),false));
        assertTrue(guard.stalled(room(460,player,melee,ranger),approach(),false));
    }
    @Test public void repeatedCallsAndChangedRequestsDoNotResetProgressDeadline(){
        PureRepositionWatchdog guard=new PureRepositionWatchdog();Mob melee=melee(10,new Tile(62,46));
        for(int i=0;i<100;i++)assertFalse(guard.stalled(room(10,player,melee),approach(),false));
        assertTrue(guard.stalled(room(40,player,melee),approach(),false));
    }
    @Test public void npcHealthProgressRestartsDeadline(){
        PureRepositionWatchdog guard=new PureRepositionWatchdog();
        guard.stalled(room(1,player,melee(10,new Tile(62,46))),approach(),false);
        assertFalse(guard.stalled(room(30,player,melee(9,new Tile(62,46))),approach(),false));
        assertFalse(guard.stalled(room(59,player,melee(9,new Tile(62,46))),approach(),false));
        assertTrue(guard.stalled(room(60,player,melee(9,new Tile(62,46))),approach(),false));
    }
    @Test public void enemyMovementCannotHideStationaryDispatchStallButPlayerMovementRestartsIt(){
        PureRepositionWatchdog guard=new PureRepositionWatchdog();
        guard.stalled(room(1,player,melee(10,new Tile(62,46))),approach(),false);
        assertFalse(guard.stalled(room(30,player,melee(10,new Tile(62,47))),approach(),false));
        assertTrue(guard.stalled(room(31,player,melee(10,new Tile(62,48))),approach(),false));
        assertFalse(guard.stalled(room(59,player.add(1,0),melee(10,new Tile(62,47))),approach(),false));
    }
    @Test public void activeAttackPlansDoNotTriggerRepositionFallback(){
        PureRepositionWatchdog guard=new PureRepositionWatchdog();
        Plan attack=new Plan(player,player,Protection.MELEE,1,true,0,0,0,"Attack");
        assertFalse(guard.stalled(room(1,player,melee(10,new Tile(62,46))),attack,false,1));
        assertFalse(guard.stalled(room(100,player,melee(10,new Tile(62,46))),attack,false,1));
    }
    @Test public void plannedButUnacknowledgedAttackTriggersFallback(){
        PureRepositionWatchdog guard=new PureRepositionWatchdog();
        Plan attack=new Plan(player,player,Protection.RANGE,1,false,0,0,10,"Kill bat");
        Mob bat=new Mob(1,Kind.BAT,player.add(0,1),1,-1,-1,-1,Protection.MELEE,true);
        assertFalse(guard.stalled(room(1,player,bat),attack,false,-1));
        assertTrue(guard.stalled(room(31,player,bat),attack,false,-1));
    }
    @Test public void actualAttackAcknowledgementResetsFailedAcquisitionDeadline(){
        PureRepositionWatchdog guard=new PureRepositionWatchdog();
        Plan attack=new Plan(player,player,Protection.MELEE,1,true,0,0,0,"Attack");
        guard.stalled(room(1,player,melee(10,new Tile(62,46))),attack,false,-1);
        assertFalse(guard.stalled(room(30,player,melee(10,new Tile(62,46))),attack,false,1));
        assertFalse(guard.stalled(room(31,player,melee(10,new Tile(62,46))),attack,false,-1));
    }
    @Test public void movementAndJadNeverTriggerRepositionFallback(){
        PureRepositionWatchdog guard=new PureRepositionWatchdog();
        assertFalse(guard.stalled(room(1,player,melee(10,new Tile(62,46))),approach(),true));
        assertFalse(guard.stalled(room(100,player,melee(10,new Tile(62,46))),approach(),true));
        Mob jad=new Mob(1,Kind.JAD,new Tile(62,46),5,10,10,1,Protection.MAGIC,true);
        assertFalse(guard.stalled(room(101,player,jad),approach(),false));
        assertFalse(guard.stalled(room(200,player,jad),approach(),false));
    }
    @Test public void recordingIsExposedAndEnabledByDefault()throws Exception {
        assertTrue(new DroFirecapeConfig(){}.recordRun());
        assertNotNull(DroFirecapeConfig.class.getMethod("recordRun").getAnnotation(net.runelite.client.config.ConfigItem.class));
    }
}
