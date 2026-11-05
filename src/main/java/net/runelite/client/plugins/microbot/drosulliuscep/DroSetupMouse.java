/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import java.awt.Rectangle;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.runelite.api.Point;

/**
 * Hub build stub. The droplugins Microbot client routes Inventory Setup mouse movement through the
 * registered Dro profile; the released Microbot client has no such hook, so this does nothing.
 */
final class DroSetupMouse
{
    private DroSetupMouse()
    {
    }

    static void register(Object owner, Runnable before, Runnable after,
            Function<Rectangle, Point> point, Runnable idle, BooleanSupplier allowed)
    {
    }

    static void unregister(Object owner)
    {
    }
}
