package net.runelite.client.plugins.microbot.combathotkeys;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Keeps a cancelled or completed request from releasing a newer hotkey request. */
final class PendingHotkeyAction {
    private final AtomicReference<Object> pending = new AtomicReference<>();

    boolean submit(Predicate<Runnable> dispatcher, Runnable action) {
        Object token = new Object();
        if (!pending.compareAndSet(null, token)) {
            return false;
        }
        try {
            if (dispatcher.test(() -> {
                try {
                    if (pending.get() == token) {
                        action.run();
                    }
                } finally {
                    pending.compareAndSet(token, null);
                }
            })) {
                return true;
            }
        } catch (RejectedExecutionException ignored) {
            // The executor can shut down between the availability check and submission.
        }
        pending.compareAndSet(token, null);
        return false;
    }

    void cancel() {
        pending.set(null);
    }
}
