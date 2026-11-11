/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Pure-only wave-gap check against the next wave's predicted spawns. The
 * predicted snapshot carries the spawn footprints; its player is the tile judged.
 * A wave appears seconds after the last kill, so a pause chosen against an empty
 * cave can stand beside a Ket-Zek or Tok-Xil spawn. */
public final class SpawnTransition {
    private SpawnTransition() { }
    /** One overhead covers every predicted spawn threat at this tile for an attack cycle. */
    public static boolean pauseSafe(Snapshot predicted) {
        return predicted==null||PureSafety.stationaryCost(predicted)==0;
    }
}
