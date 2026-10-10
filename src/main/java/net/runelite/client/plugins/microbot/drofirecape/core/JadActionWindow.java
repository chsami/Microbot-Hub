/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/** Prayer-first scheduling for optional tab/healer actions, not a prayer predictor. */
public final class JadActionWindow {
    private int consumedAttack=-1000;
    public void reset(){consumedAttack=-1000;}
    public boolean available(int tick,int lastAttack,boolean overheadConfirmed) {
        int age=tick-lastAttack;
        return overheadConfirmed && lastAttack>=0 && age>=1 && age<=4 && consumedAttack!=lastAttack;
    }
    public void consumed(int lastAttack){consumedAttack=lastAttack;}
}
