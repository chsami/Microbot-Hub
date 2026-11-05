/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Hub build base. Supplies the two Script helpers that only the droplugins Microbot client has,
 * so DroSulliuscepScript stays unchanged.
 */
abstract class DroScript extends Script
{
    /** The droplugins client logs a per-action trace here; the Hub build does not. */
    protected final void traceDroAction(String method)
    {
    }

    /** Keep run enabled while the script owns input. */
    protected final void maintainRunEnergy()
    {
        if (Thread.currentThread().isInterrupted() || Microbot.pauseAllScripts.get()
                || InputArbiter.isHuman() || !Microbot.isLoggedIn()) return;
        Rs2Player.toggleRunEnergy(true);
    }
}
