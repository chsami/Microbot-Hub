package net.runelite.client.plugins.microbot.qualityoflife;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QoLRecordedEntriesTest {
    @AfterEach
    void clearRecordedEntries() {
        QoLPlugin.resetMenuEntries();
    }

    @Test
    void bankReplayToleratesRecordingChangesDuringIteration() {
        assertReplayToleratesChanges(QoLPlugin.bankMenuEntries);
    }

    @Test
    void furnaceReplayToleratesRecordingChangesDuringIteration() {
        assertReplayToleratesChanges(QoLPlugin.furnaceMenuEntries);
    }

    @Test
    void anvilReplayToleratesRecordingChangesDuringIteration() {
        assertReplayToleratesChanges(QoLPlugin.anvilMenuEntries);
    }

    private static void assertReplayToleratesChanges(List<NewMenuEntry> recorded) {
        NewMenuEntry first = entry("Withdraw-1");
        NewMenuEntry second = entry("Withdraw-All");
        recorded.add(first);
        recorded.add(second);

        List<NewMenuEntry> replayed = new ArrayList<>();
        for (NewMenuEntry menuEntry : recorded) {
            replayed.add(menuEntry);
            recorded.clear();
            recorded.add(entry("Deposit-1"));
        }

        assertEquals(List.of(first, second), replayed);
        assertEquals(1, recorded.size());
    }

    private static NewMenuEntry entry(String option) {
        return new NewMenuEntry(option, "Coins", 1, MenuAction.CC_OP, 3, 786445, false);
    }
}
