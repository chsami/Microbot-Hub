/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/**
 * Lazy offensive cycling from recognized, observed player attack animations.
 * Never infer an NPC protection deadline from the player's weapon clock. Two equal
 * consecutive intervals are required. Unknown/interrupted cadence holds offensive
 * prayer while engaging; unknown animations never establish a cycle.
 */
public final class BowPrayerClock {
    private int weapon=-1,style=-1,last=-1,interval=-1,matches;
    public synchronized void reset(){weapon=style=last=interval=-1;matches=0;}
    public synchronized void shot(int tick,int weaponId,int attackStyle,int animation) {
        if(animation!=426)return;
        observedAttack(tick,weaponId,attackStyle);
    }
    /** Recognized attack animation only; eating/casting/blocking never seeds cadence. */
    public synchronized void swing(int tick,int weaponId,int attackStyle,int animation,boolean melee) {
        if(!attackAnimation(animation,melee))return;
        observedAttack(tick,weaponId,attackStyle);
    }
    // chsami/Microbot 2.6.24 gameval/AnimationID.java: HUMAN_* attacks,
    // SLAYER_ABYSSAL_WHIP_ATTACK, XBOWS_HUMAN_FIRE_AND_RELOAD and SNAKEBOSS_BLOWPIPE_ATTACK.
    // Unlisted weapons deliberately hold offence rather than guessing their rhythm.
    public static boolean attackAnimation(int animation,boolean melee) {
        if(!melee)return animation==426||animation==427||animation==4230||animation==5061;
        switch(animation) {
            case 380:case 381:case 382:case 386:case 390:case 392:
            case 393:case 395:case 396:case 400:case 401:case 402:
            case 405:case 406:case 407:case 408:case 412:case 413:case 414:
            case 417:case 418:case 419:case 422:case 423:case 428:case 429:
            case 433:case 437:case 438:case 1658:return true;
            default:return false;
        }
    }
    private void observedAttack(int tick,int weaponId,int attackStyle) {
        if(weaponId!=weapon||attackStyle!=style){reset();weapon=weaponId;style=attackStyle;}
        if(tick==last)return;
        int delta=last<0?-1:tick-last;
        if(delta>=2&&delta<=8){matches=delta==interval?matches+1:1;interval=delta;}
        else {matches=0;interval=-1;}
        last=tick;
    }
    public synchronized void interrupt(){last=-1;interval=-1;matches=0;}
    public synchronized boolean learned(){return last>=0&&matches>=2;}
    public synchronized int period(){return learned()?interval:-1;}
    public synchronized int ticksUntilShot(int tick) {
        if(last<0||tick<last)return 0;
        int period=learned()?interval:2; // minimum recovery after an observed bow shot, not a guessed sustained rhythm
        return Math.max(0,last+period-tick);
    }
    public synchronized boolean wantOn(int tick,int weaponId,int attackStyle,boolean shooting,boolean flick) {
        if(!shooting)return false;
        if(!flick||weaponId!=weapon||attackStyle!=style||!learned())return true;
        // Keep the observed launch tick protected from a same-tick off/on race.
        if(tick<=last)return true;
        // Late/missing next shot: do not keep blindly cycling a stale clock.
        if(tick>=last+interval)return true;
        // Request ON at least one server-tick before the next expected shot.
        return tick+1>=last+interval;
    }
}
