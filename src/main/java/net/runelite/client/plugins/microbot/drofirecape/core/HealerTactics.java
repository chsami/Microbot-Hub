/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Reach an untagged healer while retaining every live NPC in the safety model. */
public final class HealerTactics {
    private HealerTactics(){}

    public static Plan tag(Snapshot scene,boolean strict) {
        List<Mob> remaining=new ArrayList<>();
        for(Mob mob:scene.mobs())if(mob.kind()==Kind.HEALER&&!mob.attackingPlayer())remaining.add(mob);
        remaining.sort(Comparator.comparingInt((Mob mob)->mob.distance(scene.player())).thenComparingInt(Mob::index));
        if(remaining.isEmpty())return null;
        CombatPlanner planner=new CombatPlanner();
        Protection protection=CombatPlanner.protectionForNextTick(scene,scene.player());
        for(Mob healer:remaining)if(planner.attackAllowed(scene,healer,protection,strict)) {
            int risk=CombatPlanner.immediateExposure(scene,scene.player(),protection);
            return new Plan(scene.player(),scene.player(),protection,healer.index(),risk==0,0,0,risk,"Tag remaining healer");
        }
        // A healer behind Jad or cover needs a checked firing approach, not the
        // ordinary planner's preferred kill on an already-tagged healer.
        CollisionGrid.PathTree paths=scene.grid().pathsFrom(scene.player(),scene.mobs(),
            tile->TacticalMovement.clearOfJad(scene,scene.mobs(),tile));
        ArrayList<Tile> stands=new ArrayList<>();
        for(int x=1;x<scene.grid().width-1;x++)for(int y=1;y<scene.grid().height-1;y++) {
            Tile stand=new Tile(x,y);
            if(stand.equals(scene.player())||stand.distance(scene.player())>48||!scene.grid().open(stand)
                ||!TacticalMovement.clearOfJad(scene,scene.mobs(),stand))continue;
            for(Mob healer:remaining)if(tagRange(scene,stand,healer)) {
                if(!paths.to(stand).isEmpty())stands.add(stand);
                break;
            }
        }
        stands.sort(Comparator.comparingInt((Tile tile)->tile.distance(scene.player()))
            .thenComparingInt(Tile::x).thenComparingInt(Tile::y));
        for(int i=0;i<Math.min(100,stands.size());i++) {
            Tile stand=stands.get(i);Plan route=planner.route(scene,stand);
            if(!CombatPlanner.actionable(route,strict))continue;
            for(Mob healer:remaining)if(tagRange(scene,stand,healer))
                return new Plan(route.destination(),route.nextStep(),route.protection(),healer.index(),
                    route.safe(),route.blockedMobs(),route.exposedStyles(),route.risk(),"Approach remaining healer tag");
        }
        return null;
    }
    private static boolean tagRange(Snapshot scene,Tile stand,Mob healer) {
        return (scene.meleeMode()||healer.distance(stand)>=3)&&CombatPlanner.playerCanAttack(scene,stand,healer);
    }
}
