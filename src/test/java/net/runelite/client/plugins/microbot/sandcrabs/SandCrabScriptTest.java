package net.runelite.client.plugins.microbot.sandcrabs;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandCrabScriptTest {

    @Test
    void unselectedInventorySetupIsMissing() {
        assertFalse(SandCrabScript.hasInventorySetup(null));
    }

    @Test
    void setupWithoutNameIsMissing() {
        InventorySetup setup = mock(InventorySetup.class);
        assertFalse(SandCrabScript.hasInventorySetup(setup));

        when(setup.getName()).thenReturn("");
        assertFalse(SandCrabScript.hasInventorySetup(setup));
    }

    @Test
    void namedSetupIsPresent() {
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getName()).thenReturn("Sand crabs");

        assertTrue(SandCrabScript.hasInventorySetup(setup));
    }

    @Test
    void tickWithoutLocalPlayerSkipsWithoutError() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        SandCrabScript script = new SandCrabScript() {{
            scheduledExecutorService = executor;
        }};
        SandCrabConfig config = mock(SandCrabConfig.class);
        when(config.DisableInventorySetup()).thenReturn(true);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Combat> rs2Combat = mockStatic(Rs2Combat.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(null);

            script.run(config, mock(SandCrabPlugin.class));
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            microbot.verify(() -> Microbot.logStackTrace(any(), any()), never());
            rs2Combat.verify(Rs2Combat::enableAutoRetialiate, never());
            rs2Player.verify(() -> Rs2Player.getPlayers(any()), never());
        }
    }
}
