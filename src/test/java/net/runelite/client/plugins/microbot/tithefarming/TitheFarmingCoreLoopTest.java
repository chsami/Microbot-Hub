package net.runelite.client.plugins.microbot.tithefarming;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmState;
import net.runelite.client.plugins.microbot.tithefarming.models.TitheFarmPlant;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitheFarmingCoreLoopTest {

    private List<TitheFarmPlant> savedPlants;
    private TitheFarmState savedState;

    @BeforeEach
    void saveStatics() {
        savedPlants = TitheFarmingScript.plants;
        savedState = TitheFarmingScript.state;
    }

    @AfterEach
    void restoreStatics() {
        TitheFarmingScript.plants = savedPlants;
        TitheFarmingScript.state = savedState;
    }

    @Test
    void coreLoopSkipsTheTickWhenTheLocalPlayerIsNotLoaded() {
        TitheFarmPlant plant = plantToVisit();
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(null);
        ClientThread clientThread = runsOnCaller();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            microbot.when(Microbot::getClient).thenReturn(client);

            assertDoesNotThrow(() -> coreLoop().invoke(new TitheFarmingScript(), mock(TitheFarmingConfig.class)));

            walker.verify(() -> Rs2Walker.walkMiniMap(any(WorldPoint.class), anyDouble()), never());
            inventory.verifyNoInteractions();
            verify(plant, never()).isValidToWater();
            verify(plant, never()).isValidToHarvest();
        }
    }

    @Test
    void coreLoopWalksToThePatchWhenTheLocalPlayerIsLoaded() throws Exception {
        plantToVisit();
        Player localPlayer = mock(Player.class);
        when(localPlayer.getWorldLocation()).thenReturn(new WorldPoint(1805, 3501, 0));
        WorldView worldView = mock(WorldView.class);
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(localPlayer);
        when(client.getTopLevelWorldView()).thenReturn(worldView);
        ClientThread clientThread = runsOnCaller();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            microbot.when(Microbot::getClient).thenReturn(client);

            coreLoop().invoke(new TitheFarmingScript(), mock(TitheFarmingConfig.class));

            walker.verify(() -> Rs2Walker.walkMiniMap(any(WorldPoint.class), anyDouble()));
        }
    }

    private static TitheFarmPlant plantToVisit() {
        TitheFarmPlant plant = mock(TitheFarmPlant.class);
        when(plant.isEmptyPatchOrSeedling()).thenReturn(true);
        TitheFarmingScript.plants = new ArrayList<>(List.of(plant));
        TitheFarmingScript.state = TitheFarmState.PLANTING_SEEDS;
        return plant;
    }

    @SuppressWarnings("unchecked")
    private static ClientThread runsOnCaller() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(call -> ((Supplier<Object>) call.getArgument(0)).get());
        return clientThread;
    }

    private static Method coreLoop() throws NoSuchMethodException {
        Method method = TitheFarmingScript.class.getDeclaredMethod("coreLoop", TitheFarmingConfig.class);
        method.setAccessible(true);
        return method;
    }
}
