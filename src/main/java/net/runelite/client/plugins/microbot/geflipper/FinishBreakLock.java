package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;

/**
 * Defers Break Handler breaks while Finish works, through the lock both Break Handlers already honour, so a break
 * cannot log out halfway through listing. Only a lock GE Flipper took is released, and it is released when
 * GE Flipper stops. Break Handler itself is never switched off or reconfigured.
 */
final class FinishBreakLock {
    interface Lock {
        boolean locked();
        void set(boolean locked);
    }

    static final Lock BREAK_HANDLER = new Lock() {
        @Override public boolean locked() { return BreakHandlerScript.isLockState(); }
        @Override public void set(boolean locked) { BreakHandlerScript.setLockState(locked); }
    };

    private final Lock lock;
    private boolean owned;

    FinishBreakLock() {
        this(BREAK_HANDLER);
    }

    FinishBreakLock(Lock lock) {
        this.lock = lock;
    }

    /** Locks breaks unless something else already holds the lock. */
    synchronized void acquire() {
        if (owned) return;
        try {
            if (lock.locked()) return;
            lock.set(true);
            owned = true;
        } catch (RuntimeException | LinkageError unavailable) {
            // Without the lock Finish still works; a break may simply pause it.
        }
    }

    /** Puts the lock back only if GE Flipper took it. */
    synchronized void release() {
        if (!owned) return;
        owned = false;
        try {
            lock.set(false);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    synchronized boolean owned() {
        return owned;
    }
}
