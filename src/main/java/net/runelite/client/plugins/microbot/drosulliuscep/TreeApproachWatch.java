/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.api.coords.WorldPoint;

/** Let an accepted Cut finish its observed walk, without hiding a stalled approach indefinitely. */
final class TreeApproachWatch
{
    private static final long MAX_APPROACH_MS = 30_000L;
    private static final long ARRIVAL_SETTLE_MS = 1_800L;
    private long issuedAt;
    private WorldPoint lastPosition;

    void issued(long now, WorldPoint position)
    {
        issuedAt = now;
        lastPosition = position;
    }

    long deadline(long original, long now, WorldPoint position)
    {
        boolean progressed = position != null && lastPosition != null
                && position.getPlane() == lastPosition.getPlane() && !position.equals(lastPosition);
        if (position != null) lastPosition = position;
        if (!progressed || now >= issuedAt + MAX_APPROACH_MS) return original;
        return Math.min(issuedAt + MAX_APPROACH_MS, Math.max(original, now + ARRIVAL_SETTLE_MS));
    }
}
