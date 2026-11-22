/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.drofirecape.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptionalScriptHookTest {
    static class Config implements DroFirecapeConfig {
        boolean pure=true;
        @Override public boolean pureMode(){return pure;}
        @Override public boolean prayerConservation(){return true;}
        @Override public boolean observeOnly(){return true;}
        @Override public boolean strictZeroExposure(){return true;}
        @Override public int weaponRange(){return 7;}
        @Override public boolean rangingPotion(){return false;}
        @Override public boolean demonstrationLures(){return false;}
        @Override public boolean blowpipeSpecial(){return false;}
        @Override public boolean useThralls(){return false;}
        @Override public boolean exitOnDamage(){return true;}
        @Override public boolean recordRun(){return false;}
    }
    private static <T extends DroFirecapeScript>T configured(T script,Config config)throws Exception {
        FirecapeTestClient.set(script,"config",config);return script;
    }

    @Test void regularControllerReadsEveryOptionFromConfig()throws Exception {
        DroFirecapeScript s=configured(new DroFirecapeScript(),new Config());
        assertTrue(s.observeOnly());assertTrue(s.strictZeroExposure());assertEquals(7,s.weaponRangeSetting());
        assertFalse(s.rangingPotion());assertFalse(s.demonstrationLures());assertFalse(s.blowpipeSpecial());
        assertFalse(s.useThralls());assertTrue(s.exitOnDamage());assertFalse(s.traceEnabled());assertEquals("",s.startupDetail());
        assertFalse(s.usesRecordedWave(1,10));
        assertFalse(s.keepsFightingWhenDepleted());assertTrue(s.batFirstAllowed());assertTrue(s.sweetsWaitForLureReturn());
        assertTrue(s.cameraNeedsOffscreenTarget());assertFalse(s.primesWithoutOffence());
        assertEquals("preparing first-wave melee protection",s.entryProtectionStatus());
        FcFrame frame=FirecapeTestClient.frame(10,3024);
        assertFalse(s.earlyConservationGap(frame));
        assertSame(s.planner,s.attackPlanner(frame));
        assertSame(DroFirecapeScript.MoveOwner.DEFAULT,s.moveOwner(frame,null));
    }
    @Test void optionalControllerKeepsItsFixedPolicyAndPureOwnership()throws Exception {
        Config config=new Config();
        OptionalFirecapeScript s=configured(new OptionalFirecapeScript(),config);
        assertFalse(s.observeOnly());assertFalse(s.strictZeroExposure());assertEquals(0,s.weaponRangeSetting());
        assertTrue(s.rangingPotion());assertTrue(s.demonstrationLures());assertTrue(s.blowpipeSpecial());
        assertTrue(s.useThralls());assertFalse(s.exitOnDamage());assertFalse(s.traceEnabled());
        assertEquals("; Record run=false (collision maps saved after cave decisions)",s.startupDetail());
        assertTrue(s.usesRecordedWave(1,10));
        assertTrue(s.keepsFightingWhenDepleted());assertFalse(s.batFirstAllowed());assertFalse(s.sweetsWaitForLureReturn());
        assertFalse(s.cameraNeedsOffscreenTarget());assertTrue(s.primesWithoutOffence());
        assertEquals("confirming the configured entry prayer state",s.entryProtectionStatus());
        FcFrame frame=FirecapeTestClient.frame(10,3024);
        assertTrue(s.attackPlanner(frame) instanceof PureCombatPlanner);
        assertNotSame(s.planner,s.attackPlanner(frame));
        config.pure=false;
        assertSame(s.planner,s.attackPlanner(frame));
        assertTrue(s.batFirstAllowed());assertTrue(s.sweetsWaitForLureReturn());assertFalse(s.keepsFightingWhenDepleted());
    }
}
