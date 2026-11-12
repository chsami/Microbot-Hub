package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ColosseumTablesTest
{
    @Test public void knownEnemiesAndModifierOrderAreBuiltIn() {
        ColosseumTables tables = ColosseumTables.load();
        assertNull(tables.failure);
        assertTrue(tables.observedEnemies.contains(12810));
        assertTrue(tables.modifierRanks.get("blasphemy:1") > tables.modifierRanks.get("bees:1"));
    }
}
