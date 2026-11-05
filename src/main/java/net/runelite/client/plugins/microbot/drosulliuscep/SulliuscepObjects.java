/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import java.util.function.Predicate;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;

/** Scene object lookups and clicks through Microbot's tile object cache. */
final class SulliuscepObjects
{
    private SulliuscepObjects()
    {
    }

    /** Nearest game object matching {@code filter} within {@code distance} tiles of the player. */
    static TileObject nearest(Predicate<TileObject> filter, int distance)
    {
        return Microbot.getRs2TileObjectCache().query()
                .ofType(TileObjectType.GAME)
                .where(filter::test)
                .nearestOnClientThread(distance);
    }

    /** Nearest game object with this id anywhere in the loaded scene. */
    static TileObject nearest(int id)
    {
        return Microbot.getRs2TileObjectCache().query()
                .ofType(TileObjectType.GAME)
                .withId(id)
                .nearestOnClientThread();
    }

    /** Nearest game object whose name contains {@code name} within {@code distance} tiles. */
    static TileObject nearestNamed(String name, int distance)
    {
        return Microbot.getRs2TileObjectCache().query()
                .ofType(TileObjectType.GAME)
                .withNameContains(name)
                .nearestOnClientThread(distance);
    }

    /** The live scene object behind a cache model. */
    static TileObject unwrap(TileObject object)
    {
        return object instanceof Rs2TileObjectModel ? ((Rs2TileObjectModel) object).getTileObject() : object;
    }

    static boolean interact(TileObject object, String action)
    {
        return object != null && new Rs2TileObjectModel(object).click(action);
    }
}
