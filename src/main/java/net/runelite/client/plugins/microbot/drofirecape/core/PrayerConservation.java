/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
/** Optional demand policy only. The prayer clocks and atomic reset transport are unchanged. */
public final class PrayerConservation {
    private PrayerConservation(){}
    public static boolean offence(boolean configured,boolean conservation,int targetIndex,Snapshot scene) {
        if(!configured)return false;
        if(!conservation)return true;
        if(scene==null)return false;
        Mob target=scene.mobs().stream().filter(m->m.index()==targetIndex).findFirst().orElse(null);
        if(target==null||!CombatPlanner.playerCanAttack(scene,scene.player(),target))return false;
        if(target.kind()==Kind.JAD)return true;
        return (target.kind()==Kind.RANGER||target.kind()==Kind.MELEER)
            &&(CaveSafety.active(scene,target)||!CaveSafety.trapped(scene,target));
    }
    public static boolean earlyGap(boolean enabled,int nextWave,Snapshot scene) {
        return enabled&&nextWave>=0&&nextWave<=30&&scene!=null&&scene.mobs().isEmpty();
    }
    /** Pure pauses are not mandatory every configured wave. Emergency healing,
     * active input deadlines and the existing pause safety gates still run first. */
    public static boolean recoveryNeeded(int hp,int maximum) {
        return maximum>0&&hp>0&&(long)hp*100<=maximum*70L;
    }
    /** Distance-only admission, not a claim that a nearby shooter is trapped.
     * Reserve four tiles for player/NPC approach; a checked long route can still
     * explicitly pre-arm its required prayer after this idle-hold decision. */
    public static boolean remote(Snapshot s) {
        if(s==null||s.meleeMode())return false;
        for(Mob m:s.mobs())if(m.kind()==Kind.MAGER||m.kind()==Kind.JAD
            ||m.distance(s.player())<=m.kind().range+4)return false;
        return true;
    }
}
