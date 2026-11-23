package net.runelite.client.plugins.microbot.karambwans;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.karambwans.enums.KarambwanBankLocation;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class GabulhasKarambwansScriptTest {
    private static final WorldPoint ZANARIS_BANK = new WorldPoint(2381, 4455, 0);
    private static final int MAX_WALK_TO_BANK_ATTEMPTS = 3;

    private static GabulhasKarambwansScript startedScript() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        doReturn(mock(ScheduledFuture.class)).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        GabulhasKarambwansScript script = new GabulhasKarambwansScript() {{
            scheduledExecutorService = executor;
        }};
        GabulhasKarambwansConfig config = mock(GabulhasKarambwansConfig.class);
        when(config.bankLocation()).thenReturn(KarambwanBankLocation.NONE);
        script.run(config);
        return script;
    }

    @Test
    void failedWalksToZanarisBankAreRetriedThenFallBackToNearestBank() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
                 MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class);
                 MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
                 MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
                 MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class)) {
                rs2Player.when(() -> Rs2Player.isInArea(any(WorldPoint.class), anyInt())).thenReturn(false);
                walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenReturn(false);

                startedScript().doBank();

                walker.verify(() -> Rs2Walker.walkTo(ZANARIS_BANK, 3), times(MAX_WALK_TO_BANK_ATTEMPTS));
                bank.verify(Rs2Bank::walkToBankAndUseBank);
                bank.verify(Rs2Bank::openBank);
            }
        });
    }

    @Test
    void arrivingOnTheFirstWalkWalksOnlyOnce() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
                 MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class);
                 MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
                 MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
                 MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class)) {
                rs2Player.when(() -> Rs2Player.isInArea(any(WorldPoint.class), anyInt())).thenReturn(false);
                walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenReturn(true);
                bank.when(Rs2Bank::walkToBankAndUseBank).thenReturn(true);

                startedScript().doBank();

                walker.verify(() -> Rs2Walker.walkTo(ZANARIS_BANK, 3), times(1));
                bank.verify(Rs2Bank::walkToBankAndUseBank);
                bank.verify(Rs2Bank::openBank, times(0));
            }
        });
    }
}
