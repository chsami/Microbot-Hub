package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DigsiteTravelRestorationTest
{
    private static final WorldPoint FEROX = new WorldPoint(3131, 3632, 0);
    private static final WorldPoint BIRDHOUSE = new WorldPoint(3762, 3755, 0);
    private static final WorldPoint LANDING = new WorldPoint(3763, 3869, 1);

    @Test void chargedPendantIsSelectedForBirdhousesAfterLocalCourseTravel() throws Exception
    {
        try (Fixture f = new Fixture()) {
            Runnable release = f.local.acquire();
            assertFalse(f.config.useTeleportations());
            release.run();
            assertTrue(f.walker.previewRouteTo(BIRDHOUSE));
            f.settle();
            Object teleport = field(f.engine, "activeTeleportProvider");
            assertNotNull(teleport, "Expected charged pendant route: " + f.description());
            assertEquals("Digsite pendant: Fossil Island", field(teleport, "name"));
            assertEquals(LANDING, field(f.engine, "expectedTeleportDestination"));
        }
    }

    @Test void disabledTeleportPreferenceReproducesTheCastleWarsDetour() throws Exception
    {
        try (Fixture f = new Fixture()) {
            f.teleports.set(false);
            assertTrue(f.walker.previewRouteTo(BIRDHOUSE));
            f.settle();
            assertNull(field(f.engine, "activeTeleportProvider"));
            assertTrue(((List<?>) field(f.engine, "globalRoute")).contains(new WorldPoint(2440, 3089, 0)),
                "Disabled teleports must reproduce the reported Castle Wars detour: " + f.description());
        }
    }

    @Test void restoredSettingsDoNotAllowMembersPendantOnFreeWorld() throws Exception
    {
        try (Fixture f = new Fixture()) {
            when(f.client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
            Runnable release = f.local.acquire();
            release.run();
            assertTrue(f.walker.previewRouteTo(BIRDHOUSE));
            f.settle();
            assertNull(field(f.engine, "activeTeleportProvider"), "Restoring preferences must preserve current-world eligibility");
        }
    }

    static Object field(Object object, String name) throws Exception {
        Field f = WalkerTestReflection.declaredField(object.getClass(), name);f.setAccessible(true);return f.get(object);
    }
    static void set(Object object, String name, Object value) throws Exception {
        Field f = WalkerTestReflection.declaredField(object.getClass(), name);f.setAccessible(true);f.set(object, value);
    }

    private static final class Fixture implements AutoCloseable
    {
        final Client client = mock(Client.class);
        final MockedStatic<Microbot> micro = mockStatic(Microbot.class);
        final MockedStatic<Rs2Player> playerApi = mockStatic(Rs2Player.class);
        final MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
        final MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
        final WalkerLocalTravel local = new WalkerLocalTravel();
        final AtomicBoolean teleports = new AtomicBoolean(true);
        final EfficientWalkerConfig config;
        final EfficientWalker walker;
        final Object engine;

        Fixture() throws Exception {
            Player player = mock(Player.class);WorldView view = mock(WorldView.class);
            Scene scene = mock(Scene.class);CollisionData collision = mock(CollisionData.class);
            ItemContainer items = mock(ItemContainer.class);ItemComposition pendant = mock(ItemComposition.class);
            when(client.isClientThread()).thenReturn(true);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getWorld()).thenReturn(403);
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            when(client.getEnergy()).thenReturn(10000);
            when(client.getLocalPlayer()).thenReturn(player);
            when(client.getTopLevelWorldView()).thenReturn(view);
            when(player.getWorldLocation()).thenReturn(FEROX);
            when(player.getWorldView()).thenReturn(view);
            when(player.getLocalLocation()).thenReturn(new net.runelite.api.coords.LocalPoint(50*128+64,50*128+64));
            when(view.isTopLevel()).thenReturn(true);when(view.getId()).thenReturn(WorldView.TOPLEVEL);
            when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);
            when(view.getBaseX()).thenReturn(FEROX.getX()-50);when(view.getBaseY()).thenReturn(FEROX.getY()-50);
            when(view.getScene()).thenReturn(scene);when(scene.getTiles()).thenReturn(new Tile[1][104][104]);
            when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});when(collision.getFlags()).thenReturn(new int[104][104]);
            when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(items);
            when(items.getItems()).thenReturn(new Item[]{new Item(11193,1)});
            when(client.getItemDefinition(11193)).thenReturn(pendant);when(pendant.isMembers()).thenReturn(true);
            playerApi.when(Rs2Player::getWorldLocation).thenReturn(FEROX);
            playerApi.when(() -> Rs2Player.getQuestState(any(Quest.class))).thenReturn(QuestState.FINISHED);
            inventory.when(() -> Rs2Inventory.hasItem(any(int[].class))).thenAnswer(i -> Arrays.stream((int[])i.getRawArguments()[0]).anyMatch(id -> id==11193));
            inventory.when(() -> Rs2Inventory.itemQuantity(11193)).thenReturn(1);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(27);
            micro.when(Microbot::getClient).thenReturn(client);
            ClientThread thread = mock(ClientThread.class);
            when(thread.invoke(any(Supplier.class))).thenAnswer(i -> ((Supplier<?>)i.getArgument(0)).get());
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i -> Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            micro.when(Microbot::getClientThread).thenReturn(thread);
            config = local.effectiveConfig(new EfficientWalkerConfig() {
                @Override public boolean useItemsFromBank() { return false; }
                @Override public boolean useTeleportations() { return teleports.get(); }
                @Override public boolean useFairyRings() { return false; }
                @Override public boolean useSpiritTrees() { return false; }
                @Override public boolean useMinigameTeleports() { return false; }
                @Override public boolean useCharterShips() { return false; }
                @Override public boolean useHotAirBalloons() { return false; }
                @Override public boolean useWildernessTeleports() { return false; }
                @Override public boolean useWildernessLevers() { return false; }
                @Override public boolean useWildernessObelisks() { return false; }
                @Override public boolean allowWildernessTravel() { return true; }
            });
            walker = new EfficientWalker(client, config, TestRouteSubmissions.immediate());
            engine = field(walker, "engine");
            set(engine, "suppliedPlanningExecutor", (Executor)Runnable::run);set(engine, "completionExecutor", (Executor)Runnable::run);
        }
        void settle() throws Exception {
            Method tick=WalkerTestReflection.declaredMethod(engine.getClass(), "tick");tick.setAccessible(true);
            for (int i=0;i<5;i++) tick.invoke(engine);
        }
        String description() throws Exception {
            return walker.getStatus()+" / "+walker.getPlanningFailure()+" / "+field(engine,"failureMessage")+" / "+field(engine,"globalRoute");
        }
        @Override public void close() {
            walker.shutdown();bank.close();inventory.close();playerApi.close();micro.close();
        }
    }
}
