package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SafeSpotTest {

    @Test
    void unsetStateDoesNotBlockSafeSpot() {
        assertFalse(SafeSpot.isBlockedByState(null));
    }

    @Test
    void bankingAndWalkingBlockSafeSpot() {
        assertTrue(SafeSpot.isBlockedByState(State.BANKING));
        assertTrue(SafeSpot.isBlockedByState(State.WALKING));
    }

    @Test
    void otherStatesDoNotBlockSafeSpot() {
        for (State state : State.values()) {
            if (state != State.BANKING && state != State.WALKING) {
                assertFalse(SafeSpot.isBlockedByState(state));
            }
        }
    }
}
