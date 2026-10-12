/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The .69 wave-51 escape was selected but never dispatched before its blocker died. */
class PureMovementHandoffTest {
    private Snapshot recorded()throws Exception {
        return new RetainedRunRegressionTest().scenes("pure-069-spacing").get(0);
    }
    private Snapshot at(Snapshot s,int tick) {
        int launch=6331+Math.max(0,(tick-6331)/4)*4;
        List<Mob> mobs=new ArrayList<>();
        for(Mob m:s.mobs())mobs.add(new Mob(m.index(),m.kind(),m.tile(),m.size(),m.healthRatio(),m.healthScale(),
            m.lastAttackTick()<0?-1:launch,m.lastStyle(),m.attackingPlayer()));
        return new Snapshot(tick,s.player(),s.grid(),mobs,100,true,s.weaponRange(),Protection.NONE).atWave(s.wave());
    }
    private FcFrame frame(FirecapeTestClient c,Snapshot s)throws Exception {
        c.instance=true;c.position=s.player();FcFrame f=c.frame(s.tick(),s.mobs(),Protection.NONE);
        FirecapeTestClient.set(f,"model",s);FirecapeTestClient.set(f,"cave",true);
        FirecapeTestClient.set(f,"running",true);return f;
    }
    private void observed(FirecapeTestClient c,Protection guard) {
        for(Rs2PrayerEnum p:List.of(Rs2PrayerEnum.PROTECT_MELEE,Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MAGIC))
            c.varbits.put(p.getVarbit(),0);
        if(guard!=Protection.NONE)c.varbits.put((guard==Protection.MAGIC?Rs2PrayerEnum.PROTECT_MAGIC:
            guard==Protection.RANGE?Rs2PrayerEnum.PROTECT_RANGE:Rs2PrayerEnum.PROTECT_MELEE).getVarbit(),1);
    }
    private OptionalTickPrayers calibrated(FirecapeTestClient c,AtomicLong clock,Snapshot s)throws Exception {
        OptionalTickPrayers d=new OptionalTickPrayers(c.client,false,x->{},clock::get);
        d.nativeWidgets(true);d.pureTinyPrayers(true);d.pureWave(s.wave());
        for(int tick=6331;tick<=6341;tick++) {
            clock.set((tick-6330)*600_000_000L);
            if((tick-6331)%4==0) {
                d.protection.animation(65367,Kind.MAGER,2647);
                d.protection.animation(65368,Kind.MELEER,2637);
                d.protection.animation(65371,Kind.BAT,2625);
            }
            d.gameTick(frame(c,at(s,tick)),Protection.NONE,clock.get(),Set.of());
            d.clientTick(true,false,false);observed(c,d.requested());d.clientTick(true,false,false);
        }
        return d;
    }
    @Test void escapePrearmUsesTheExistingMovementForecastAndReadinessGate()throws Exception {
        Snapshot s=recorded();FirecapeTestClient c=new FirecapeTestClient();AtomicLong clock=new AtomicLong();
        OptionalTickPrayers d=calibrated(c,clock,s);s=at(s,6341);
        Plan escape=new PureSpacing().decide(s,0);assertNotNull(escape);
        assertEquals(new Tile(48,25),escape.destination());assertEquals(Protection.MAGIC,escape.protection());
        d.primeMovement(s.tick(),escape.protection());d.clientTick(true,false,false);
        assertEquals(Protection.MAGIC,d.requested(),"The existing movement forecast supplies the route guard");
        assertTrue(d.protectionReady());
        assertTrue(d.movementReady(escape.protection()));
        Plan checked=MinimapMovement.checked(s,escape.destination(),escape.nextStep(),d.requested(),escape.reason());
        assertTrue(CombatPlanner.actionable(checked,false));assertTrue(PureSafety.routeAllowed(s,checked.nextStep()));
        d.clientTick(false,false,false);assertFalse(d.movementReady(escape.protection()),"Human input still owns the client");
    }
    @Test void escapeIsReleasedIfTheBlockerDiesBeforeDispatch()throws Exception {
        Snapshot s=at(recorded(),6341);PureSpacing spacing=new PureSpacing();assertNotNull(spacing.decide(s,0));
        List<Mob> survivors=new ArrayList<>(s.mobs());survivors.removeIf(m->m.index()==65371);
        Snapshot changed=new Snapshot(6342,s.player(),s.grid(),survivors,100,true,s.weaponRange(),Protection.NONE).atWave(s.wave());
        assertNull(spacing.decide(changed,0));assertNull(spacing.destination(),"Do not retain a route whose pursuit forecast has changed");
    }
    @Test void selectedEscapePrimesBeforeReadinessAndSendsOneCheckedMove()throws Exception {
        Snapshot s=recorded();FirecapeTestClient c=new FirecapeTestClient();AtomicLong clock=new AtomicLong();
        OptionalTickPrayers d=calibrated(c,clock,s);s=at(s,6341);FcFrame f=frame(c,s);
        OptionalFirecapeScript script=new OptionalFirecapeScript();
        class Actions extends OptionalActions {
            int moves;Tile sent;
            @Override boolean move(FcFrame current,Tile tile,boolean minimap){moves++;sent=tile;return true;}
        }
        Actions a=new Actions();a.tickPrayers=d;
        FirecapeTestClient.set(script,"actions",a);FirecapeTestClient.set(script,"tickPrayers",d);
        FirecapeTestClient.set(script,"config",new DroFirecapeConfig(){
            @Override public boolean pureMode(){return true;}
            @Override public boolean prayerConservation(){return false;}
        });
        FirecapeTestClient.set(script,"frame",f);script.waves.chat("Wave: 51",s.tick());
        d.ready=false;
        assertTrue(script.handlePureCombatOwned(f,Protection.MAGIC));
        assertEquals(Protection.MAGIC,d.movementGuard,"Selecting the escape must reach the existing pre-arm owner");
        assertEquals(0,a.moves,"Pre-arm intent alone cannot dispatch");
        d.clientTick(true,false,false);script.handlePureCombatOwned(f,Protection.MAGIC);
        assertEquals(1,a.moves);assertEquals(new Tile(48,25),a.sent);
        script.handlePureCombatOwned(f,Protection.MAGIC);assertEquals(1,a.moves,"Pending movement cannot spam another click");
    }
}
