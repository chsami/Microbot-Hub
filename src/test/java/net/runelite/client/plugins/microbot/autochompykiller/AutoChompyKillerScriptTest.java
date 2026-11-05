package net.runelite.client.plugins.microbot.autochompykiller;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AutoChompyKillerScriptTest {

    @Test
    void tickWithoutLocalPlayerLocationSkipsWithoutError() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        AutoChompyKillerScript script = new AutoChompyKillerScript() {{
            scheduledExecutorService = executor;
        }};

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(null);

            script.run(mock(AutoChompyKillerConfig.class));
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            rs2Player.verify(Rs2Player::getWorldLocation);
            microbot.verify(Microbot::getClient, never());
            microbot.verify(Microbot::getRs2NpcCache, never());
            microbot.verify(Microbot::getRs2TileObjectCache, never());
        }
    }
}
