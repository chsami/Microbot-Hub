/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.*;

/** The pure controller is the only owner of cave movement and prayers. */
@Singleton
final class FcControllers {
    @Inject private OptionalFirecapeScript script;
    void run(DroFirecapeConfig config){script.run(config);}
    void shutdown(){script.shutdown();}
    FcFrame frame(){return script.frame();}
    void onGameTick(){script.onGameTick();}
    void onClientTick(){script.onClientTick();}
    void onChatMessage(ChatMessage e){script.onChatMessage(e);}
    void onNpcSpawned(NpcSpawned e){script.onNpcSpawned(e);}
    void onNpcDespawned(NpcDespawned e){script.onNpcDespawned(e);}
    void onAnimationChanged(AnimationChanged e){script.onAnimationChanged(e);}
    void onActorDeath(ActorDeath e){script.onActorDeath(e);}
    void onHitsplatApplied(HitsplatApplied e){script.onHitsplatApplied(e);}
    void onProjectileMoved(ProjectileMoved e){script.onProjectileMoved(e);}
    void onGameStateChanged(GameStateChanged e){script.onGameStateChanged(e);}
    String rotationWaitEstimate(){
        String status=script.entryGateStatus();
        int start=status.indexOf("~"),end=status.lastIndexOf(")");
        return start<0||end<start?"":status.substring(start,end);
    }
    String state(){return script.state().name();}
    long runtime(){return script.runtime();}
    int wave(){return script.wave();}
    String monsterCount(){return script.frame()==null?"-":String.valueOf(script.frame().monsterCount());}
}
