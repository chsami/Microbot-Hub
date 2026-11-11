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

class MagicAxesRouteTest
{
    @Test void recordedDoorMakesTheHutReachableFromCampAndEdgeville() throws Exception {
        for (WorldPoint origin : new WorldPoint[]{new WorldPoint(3741,3805,0),new WorldPoint(3094,3491,0)}) {
            try (Fixture f = new Fixture(origin, true)) {
                assertTrue(f.walker.previewRouteTo(new WorldPoint(3190,3960,0)));f.settle();
                assertEquals(EfficientWalker.Status.PREVIEW, f.walker.getStatus(), f.description());
                List<?> route = (List<?>) field(f.engine, "globalRoute");
                assertTrue(route.contains(MagicAxesHutTransports.OUTSIDE), route.toString());
                assertTrue(route.contains(MagicAxesHutTransports.INSIDE), route.toString());
                assertTrue(route.contains(new WorldPoint(3154,3924,0)), "Expected maintained Wilderness lever landing: " + route);
            }
        }
    }

    @Test void recordedMageBankWebsAndLeverReachTheBankAndReturn() throws Exception {
        WorldPoint bank = new WorldPoint(2534,4712,0);
        try (Fixture f = new Fixture(new WorldPoint(3170,3953,0), true)) {
            assertTrue(f.walker.previewRouteTo(bank));f.settle();
            assertEquals(EfficientWalker.Status.PREVIEW, f.walker.getStatus(), f.description());
            List<?> route = (List<?>) field(f.engine, "globalRoute");
            for (WorldPoint tile : List.of(new WorldPoint(3095,3957,0), new WorldPoint(3092,3957,0),
                    new WorldPoint(3090,3956,0), new WorldPoint(2539,4712,0)))
                assertTrue(route.contains(tile), "Missing recorded Mage Bank transition " + tile + ": " + route);
        }
        try (Fixture f = new Fixture(bank, true)) {
            assertTrue(f.walker.previewRouteTo(new WorldPoint(3170,3953,0)));f.settle();
            assertEquals(EfficientWalker.Status.PREVIEW, f.walker.getStatus(), f.description());
            List<?> route = (List<?>) field(f.engine, "globalRoute");
            assertTrue(route.contains(new WorldPoint(3090,3956,0)), route.toString());
        }
    }

    @Test void disabledLeversAreAbsentFromBothInstalledCatalogues() throws Exception {
        try (Fixture f = new Fixture(new WorldPoint(3094,3491,0), false)) {
            assertTrue(f.walker.previewRouteTo(new WorldPoint(3190,3960,0)));f.settle();
            Object network = field(f.walker, "networkTransportSupport");
            Map<?, ?> shared = (Map<?, ?>) field(network, "sharedCatalogEdges");
            assertTrue(shared.values().stream().map(net.runelite.client.plugins.microbot.shortestpath.Transport.class::cast)
                .noneMatch(t -> t.getType() == net.runelite.client.plugins.microbot.shortestpath.TransportType.TELEPORTATION_LEVER));
            List<?> route = (List<?>) field(f.engine, "globalRoute");
            assertFalse(route.contains(new WorldPoint(3154,3924,0)), route.toString());
        }
    }

