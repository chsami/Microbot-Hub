package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Eating-threshold decision (no client): {@link EatAction} eats at or below 50% health and stands down
 * above it. The health read is stubbed so only the decision boundary is under test.
 */
class EatActionTest {

    private final EatAction action = new EatAction();
    private final ZulrahState state = new ZulrahState(null); // needsExecution ignores the context

    @Test
    void eatsAtOrBelowFiftyPercentHealth() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getHealthPercentage).thenReturn(50.0);
            assertTrue(action.needsExecution(state), "should eat at exactly 50%");

            player.when(Rs2Player::getHealthPercentage).thenReturn(30.0);
            assertTrue(action.needsExecution(state), "should eat below 50%");
        }
    }

    @Test
    void doesNotEatAboveFiftyPercentHealth() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getHealthPercentage).thenReturn(50.5);
            assertFalse(action.needsExecution(state), "should not eat above 50%");

            player.when(Rs2Player::getHealthPercentage).thenReturn(100.0);
            assertFalse(action.needsExecution(state));
        }
    }

    @Test
    void executeReportsWhetherFoodWasActuallyEaten() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(() -> Rs2Player.eatAt(50, true)).thenReturn(true);
            assertTrue((Boolean) action.execute(state), "execute should return the eat result");

            player.when(() -> Rs2Player.eatAt(50, true)).thenReturn(false);
            assertFalse((Boolean) action.execute(state), "no food eaten -> false");
        }
    }
}
