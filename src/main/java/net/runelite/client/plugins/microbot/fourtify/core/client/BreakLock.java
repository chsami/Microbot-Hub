package net.runelite.client.plugins.microbot.fourtify.core.client;

import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;

/**
 * Holds Microbot's break-handler lock while a task says a break is unsafe. Both break handlers (v1 and
 * v2) respect {@link BreakHandlerScript#isLockState()}. Only changes are sent, and {@link #release()}
 * drops the lock only if this runner took it, so another script's lock is left alone.
 */
class BreakLock {
    private boolean held;

    void hold(boolean lock) {
        if (lock != held) {
            BreakHandlerScript.setLockState(lock);
            held = lock;
        }
    }

    void release() {
        hold(false);
    }

    boolean isHeld() {
        return held;
    }
}
