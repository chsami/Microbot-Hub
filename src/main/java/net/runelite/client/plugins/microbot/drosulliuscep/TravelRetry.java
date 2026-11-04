/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

/** An issued input owns its stage until observation succeeds or its deadline expires. */
final class TravelRetry
{
    private int attempts;
    private long deadline;
    private boolean menuSelected;

    boolean waiting(long now) { return attempts > 0 && now < deadline; }
    boolean exhausted(long now) { return attempts >= 3 && !waiting(now); }
    boolean menuAllowed(long now) { return waiting(now) && !menuSelected; }
    void menuIssued() { menuSelected = true; }
    void issued(long now, long timeout) { attempts++; deadline = now + timeout; menuSelected = false; }
    void reset() { attempts = 0; deadline = 0L; menuSelected = false; }
}
