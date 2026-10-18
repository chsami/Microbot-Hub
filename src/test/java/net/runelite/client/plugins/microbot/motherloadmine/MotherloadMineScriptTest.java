package net.runelite.client.plugins.microbot.motherloadmine;

import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class MotherloadMineScriptTest {
    @Test
    void missingAntibanActivityFallsBackToGeneralMining() {
        assertEquals(Activity.GENERAL_MINING.getActivityIntensity(), MotherloadMineScript.miningIntensity(null));
    }

    @Test
    void usesConfiguredAntibanActivityIntensity() {
        assertEquals(Activity.GENERAL_COMBAT.getActivityIntensity(), MotherloadMineScript.miningIntensity(Activity.GENERAL_COMBAT));
    }
}
