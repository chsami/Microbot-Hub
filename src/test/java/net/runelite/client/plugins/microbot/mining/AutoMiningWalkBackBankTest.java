package net.runelite.client.plugins.microbot.mining;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoMiningWalkBackBankTest {

    private static final List<BankLocation> BANKS = Arrays.asList(
            BankLocation.LUMBRIDGE_TOP,
            BankLocation.VARROCK_WEST,
            BankLocation.VARROCK_EAST);

    @Test
    void picksTheBankTheWalkingRouteEndsAt() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3175, 3367, 0),
                new WorldPoint(3180, 3400, 0),
                new WorldPoint(3183, 3440, 0));

        assertEquals(BankLocation.VARROCK_WEST, AutoMiningScript.bankAtPathEnd(path, BANKS));
    }

    @Test
    void acceptsARouteEndingNextToTheBankTile() {
        List<WorldPoint> path = Collections.singletonList(new WorldPoint(3254, 3423, 0));

        assertEquals(BankLocation.VARROCK_EAST, AutoMiningScript.bankAtPathEnd(path, BANKS));
    }

    @Test
    void returnsNothingForAPartialRouteThatStopsShortOfEveryBank() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3175, 3367, 0),
                new WorldPoint(3176, 3368, 0));

        assertNull(AutoMiningScript.bankAtPathEnd(path, BANKS));
    }

    @Test
    void returnsNothingWhenNoRouteWasFound() {
        assertNull(AutoMiningScript.bankAtPathEnd(Collections.emptyList(), BANKS));
        assertNull(AutoMiningScript.bankAtPathEnd(null, BANKS));
    }

    @Test
    void ignoresBanksTheRouteWasNotPlannedTo() {
        List<WorldPoint> path = Collections.singletonList(new WorldPoint(3183, 3441, 0));

        assertNull(AutoMiningScript.bankAtPathEnd(path, Collections.singletonList(BankLocation.LUMBRIDGE_TOP)));
    }
}
