package net.runelite.client.plugins.microbot.droslayer;

import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerStartupLocationTest {
    @Test void liveAssignmentNearItsSelectedAreaCanUseNativeStartupButBanksCannot() {
        var config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.attackRadius()).thenReturn(12);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(i ->
            Optional.ofNullable(((Callable<?>) i.getArgument(0)).call()));
        try (var micro = mockStatic(Microbot.class); var player = mockStatic(Rs2Player.class);
             var slayer = mockStatic(Rs2Slayer.class); var task = mockStatic(DroSlayerWildernessTaskReader.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(Microbot::isLoggedIn).thenReturn(true);
            slayer.when(Rs2Slayer::getSlayerTaskSize).thenReturn(75);
            task.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Magic axes");
            task.when(DroSlayerWildernessTaskReader::isKrystiliaAssignment).thenReturn(true);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3190,3960,0));
            assertTrue(DroSlayerStartupLocation.atCurrentTask(config));
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3741,3805,0));
            assertFalse(DroSlayerStartupLocation.atCurrentTask(config));
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3094,3491,0));
            assertFalse(DroSlayerStartupLocation.atCurrentTask(config));
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3190,3960,1));
            assertFalse(DroSlayerStartupLocation.atCurrentTask(config));
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3190,3960,0));
            task.when(DroSlayerWildernessTaskReader::isKrystiliaAssignment).thenReturn(false);
            assertFalse(DroSlayerStartupLocation.atCurrentTask(config));
            task.when(DroSlayerWildernessTaskReader::isKrystiliaAssignment).thenReturn(true);
            slayer.when(Rs2Slayer::getSlayerTaskSize).thenReturn(0);
            assertFalse(DroSlayerStartupLocation.atCurrentTask(config));
            slayer.when(Rs2Slayer::getSlayerTaskSize).thenReturn(75);
            micro.when(Microbot::isLoggedIn).thenReturn(false);
            assertFalse(DroSlayerStartupLocation.atCurrentTask(config));
        }
    }
}
