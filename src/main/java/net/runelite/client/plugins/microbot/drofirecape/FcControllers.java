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

/** Selects one owner at startup; never runs two movement or prayer writers. */
@Singleton
final class FcControllers {
    @Inject private DroFirecapeScript regular;
    @Inject private OptionalFirecapeScript optional;
    private boolean optionalSelected;
    static boolean selectsOptional(DroFirecapeConfig config) {
        return config.pureMode() || config.nativeTickPrayers();
    }
    void run(DroFirecapeConfig config) {
        optionalSelected=selectsOptional(config);
        if(optionalSelected)optional.run(config);else regular.run(config);
    }
    void shutdown(){if(optionalSelected)optional.shutdown();else regular.shutdown();}
    void onGameTick(){if(optionalSelected)optional.onGameTick();else regular.onGameTick();}
    void onClientTick(){if(optionalSelected)optional.onClientTick();else regular.onClientTick();}
    void onChatMessage(ChatMessage e){if(optionalSelected)optional.onChatMessage(e);else regular.onChatMessage(e);}
    void onNpcSpawned(NpcSpawned e){if(optionalSelected)optional.onNpcSpawned(e);else regular.onNpcSpawned(e);}
    void onNpcDespawned(NpcDespawned e){if(optionalSelected)optional.onNpcDespawned(e);else regular.onNpcDespawned(e);}
    void onAnimationChanged(AnimationChanged e){if(optionalSelected)optional.onAnimationChanged(e);else regular.onAnimationChanged(e);}
    void onActorDeath(ActorDeath e){if(optionalSelected)optional.onActorDeath(e);else regular.onActorDeath(e);}
    void onHitsplatApplied(HitsplatApplied e){if(optionalSelected)optional.onHitsplatApplied(e);else regular.onHitsplatApplied(e);}
    void onGameStateChanged(GameStateChanged e){if(optionalSelected)optional.onGameStateChanged(e);else regular.onGameStateChanged(e);}
    String rotationWaitEstimate(){
        String status=optionalSelected?optional.entryGateStatus():regular.entryGateStatus();
        int start=status.indexOf("~"),end=status.lastIndexOf(")");
        return start<0||end<start?"":status.substring(start,end);
    }
    String state(){return optionalSelected?optional.state().name():regular.state().name();}
    long runtime(){return optionalSelected?optional.runtime():regular.runtime();}
    int wave(){return optionalSelected?optional.wave():regular.wave();}
    String monsterCount(){
        if(optionalSelected){return optional.frame()==null?"-":String.valueOf(optional.frame().monsterCount());}
        FcFrame f=regular.frame();return f!=null&&f.cave?String.valueOf(f.model.mobs().size()):"-";
    }
}
