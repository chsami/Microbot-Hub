/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

/**
 * Hub build stub. The droplugins Microbot client applies this per-session motion variation inside
 * its mouse movement; the released Microbot client has no such hook, so these scopes do nothing.
 */
final class MouseMotionVariation
{
    private MouseMotionVariation()
    {
    }

    static Scope open(Session session)
    {
        return new Scope();
    }

    static final class Scope implements AutoCloseable
    {
        @Override
        public void close()
        {
        }
    }

    static final class Session implements AutoCloseable
    {
        Scope open()
        {
            return new Scope();
        }

        @Override
        public void close()
        {
        }
    }
}
