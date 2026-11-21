package net.runelite.client.plugins.microbot.valetotems;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.microbot.valetotems.utils.CoordinateUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.GameObjectUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CoordinateUtilsNullPlayerTest {

    private MockedStatic<Microbot> microbot;
    private Client client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @AfterEach
    void tearDown() {
        microbot.close();
    }

    @Test
    void nullLocalPlayerYieldsNotFoundResults() {
        when(client.getLocalPlayer()).thenReturn(null);
        WorldPoint target = new WorldPoint(1370, 3300, 0);

        assertNull(CoordinateUtils.getPlayerLocation());
        assertEquals(Integer.MAX_VALUE, CoordinateUtils.getDistanceToPlayer(target));
        assertFalse(CoordinateUtils.isPlayerNear(target, 5));
        assertNull(CoordinateUtils.getNearestTotemLocation());
        assertEquals(-1, CoordinateUtils.getDistanceToNearestTotem());
        assertEquals(Integer.MAX_VALUE, CoordinateUtils.getDistanceToBank());
        assertFalse(CoordinateUtils.isAtBankingPosition());
        assertFalse(CoordinateUtils.isNearBank(10));
        assertFalse(CoordinateUtils.isAtTotemLocation(TotemLocation.values()[0], 3));
        assertTrue(GameObjectUtils.findGameObjects(1, null, 10).isEmpty());
    }

    @Test
    void presentLocalPlayerYieldsRealDistances() {
        Player player = mock(Player.class);
        WorldPoint playerLocation = new WorldPoint(1370, 3300, 0);
        when(player.getWorldLocation()).thenReturn(playerLocation);
        when(client.getLocalPlayer()).thenReturn(player);

        assertEquals(playerLocation, CoordinateUtils.getPlayerLocation());
        assertEquals(4, CoordinateUtils.getDistanceToPlayer(new WorldPoint(1374, 3300, 0)));
        assertTrue(CoordinateUtils.isPlayerNear(new WorldPoint(1374, 3300, 0), 5));
    }
}
