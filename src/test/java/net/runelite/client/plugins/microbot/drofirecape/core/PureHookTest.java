/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PureHookTest {
    private static Mob mob(int id,Kind kind,int x,int y){return new Mob(id,kind,new Tile(x,y),kind.size,9,30,1825,kind.protection,true);}
    private static Snapshot scene(Mob...mobs){return new Snapshot(1825,new Tile(34,26),new CollisionGrid(new int[104][104]),List.of(mobs),88,true,5,Protection.NONE);}
    private final Mob melee=mob(1,Kind.MELEER,35,27),ranger=mob(2,Kind.RANGER,32,29),mage=mob(3,Kind.MAGER,40,26);
    private final Snapshot exposedRelease=scene(melee,ranger,mage);

    @Test void plannerReleaseHookBlocksOnlyPureShotsThatReleaseAnotherShooter() {
        assertFalse(PureSafety.releaseSafe(exposedRelease,ranger,Protection.RANGE));
        CombatPlanner regular=new CombatPlanner();PureCombatPlanner pure=new PureCombatPlanner();
        assertTrue(regular.releaseAllowed(exposedRelease,ranger,Protection.RANGE));
        assertFalse(pure.releaseAllowed(exposedRelease,ranger,Protection.RANGE));
        assertTrue(regular.attackAllowed(exposedRelease,ranger,Protection.RANGE,false));
        assertFalse(pure.attackAllowed(exposedRelease,ranger,Protection.RANGE,false));
    }
    @Test void plannerTargetHookSkipsUnsafeReleaseAndUsesPurePriority() {
        CombatPlanner regular=new CombatPlanner();PureCombatPlanner pure=new PureCombatPlanner();
        Snapshot s=exposedRelease;
        assertEquals(CombatPlanner.SKIP_TARGET,pure.targetScore(s,s.player(),s.mobs(),ranger,Protection.RANGE));
        assertEquals(CombatPlanner.priority(ranger,Protection.RANGE)+(CaveSafety.lateAttackingRanger(s,ranger)?100:0),
            regular.targetScore(s,s.player(),s.mobs(),ranger,Protection.RANGE));
        Snapshot alone=scene(melee,ranger);
        assertTrue(PureSafety.releaseSafe(alone,ranger,Protection.RANGE));
        assertEquals(PureCombatPolicy.targetPriority(alone,ranger,Protection.RANGE),pure.targetScore(alone,alone.player(),alone.mobs(),ranger,Protection.RANGE));
    }
    @Test void plannerScoringAndRouteHooksDelegateToPurePolicy() {
        CombatPlanner regular=new CombatPlanner();PureCombatPlanner pure=new PureCombatPlanner();
        Snapshot s=exposedRelease;Tile step=s.player().add(0,-1);
        assertEquals(0,regular.arrivalPenalty(s,step,s.mobs()));
        assertEquals(PureCombatPolicy.penalty(s,step,s.mobs()),pure.arrivalPenalty(s,step,s.mobs()));
        assertTrue(regular.routeAllowed(s,step));
        assertEquals(PureSafety.routeAllowed(s,step),pure.routeAllowed(s,step));
        assertEquals(CaveSafety.lateAttackingRanger(s,ranger),regular.rangerFirst(s,ranger));
        assertEquals(PureCombatPolicy.rangerBeforeBat(s,ranger),pure.rangerFirst(s,ranger));
    }
    @Test void plannerMeleeKitingGuardIsRegularOnly() {
        Snapshot adjacent=scene(mob(1,Kind.MELEER,35,26));
        assertTrue(new CombatPlanner().avoidsMeleeKiting(adjacent));
        assertFalse(new PureCombatPlanner().avoidsMeleeKiting(adjacent));
        Snapshot jad=scene(mob(1,Kind.MELEER,35,26),mob(2,Kind.JAD,50,50));
        assertFalse(new CombatPlanner().avoidsMeleeKiting(jad));
    }
    @Test void lureTargetingHooksUsePureCombatPolicy() {
        LureController regular=new LureController();PureLureController pure=new PureLureController();
        Snapshot s=exposedRelease;
        assertTrue(regular.canShoot(s,ranger));
        assertFalse(pure.canShoot(s,ranger));
        assertFalse(pure.canShoot(s,null));
        assertEquals(CaveSafety.rangedTarget(s),regular.rangedTarget(s));
        assertEquals(PureCombatPolicy.rangedTarget(s),pure.rangedTarget(s));
        assertEquals(CaveSafety.preferredShot(s,Protection.RANGE),regular.preferredShot(s,Protection.RANGE));
        assertEquals(PureCombatPolicy.preferredShot(s,Protection.RANGE),pure.preferredShot(s,Protection.RANGE));
        for(Mob m:s.mobs()) {
            assertEquals(CaveSafety.targetPriority(s,m,Protection.MAGIC),regular.targetPriority(s,m,Protection.MAGIC));
            assertEquals(PureCombatPolicy.targetPriority(s,m,Protection.MAGIC),pure.targetPriority(s,m,Protection.MAGIC));
            assertEquals(CaveSafety.lateAttackingRanger(s,m),regular.rangerBeforeBat(s,m));
            assertEquals(PureCombatPolicy.rangerBeforeBat(s,m),pure.rangerBeforeBat(s,m));
        }
    }
    @Test void lureExcursionHooksAreInertForRegularController() {
        LureController regular=new LureController();Snapshot s=exposedRelease;
        assertFalse(regular.excursionActive());
        assertNull(regular.excursionPlan(s));
        assertNull(regular.retainEstablishedTrap(s,s.player()));
        assertNull(regular.retainedPosition(s));
        assertNull(regular.contactShot(s));
        assertNull(regular.beforeRangerFirst(s,s.player(),s.player().add(8,17)));
        PureLureController pure=new PureLureController();
        assertFalse(pure.excursionActive());
        Snapshot contact=scene(mob(4,Kind.BAT,35,26));
        Plan tiny=pure.contactShot(contact);
        assertNotNull(tiny);assertEquals(4,tiny.targetIndex());
        assertEquals("Pure: clear contacting tiny monster without leaving cover",tiny.reason());
    }
}
