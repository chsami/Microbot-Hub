/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.awt.Rectangle;
import java.awt.Shape;

/** A partially visible model or tile is NOT a reason to turn the camera. */
public final class NpcVisibility {
    private NpcVisibility() { }
    public static boolean completelyOutside(Shape hull,Shape tile,Rectangle viewport) {
        if(viewport==null||viewport.isEmpty())return false; // Unknown viewport: do not pivot.
        if(hull!=null&&hull.intersects(viewport))return false;
        if(tile!=null&&tile.intersects(viewport))return false;
        return true;
    }
}
