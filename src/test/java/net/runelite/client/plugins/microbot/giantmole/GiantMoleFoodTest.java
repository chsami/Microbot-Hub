package net.runelite.client.plugins.microbot.giantmole;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class GiantMoleFoodTest {

    @Test
    void eatsWithoutQoLConfigPresent() {
        GiantMoleConfig config = mock(GiantMoleConfig.class);
        when(config.useRockCake()).thenReturn(false);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            assertDoesNotThrow(() -> new GiantMoleScript().handleFood(config));

            player.verify(() -> Rs2Player.eatAt(anyInt()));
            microbot.verify(Microbot::getConfigManager, never());
        }
    }

    @Test
    void skipsEatingWhenUsingRockCake() {
        GiantMoleConfig config = mock(GiantMoleConfig.class);
        when(config.useRockCake()).thenReturn(true);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            assertDoesNotThrow(() -> new GiantMoleScript().handleFood(config));

            player.verify(() -> Rs2Player.eatAt(anyInt()), never());
        }
    }
}
