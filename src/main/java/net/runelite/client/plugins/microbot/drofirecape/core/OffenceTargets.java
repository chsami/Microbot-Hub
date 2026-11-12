/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/**
 * Which shots get an offensive prayer, the Inferno controller's rule: only the big melee, ranger, mager, Jad and
 * healers, never bats or blobs. A mager is boosted only in a fight it has to itself: not while another monster
 * attacked the player within two of that monster's attack cycles.
 */
public final class OffenceTargets {
    private OffenceTargets(){}
    public static boolean worthwhile(Kind kind) {
        return kind==Kind.MELEER||kind==Kind.RANGER||kind==Kind.MAGER||kind==Kind.JAD||kind==Kind.HEALER;
    }
    public static boolean offence(int targetIndex,Snapshot scene) {
        if(scene==null)return false;
        Mob target=null;
        for(Mob m:scene.mobs())if(m.index()==targetIndex){target=m;break;}
        if(target==null||!worthwhile(target.kind())||!CombatPlanner.playerCanAttack(scene,scene.player(),target))return false;
        return target.kind()!=Kind.MAGER||!otherAttacker(scene,target);
    }
    static boolean otherAttacker(Snapshot scene,Mob shot) {
        for(Mob m:scene.mobs())if(m.index()!=shot.index()&&m.lastAttackTick()>=0
            &&scene.tick()-m.lastAttackTick()<=2L*m.kind().speed)return true;
        return false;
    }
}
