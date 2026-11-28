package net.runelite.client.plugins.microbot.sandcrabs;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.sandcrabs.enums.State;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    @Test
    void resetAggroWithoutStartLocationDoesNotWalk() {
        SandCrabScript script = new SandCrabScript();
        script.state = State.RESET_AGGRO;

        try (MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> rs2Walker = mockStatic(Rs2Walker.class)) {
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(1776, 3468, 0));

            script.resetAggro(mock(SandCrabPlugin.class));
            script.walkBack(mock(SandCrabPlugin.class));

            rs2Walker.verifyNoInteractions();
            assertEquals(State.RESET_AGGRO, script.state);
        }
    }

    @Test
    void resetAggroWithoutPlayerLocationDoesNotWalk() {
        SandCrabScript script = new SandCrabScript();
        script.startLocation = new WorldPoint(1776, 3468, 0);
        script.state = State.RESET_AGGRO;

        try (MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> rs2Walker = mockStatic(Rs2Walker.class)) {
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(null);

            script.resetAggro(mock(SandCrabPlugin.class));

            rs2Walker.verifyNoInteractions();
            assertEquals(State.RESET_AGGRO, script.state);
        }
    }

    @Test
    void resetAggroWalksNorthOfOwnStartLocation() {
        SandCrabScript script = new SandCrabScript();
        script.startLocation = new WorldPoint(1776, 3468, 0);
        script.state = State.RESET_AGGRO;

        try (MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> rs2Walker = mockStatic(Rs2Walker.class)) {
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(1776, 3468, 0));
            rs2Walker.when(() -> Rs2Walker.walkTo(new WorldPoint(1776, 3508, 0), 4)).thenReturn(true);

            script.resetAggro(mock(SandCrabPlugin.class));

            assertEquals(State.WALK_BACK, script.state);
        }
    }

    @Test
    void resetAggroCountsAsDoneOnceFarFromStartWhenResetTileIsUnreachable() {
        SandCrabScript script = new SandCrabScript();
        script.startLocation = new WorldPoint(1749, 3469, 0);
        script.state = State.RESET_AGGRO;

        try (MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> rs2Walker = mockStatic(Rs2Walker.class)) {
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(1747, 3510, 0));

            script.resetAggro(mock(SandCrabPlugin.class));

            rs2Walker.verifyNoInteractions();
            assertEquals(State.WALK_BACK, script.state);
        }
    }

    @Test
    void resetAggroKeepsWalkingWhileStillNearStart() {
        SandCrabScript script = new SandCrabScript();
        script.startLocation = new WorldPoint(1749, 3469, 0);
        script.state = State.RESET_AGGRO;

        try (MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> rs2Walker = mockStatic(Rs2Walker.class)) {
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(1749, 3485, 0));
            rs2Walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenReturn(false);

            script.resetAggro(mock(SandCrabPlugin.class));

            rs2Walker.verify(() -> Rs2Walker.walkTo(new WorldPoint(1749, 3509, 0), 4));
            assertEquals(State.RESET_AGGRO, script.state);
        }
    }

    @Test
    void restartClearsStateLeftFromPreviousRun() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        SandCrabScript script = new SandCrabScript() {{
            scheduledExecutorService = executor;
        }};
        script.state = State.RESET_AGGRO;
        script.afkTimer = 15;
        script.hijackTimer = 12;
        script.startLocation = new WorldPoint(1747, 3510, 0);
        script.initSandCrabLocations.get(0).scanned = true;

        script.run(mock(SandCrabConfig.class), mock(SandCrabPlugin.class));

        assertEquals(State.FIGHT, script.state);
        assertEquals(0, script.afkTimer);
        assertEquals(0, script.hijackTimer);
        assertNull(script.startLocation);
        assertFalse(script.initSandCrabLocations.get(0).scanned);
    }
}
