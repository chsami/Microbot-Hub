/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.StringJoiner;
import net.runelite.client.plugins.microbot.drofirecape.core.AttackClock;
import net.runelite.client.plugins.microbot.drofirecape.core.WaveBook;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Former overlay diagnostics, formatted from cached evidence on the controller worker. */
final class FcDiagnostics {
    private FcDiagnostics() { }

    static String describe(DroFirecapeScript script) {
        StringBuilder text=new StringBuilder(1024);
        text.append("state=").append(script.state()).append(" status=").append(script.status())
            .append(" runtimeMs=").append(script.runtime()).append(" phase=").append(script.combatPhase())
            .append(" wave=").append(script.wave()).append("/63 rotation=").append(script.rotation())
            .append(" rotationStatus=").append(script.rotationStatus())
            .append(" mode=").append(script.combatModeName()).append(" offence=").append(script.offensivePrayerName())
            .append(" planner=").append(script.plannerMode()).append(" warning=").append(script.warning());
        FcFrame frame=script.frame();Plan plan=script.plan();
        if(frame==null)return text.append(" scene=unavailable").toString();
        text.append(" world=").append(frame.world).append(" position=").append(frame.model.player())
            .append(" hp=").append(frame.hp).append('/').append(frame.maxHp)
            .append(" prayer=").append(frame.prayer).append('/').append(frame.maxPrayer)
            .append(" energy=").append(frame.energyPercent()).append("% recovery=").append(script.pauseStatus())
            .append(" overhead=").append(frame.overhead).append(" requested=")
            .append(frame.cave?script.requestedProtection():plan==null?"-":plan.protection())
            .append(" weaponRange=").append(frame.model.weaponRange()).append(" damage=").append(script.damage());
        if(!frame.cave)text.append(" predictorCurrentDesired=").append(script.entryRotationText())
            .append(" predictorClock=").append(script.entryClockText()).append(" gate=").append(script.entryGateStatus());
        if(plan!=null)text.append(" cover=").append(plan.destination()).append(" step=").append(plan.nextStep())
            .append(" target=").append(plan.targetIndex()).append(" blocked=").append(plan.blockedMobs())
            .append(" exposedStyles=").append(plan.exposedStyles()).append(" safe=").append(plan.safe())
            .append(" risk=").append(plan.risk()).append(" reason=").append(plan.reason());
        if(script.wave()==63)text.append(" jadPrayer=").append(script.jadStyle()).append(" healers=")
            .append(frame.model.mobs().stream().filter(m->m.kind()==Kind.HEALER).count());
        if(script.wave()>0&&script.wave()<63)text.append(" nextWave=")
            .append(script.predictionReady()?WaveBook.describe(script.rotation(),script.wave()+1):"Prediction suspended; live combat active");
        if(frame.cave) {
            text.append(" prayerTiming=").append(script.prayerTickStatus()).append(" attackCadence=").append(script.bowCadence());
            StringJoiner clocks=new StringJoiner(" | ");
            for(Mob mob:frame.model.mobs())if(mob.kind()==Kind.MAGER||mob.kind()==Kind.RANGER||mob.kind()==Kind.MELEER) {
                int remaining=AttackClock.remaining(mob,frame.tick);
                clocks.add(mob.kind()+":"+(remaining<0?"?":remaining));
            }
            text.append(" attackClocks=").append(clocks);
        }
        return text.toString();
    }
}
