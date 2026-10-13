package net.runelite.client.plugins.microbot.giantmole;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.giantmole.enums.GiantMoleState;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class GiantMoleHopResultTest {

    @AfterEach
    void resetStatics() {
        GiantMoleScript.checkedIfWorldOccupied = false;
        GiantMoleScript.isWorldOccupied = true;
        GiantMoleScript.state = GiantMoleState.IDLE;
    }

    @Test
    void refusedHopKeepsOccupiedWorldVerdictSoTheNextLoopRetries() {
        GiantMoleScript.checkedIfWorldOccupied = true;
        GiantMoleScript.isWorldOccupied = true;
        GiantMoleScript.state = GiantMoleState.HOPPING;

        hop(false);

        assertTrue(GiantMoleScript.checkedIfWorldOccupied);
        assertEquals(GiantMoleState.HOPPING, GiantMoleScript.state);
    }

    @Test
    void successfulHopClearsVerdictForTheNewWorld() {
        GiantMoleScript.checkedIfWorldOccupied = true;
        GiantMoleScript.isWorldOccupied = true;
        GiantMoleScript.state = GiantMoleState.HOPPING;

        hop(true);

        assertFalse(GiantMoleScript.checkedIfWorldOccupied);
        assertEquals(GiantMoleState.IDLE, GiantMoleScript.state);
    }

    private static void hop(boolean hopResult) {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Login> login = mockStatic(Login.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::isMember).thenReturn(true);
            login.when(() -> Login.getRandomWorld(true)).thenReturn(420);
            microbot.when(() -> Microbot.hopToWorld(420)).thenReturn(hopResult);

            new GiantMoleScript().hopWorlds();

            microbot.verify(() -> Microbot.hopToWorld(420));
        }
    }
}
