package net.runelite.client.plugins.microbot.accountstarter.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudfootTextTest {
    @Test
    void mudfootsWordsEndOnlyTheirOwnFight() {
        String after = MudfootActivity.plain("Yes, we do good work making them dead.");
        assertTrue(MudfootActivity.outlawDone(MudfootActivity.Step.RANGER, after));
        assertFalse(MudfootActivity.outlawDone(MudfootActivity.Step.MAGE, after));
        assertTrue(MudfootActivity.outlawDone(MudfootActivity.Step.RANGER,
                MudfootActivity.plain("Other goblins say they see next thief by ruined building, west of shop.")));
    }

    @Test
    void mudfootsLineAfterTheWarriorCountsAsTheKill() {
        assertTrue(MudfootActivity.outlawDone(MudfootActivity.Step.WARRIOR,
                MudfootActivity.plain("Stupid humans should never have crossed Mudfoot the good at quests.")));
        assertTrue(MudfootActivity.outlawDone(MudfootActivity.Step.WARRIOR,
                MudfootActivity.plain("We should go tell guard man that nasty thieves have gone dead.")));
    }

    @Test
    void lineBreaksDoNotSplitTheWordsAStepWaitsFor() {
        assertEquals("me go grab poker and meet sidekick", MudfootActivity.plain("Me go grab<br>poker and meet sidekick"));
    }
}
