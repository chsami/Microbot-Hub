package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import java.util.Set;

/** Known Colosseum enemies. */
final class ColosseumTables
{
    final Set<Integer> observedEnemies = Set.of(12810, 12811, 12812, 12814, 12815, 12816, 12817, 12818, 12819, 12821, 12824, 12826);
    String failure;

    static ColosseumTables load() { return new ColosseumTables(); }
}
