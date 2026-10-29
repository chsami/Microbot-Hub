package net.runelite.client.plugins.microbot.tithefarming;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmState;
import net.runelite.client.plugins.microbot.tithefarming.models.TitheFarmPlant;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitheFarmingCoreLoopTest {

    private static final WorldPoint SACK = new WorldPoint(1805, 3501, 0);
    private static final WorldPoint FAR_AWAY = new WorldPoint(1835, 3501, 0);

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

            assertDoesNotThrow(() -> method("coreLoop", TitheFarmingConfig.class).invoke(new TitheFarmingScript(), mock(TitheFarmingConfig.class)));

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

            method("coreLoop", TitheFarmingConfig.class).invoke(new TitheFarmingScript(), mock(TitheFarmingConfig.class));

            walker.verify(() -> Rs2Walker.walkMiniMap(any(WorldPoint.class), anyDouble()));
        }
    }

    @Test
    void walkToBarrelDoesNotWalkWhenTheLocalPlayerIsNotLoaded() {
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(null);
        Rs2TileObjectCache cache = cacheWithNearest(objectAt(SACK));
        ClientThread clientThread = runsOnCaller();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);

            assertDoesNotThrow(() -> method("walkToBarrel").invoke(new TitheFarmingScript()));

            walker.verify(() -> Rs2Walker.walkMiniMap(any(WorldPoint.class), anyDouble()), never());
            global.verify(() -> Global.sleepUntil(any(BooleanSupplier.class)), never());
        }
    }

    @Test
    void depositSackWaitsWithoutClickingWhenTheLocalPlayerIsNotLoaded() throws Exception {
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(null);
        Rs2TileObjectModel sack = objectAt(SACK);
        Rs2TileObjectCache cache = cacheWithNearest(sack);
        ClientThread clientThread = runsOnCaller();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            inventory.when(() -> Rs2Inventory.hasItem(anyInt())).thenReturn(true);

            Object handled = method("depositSack").invoke(new TitheFarmingScript());

            assertEquals(true, handled);
            verify(sack, never()).click();
            walker.verify(() -> Rs2Walker.walkMiniMap(any(WorldPoint.class), anyDouble()), never());
        }
    }

    @Test
    void depositSackTreatsALostLocalPlayerAsNotArrivedWhileWalking() throws Exception {
        Player localPlayer = mock(Player.class);
        when(localPlayer.getWorldLocation()).thenReturn(FAR_AWAY);
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(localPlayer);
        Rs2TileObjectModel sack = objectAt(SACK);
        Rs2TileObjectCache cache = cacheWithNearest(sack);
        ClientThread clientThread = runsOnCaller();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            inventory.when(() -> Rs2Inventory.hasItem(anyInt())).thenReturn(true);

            method("depositSack").invoke(new TitheFarmingScript());

            walker.verify(() -> Rs2Walker.walkMiniMap(SACK, 1));
            ArgumentCaptor<BooleanSupplier> waits = ArgumentCaptor.forClass(BooleanSupplier.class);
            global.verify(() -> Global.sleepUntil(waits.capture()), times(2));
            BooleanSupplier arrived = waits.getAllValues().get(1);

            when(client.getLocalPlayer()).thenReturn(null);
            assertFalse(arrived.getAsBoolean());

            when(localPlayer.getWorldLocation()).thenReturn(SACK);
            when(client.getLocalPlayer()).thenReturn(localPlayer);
            assertTrue(arrived.getAsBoolean());
        }
    }

    private static TitheFarmPlant plantToVisit() {
        TitheFarmPlant plant = mock(TitheFarmPlant.class);
        when(plant.isEmptyPatchOrSeedling()).thenReturn(true);
        TitheFarmingScript.plants = new ArrayList<>(List.of(plant));
        TitheFarmingScript.state = TitheFarmState.PLANTING_SEEDS;
        return plant;
    }

    private static Rs2TileObjectModel objectAt(WorldPoint location) {
        Rs2TileObjectModel model = mock(Rs2TileObjectModel.class);
        when(model.getWorldLocation()).thenReturn(location);
        return model;
    }

    private static Rs2TileObjectCache cacheWithNearest(Rs2TileObjectModel model) {
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.nearest()).thenReturn(model);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        return cache;
    }

    @SuppressWarnings("unchecked")
    private static ClientThread runsOnCaller() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(call -> ((Supplier<Object>) call.getArgument(0)).get());
        return clientThread;
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = TitheFarmingScript.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