    @Test void allSevenLeverLinksRespectWorldAndWildernessSettings() throws Exception {
        for (String mode : List.of("enabled", "disabled", "wilderness-off", "free-world")) {
            try (Fixture f = new Fixture(new WorldPoint(3094,3491,0), !mode.equals("disabled"))) {
                if (mode.equals("wilderness-off")) f.wilderness.set(false);
                if (mode.equals("free-world")) when(f.client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
                f.walker.previewRouteTo(new WorldPoint(3100,3490,0));f.settle();
                Object network = field(f.walker, "networkTransportSupport");
                Map<?, ?> shared = (Map<?, ?>) field(network, "sharedCatalogEdges");
                long levers = shared.values().stream().map(net.runelite.client.plugins.microbot.shortestpath.Transport.class::cast)
                    .filter(t -> t.getType() == net.runelite.client.plugins.microbot.shortestpath.TransportType.TELEPORTATION_LEVER).count();
                assertEquals(mode.equals("enabled") ? 7L : 0L, levers, mode);
                Map<?, ?> injected = (Map<?, ?>) field(network, "injectedEdges");
                int nativeLevers = 0;
                for (Object edge : injected.values()) if ("Wilderness lever".equals(field(edge,"label"))) nativeLevers++;
                assertEquals(mode.equals("enabled") ? 7 : 0, nativeLevers, mode);
            }
        }
    }

    @Test void lockpickAndThievingAreRequiredBeforeInstallingTheHutEntry() throws Exception {
        for (boolean lockpick : new boolean[]{false,true}) try (Fixture f = new Fixture(new WorldPoint(3094,3491,0), true)) {
            if (!lockpick) f.inventory.when(() -> Rs2Inventory.hasItem(any(int[].class))).thenReturn(false);
            else f.playerApi.when(() -> Rs2Player.getBoostedSkillLevel(Skill.THIEVING)).thenReturn(22);
            f.walker.previewRouteTo(new WorldPoint(3190,3960,0));f.settle();
            Object network = field(f.walker, "networkTransportSupport");
            Map<?, ?> shared = (Map<?, ?>) field(network, "sharedCatalogEdges");
            assertTrue(shared.values().stream().map(net.runelite.client.plugins.microbot.shortestpath.Transport.class::cast)
                .noneMatch(MagicAxesHutTransports::isEntry));
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
        final AtomicBoolean wilderness = new AtomicBoolean(true);
        final EfficientWalkerConfig config;
        final EfficientWalker walker;
        final Object engine;

        Fixture(WorldPoint origin, boolean levers) throws Exception {
            Player player = mock(Player.class);WorldView view = mock(WorldView.class);
            Scene scene = mock(Scene.class);CollisionData collision = mock(CollisionData.class);
            ItemContainer items = mock(ItemContainer.class);ItemComposition pendant = mock(ItemComposition.class);
            when(client.isClientThread()).thenReturn(true);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getWorld()).thenReturn(403);
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
            when(client.getEnergy()).thenReturn(10000);
            when(client.getLocalPlayer()).thenReturn(player);
            when(client.getTopLevelWorldView()).thenReturn(view);
            when(player.getWorldLocation()).thenReturn(origin);
            when(player.getWorldView()).thenReturn(view);
            when(player.getLocalLocation()).thenReturn(new net.runelite.api.coords.LocalPoint(50*128+64,50*128+64));
            when(view.isTopLevel()).thenReturn(true);when(view.getId()).thenReturn(WorldView.TOPLEVEL);
            when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);
            when(view.getBaseX()).thenReturn(origin.getX()-50);when(view.getBaseY()).thenReturn(origin.getY()-50);
            when(view.getScene()).thenReturn(scene);when(scene.getTiles()).thenReturn(new Tile[1][104][104]);
            when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});when(collision.getFlags()).thenReturn(new int[104][104]);
            when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(items);
            when(items.getItems()).thenReturn(new Item[]{new Item(11193,1),new Item(1523,1),new Item(946,1),new Item(1712,1)});
            when(client.getItemDefinition(11193)).thenReturn(pendant);when(pendant.isMembers()).thenReturn(true);
            playerApi.when(Rs2Player::getWorldLocation).thenReturn(origin);
            playerApi.when(() -> Rs2Player.getQuestState(any(Quest.class))).thenReturn(QuestState.FINISHED);
            inventory.when(() -> Rs2Inventory.hasItem(any(int[].class))).thenAnswer(i -> Arrays.stream((int[])i.getRawArguments()[0]).anyMatch(id -> id==11193 || id==1523 || id==946 || id==1712));
            inventory.when(() -> Rs2Inventory.itemQuantity(anyInt())).thenAnswer(i -> java.util.Set.of(11193,1523,946,1712).contains((Integer)i.getArgument(0))?1:0);
            playerApi.when(() -> Rs2Player.getRealSkillLevel(any(Skill.class))).thenReturn(99);
            playerApi.when(() -> Rs2Player.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
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
                @Override public boolean useWildernessLevers() { return levers; }
                @Override public boolean useWildernessObelisks() { return false; }
                @Override public boolean allowWildernessTravel() { return wilderness.get(); }
            });
            walker = new EfficientWalker(client, config, TestRouteSubmissions.immediate());
            engine = field(walker, "engine");
            set(engine, "suppliedPlanningExecutor", (Executor)Runnable::run);set(engine, "completionExecutor", (Executor)Runnable::run);
        }
        void settle() throws Exception {
            Method tick=WalkerTestReflection.declaredMethod(engine.getClass(), "tick");tick.setAccessible(true);
            for (int i=0;i<60;i++) tick.invoke(engine);
        }
        String description() throws Exception {
            return walker.getStatus()+" / "+walker.getPlanningFailure()+" / "+field(engine,"failureMessage")+" / "+field(engine,"globalRoute");
        }
        @Override public void close() {
            walker.shutdown();bank.close();inventory.close();playerApi.close();micro.close();
        }
    }
}
