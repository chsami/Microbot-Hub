/*
 * DroSulliuscep - Copyright (c) 2026, Dorian (DRO / droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by Dorian (DRO / droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

/** A targeting NPC may already be trapped. Require a new hit after giving it time to settle. */
final class ThirdTreeAttackWatch
{
    static final long OBSERVATION_MS = 8_000L;
    private static final long FRESH_HIT_MS = 1_500L;
    private long startedAt = -1L;
    private long lastHitAt = -1L;

    synchronized void recordHit(long now)
    {
        lastHitAt = now;
    }

    synchronized boolean shouldRecover(boolean eligible, long now)
    {
        if (!eligible)
        {
            reset();
            return false;
        }
        if (startedAt < 0L) startedAt = now;
        return lastHitAt >= startedAt + OBSERVATION_MS
                && now >= lastHitAt && now - lastHitAt <= FRESH_HIT_MS;
    }

    synchronized void reset()
    {
        startedAt = -1L;
        lastHitAt = -1L;
    }
}
