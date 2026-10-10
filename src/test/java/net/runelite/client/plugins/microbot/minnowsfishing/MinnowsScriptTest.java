package net.runelite.client.plugins.microbot.minnowsfishing;

import net.runelite.client.game.FishingSpot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MinnowsScriptTest {
    @Test
    void targetsEveryMinnowFishingSpot() {
        int[] expected = FishingSpot.MINNOW.getIds().clone();
        int[] actual = MinnowsScript.MINNOW_SPOT_IDS.clone();
        Arrays.sort(expected);
        Arrays.sort(actual);
        assertArrayEquals(expected, actual);
    }
}
