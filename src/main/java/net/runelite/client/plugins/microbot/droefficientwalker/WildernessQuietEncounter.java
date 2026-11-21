package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.HashSet;
import java.util.Set;

/** Releases failed escape waits, while suppressing only the players already watched. */
final class WildernessQuietEncounter
{
    private final Set<Integer> watched = new HashSet<>();
    private final Set<Integer> suppressed = new HashSet<>();
    private boolean active;
    private int lastActivityTick;

    boolean observe(WildernessThreatPolicy.Snapshot threat, int tick)
    {
        if (active && tick < lastActivityTick) reset();
        suppressed.retainAll(threat.threatPlayers());
        suppressed.removeAll(threat.attackingPlayers());
        if (!active)
        {
            boolean fresh = threat.threatPlayers().stream().anyMatch(id -> !suppressed.contains(id));
            if (!fresh && !threat.hasAttackAction()) return false;
            active = true;
            lastActivityTick = tick;
        }
        watched.retainAll(threat.threatPlayers());
        if (watched.addAll(threat.threatPlayers()) || threat.hasAttackAction()) lastActivityTick = tick;
        return true;
    }

    boolean isQuiet(int tick)
    {
        return active && tick - lastActivityTick >= WildernessPvpActivity.QUIET_TICKS;
    }

    void release(WildernessThreatPolicy.Snapshot threat)
    {
        suppressed.addAll(threat.threatPlayers());
        watched.clear();
        active = false;
        lastActivityTick = 0;
    }

    void reset()
    {
        watched.clear();
        suppressed.clear();
        active = false;
        lastActivityTick = 0;
    }
}
