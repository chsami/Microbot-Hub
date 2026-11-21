package net.runelite.client.plugins.microbot.drorecorder;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Tracks recording identities without retaining live entities across ticks. Client-thread only. */
final class DroRecorderIdentityMap<T>
{
    private final ReferenceQueue<T> collected = new ReferenceQueue<>();
    private final Map<Key<T>, Long> identities = new HashMap<>();

    long computeIfAbsent(T entity, Function<T, Long> create)
    {
        reap();
        Key<T> lookup = new Key<>(entity, null);
        Long id = identities.get(lookup);
        if (id != null) { return id; }
        long next = create.apply(entity);
        identities.put(new Key<>(entity, collected), next);
        return next;
    }

    void remove(T entity)
    {
        reap();
        identities.remove(new Key<>(entity, null));
    }

    void removeIf(Predicate<T> expired)
    {
        reap();
        identities.keySet().removeIf(key ->
        {
            T entity = key.get();
            return entity == null || expired.test(entity);
        });
    }

    void clear()
    {
        identities.clear();
        reap();
    }

    private void reap()
    {
        java.lang.ref.Reference<? extends T> reference;
        while ((reference = collected.poll()) != null) { identities.remove(reference); }
    }

    private static final class Key<T> extends WeakReference<T>
    {
        private final int identityHash;

        Key(T referent, ReferenceQueue<T> queue)
        {
            super(referent, queue);
            identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() { return identityHash; }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) { return true; }
            if (!(other instanceof Key)) { return false; }
            Object entity = get();
            return entity != null && entity == ((Key<?>) other).get();
        }
    }
}
