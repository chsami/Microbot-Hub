/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

/**
 * Public-client guard lifecycle. The released client uses the existing visible
 * prayer/widget bounds and BaseProfileDro's disabled off-screen parking.
 * This adapter deliberately needs no private client extension.
 */
final class FcCanvasGuard implements AutoCloseable {
    private FcCanvasGuard() {}
    static FcCanvasGuard acquire(){return new FcCanvasGuard();}
    static FcCanvasGuard acquire(ClassLoader loader){return acquire();}
    boolean nativeGuard(){return false;}
    @Override public void close() {}
}
