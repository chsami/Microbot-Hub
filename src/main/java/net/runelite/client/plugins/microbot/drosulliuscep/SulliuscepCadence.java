/*
 * DroSulliuscep - Copyright (c) 2026, Dorian (DRO / droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by Dorian (DRO / droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import java.util.Random;

/** Event-based cadence: polling and urgent work never reset the pending pause. */
final class SulliuscepCadence
{
    private final Random random;
    private long nextPauseAt;

    SulliuscepCadence(Random random, long now)
    {
        this.random = random;
        pauseCompleted(now);
    }

    boolean pauseDue(long now, boolean urgent)
    {
        return !urgent && now >= nextPauseAt;
    }

    void pauseCompleted(long now)
    {
        nextPauseAt = now + 3_200 + random.nextInt(56_801);
    }

    int afterActionDelay()
    {
        return random.nextInt(10) == 0 ? 20 + random.nextInt(261) : 0;
    }

    boolean mouseDistraction()
    {
        return random.nextInt(100) < 5;
    }
}
