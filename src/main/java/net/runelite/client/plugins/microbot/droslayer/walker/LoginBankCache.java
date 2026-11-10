package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.Map;
import java.util.function.Supplier;

/** Immutable bank contents retained only while the observed login's live bank epoch is valid. */
final class LoginBankCache
{
    private int loginFloor;
    private int cachedEpoch = -1;
    private Map<Integer, Integer> counts = Map.of();

    synchronized boolean isKnown(int epoch)
    {
        if (epoch <= 0 || epoch < loginFloor)
        {
            loginFloor = 0;
            cachedEpoch = -1;
            counts = Map.of();
        }
        return epoch > loginFloor;
    }

    synchronized Map<Integer, Integer> counts(int epoch, Supplier<Map<Integer, Integer>> readLiveMirror)
    {
        if (!isKnown(epoch)) return Map.of();
        if (cachedEpoch != epoch)
        {
            counts = Map.copyOf(readLiveMirror.get());
            cachedEpoch = epoch;
        }
        return counts;
    }

    synchronized void reset(int epoch)
    {
        loginFloor = Math.max(0, epoch);
        cachedEpoch = -1;
        counts = Map.of();
    }
}
