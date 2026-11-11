/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.Microbot;

/** Optional local-client canvas clamp; released clients use the visible input bounds. */
final class FcCanvasGuard implements AutoCloseable {
    private final AutoCloseable nativeLease;
    private boolean closed;
    private FcCanvasGuard(AutoCloseable nativeLease){this.nativeLease=nativeLease;}
    static FcCanvasGuard acquire(){return acquire(Microbot.class.getClassLoader());}
    static FcCanvasGuard acquire(ClassLoader loader) {
        try {
            Class<?> guard=Class.forName("net.runelite.client.plugins.microbot.util.input.CanvasPointerLease",true,loader);
            return new FcCanvasGuard((AutoCloseable)guard.getMethod("acquire").invoke(null));
        }catch(ClassNotFoundException absent) {
            // BaseProfileDro still disables off-screen parking; every plugin
            // prayer/widget click already requires visible canvas bounds.
            return new FcCanvasGuard(null);
        }catch(ReflectiveOperationException|ClassCastException failure) {
            throw new IllegalStateException("Cannot acquire the client's canvas guard",failure);
        }
    }
    boolean nativeGuard(){return nativeLease!=null;}
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        if(nativeLease!=null)try{nativeLease.close();}
        catch(Exception failure){Microbot.log("[Dro Firecape] Canvas guard release failed: "+failure.getClass().getSimpleName());}
    }
}
