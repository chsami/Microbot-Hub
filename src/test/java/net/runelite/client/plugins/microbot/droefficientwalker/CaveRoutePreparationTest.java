package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.DroSetupMouse;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CaveRoutePreparationTest {
    private static final WorldPoint HOME = new WorldPoint(3164, 3487, 0);
    private static final WorldPoint OUTSIDE = new WorldPoint(3168, 3172, 0), INSIDE = new WorldPoint(3168, 9572, 0);
    private static final WorldPoint JUNA = new WorldPoint(3250, 9516, 2);

    @Test void necklaceLandingOtherCavesAndTheExitNeverStartLightPreparation() throws Exception {
        try (Fixture f = new Fixture()) {
            for (List<WorldPoint> route : List.of(List.of(HOME, new WorldPoint(3245, 9501, 2), JUNA),
                    List.of(INSIDE, OUTSIDE, HOME), List.of(HOME, new WorldPoint(3104, 9573, 0), JUNA))) {
                f.engine.path = route;
                assertFalse(f.prepare());
            }
            assertEquals("IDLE", f.state()); assertEquals(0, f.engine.bankWalks);
            assertTrue(f.withdrawals.isEmpty()); assertTrue(f.lighting.isEmpty());
        }
    }

    @Test void aCommittedCaveRouteBanksForItsOwnedLightAndRetainsItsFinalGoal() throws Exception {
        try (Fixture f = new Fixture()) {
            f.bankCounts.put(ItemID.WINT_TORCH, 1);
            assertTrue(f.prepare());
            assertEquals("WALKING_TO_BANK", f.state()); assertEquals(1, f.engine.bankWalks);
            assertEquals(JUNA, f.walker.getLogicalDestination());
            assertTrue(f.withdrawals.isEmpty()); assertTrue(f.lighting.isEmpty());
        }
    }

    @Test void bankedLitLightWaitsForWithdrawalEpochAndClosureBeforeResuming() throws Exception {
        try (Fixture f = new Fixture()) {
            f.bankOpen.set(true); f.bankCounts.put(ItemID.WINT_TORCH, 1);
            assertTrue(f.prepare()); f.bankTick(); f.bankTick();
            assertEquals(List.of(ItemID.WINT_TORCH), f.withdrawals);
            f.carried.put(ItemID.WINT_TORCH, 1);
            for (int i = 0; i < 3; i++) { f.tick.incrementAndGet(); f.bankTick(); }
            assertEquals("WITHDRAWING", f.state()); assertEquals(1, f.withdrawals.size());
            f.epoch.incrementAndGet(); f.bankTick();
            assertEquals("CLOSING_BANK", f.state()); assertEquals(0, f.engine.resumes);
            f.bankTick(); assertEquals(0, f.engine.resumes);
            f.bankOpen.set(false); f.bankTick();
            assertEquals("LIGHTING_CAVE", f.state()); assertEquals(0, f.engine.resumes);
            f.bankTick(); assertEquals("IDLE", f.state()); assertEquals(1, f.engine.resumes);
            assertEquals(JUNA, f.engine.destination); assertTrue(f.lighting.isEmpty());
        }
    }

    @Test void aBankedCandleAndTinderboxAreLitOnlyAfterClosureAndNeedAnActualLitItem() throws Exception {
        try (Fixture f = new Fixture()) {
            f.bankOpen.set(true); f.bankCounts.put(ItemID.UNLIT_CANDLE, 1);
            f.bankCounts.put(ItemID.TINDERBOX, 1); f.level.set(1);
            assertTrue(f.prepare()); f.bankTick();
            for (int item : new ArrayList<>(((Map<Integer,Integer>) get(f.coordinator, "pendingWithdrawals")).keySet())) {
                f.bankTick(); assertEquals(item, f.withdrawals.get(f.withdrawals.size() - 1));
                assertTrue(f.lighting.isEmpty());
                f.carried.put(item, 1); f.epoch.incrementAndGet();
            }
            f.bankTick(); assertEquals("CLOSING_BANK", f.state());
            f.bankOpen.set(false); f.bankTick(); f.bankTick();
            assertEquals(List.of(ItemID.UNLIT_CANDLE), f.lighting);
            for (int i = 0; i < 4; i++) { f.tick.incrementAndGet(); f.bankTick(); }
            assertEquals(0, f.engine.resumes); assertEquals(1, f.lighting.size());
            f.carried.remove(ItemID.UNLIT_CANDLE); f.bankTick();
            assertEquals(0, f.engine.resumes); assertEquals(EfficientWalker.Status.WALKING, f.walker.getStatus());
            f.carried.put(ItemID.LIT_CANDLE, 1); f.bankTick();
            assertEquals(1, f.engine.resumes); assertEquals(JUNA, f.engine.destination);
        }
    }

    @Test void carriedUnlitLightWorksWithoutBankAuthorityAndNoSourceIsEquipped() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.config.useItemsFromBank()).thenReturn(false);
            f.carried.put(ItemID.CANDLE_LANTERN_UNLIT, 1); f.carried.put(ItemID.TINDERBOX, 1); f.level.set(4);
            assertTrue(f.prepare()); assertEquals("LIGHTING_CAVE", f.state()); f.bankTick();
            assertEquals(List.of(ItemID.CANDLE_LANTERN_UNLIT), f.lighting);
            assertEquals(0, f.engine.bankWalks); assertTrue(f.withdrawals.isEmpty());
            f.inventory.verify(() -> Rs2Inventory.equip(anyInt()), never());
        }
    }

    @Test void aLostOrUnavailableLightStopsBeforeAnyDepartureOrSpeculativeBankJourney() throws Exception {
        try (Fixture f = new Fixture()) {
            f.carried.put(ItemID.BULLSEYE_LANTERN_EMPTY, 1);
            assertTrue(f.prepare());
            assertEquals(EfficientWalker.Status.BLOCKED, f.walker.getStatus());
            assertTrue(f.walker.getFailureReason().contains("light"));
            assertEquals(0, f.engine.bankWalks); assertEquals(0, f.engine.resumes); assertTrue(f.lighting.isEmpty());
        }
    }

    @Test void acceptedLightingWithoutAReceiptStopsAtThreeAttempts() throws Exception {
        try (Fixture f = new Fixture()) {
            f.carried.put(ItemID.UNLIT_CANDLE, 1); f.carried.put(ItemID.TINDERBOX, 1);
            assertTrue(f.prepare());
            for (int i = 0; i < 4; i++) { f.bankTick(); f.tick.addAndGet(6); }
            assertEquals(3, f.lighting.size()); assertEquals(0, f.engine.resumes);
            assertEquals(EfficientWalker.Status.BLOCKED, f.walker.getStatus());
            assertTrue(f.walker.getFailureReason().contains("three attempts"));
        }
    }

    @Test void pauseKeepsTheLightingBudgetAndAChangedWorldRevokesTheInput() throws Exception {
        try (Fixture f = new Fixture()) {
            f.carried.put(ItemID.UNLIT_CANDLE, 1); f.carried.put(ItemID.TINDERBOX, 1);
            assertTrue(f.prepare()); f.bankTick();
            call(f.coordinator, "holdPreparationBudget", new Class<?>[]{boolean.class}, true);
            f.tick.addAndGet(600);
            call(f.coordinator, "holdPreparationBudget", new Class<?>[]{boolean.class}, false);
            f.bankTick(); assertEquals(1, f.lighting.size());
            when(f.client.getWorld()).thenReturn(302); f.bankTick();
            assertEquals(EfficientWalker.Status.BLOCKED, f.walker.getStatus()); assertEquals(1, f.lighting.size());
            assertEquals(0, f.engine.resumes);
        }
    }

    @Test void aHumanHoldOrHostHandoffPreventsLightingEvenAfterTheWorkerWasQueued() throws Exception {
        try (Fixture f = new Fixture()) {
            f.carried.put(ItemID.UNLIT_CANDLE, 1); f.carried.put(ItemID.TINDERBOX, 1);
            assertTrue(f.prepare());
            f.input.when(InputArbiter::isHuman).thenReturn(true); f.bankTick();
            assertTrue(f.lighting.isEmpty());
            f.input.when(InputArbiter::isHuman).thenReturn(false);
            f.host.when(DroSetupMouse::isHostInputAllowed).thenReturn(false); f.tick.addAndGet(6); f.bankTick();
            assertTrue(f.lighting.isEmpty()); assertEquals(0, f.engine.resumes);
        }
    }

    @Test void aWorldChangeBetweenBankModeAndWithdrawalPreventsTheWithdrawal() throws Exception {
        try (Fixture f = new Fixture()) {
            f.bankOpen.set(true); f.bankCounts.put(ItemID.WINT_TORCH, 1);
            assertTrue(f.prepare()); f.bankTick();
            f.bank.when(Rs2Bank::setWithdrawAsItem).thenAnswer(i -> { when(f.client.getWorld()).thenReturn(302); return true; });
            f.bankTick(); assertTrue(f.withdrawals.isEmpty());
            f.bankTick(); assertEquals(EfficientWalker.Status.BLOCKED, f.walker.getStatus());
            assertEquals(0, f.engine.resumes);
        }
    }

    @Test void thePlannerExposesTheCaveJumpButTheGamesLandingHasNoLightLeg() throws Exception {
        try (Fixture f = new Fixture()) {
            ClassLoader loader = (ClassLoader) get(f.walker, "engineLoader");
            String shadow = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
            Method catalogue = WalkerTestReflection.declaredMethod(Class.forName(shadow + ".TransportCatalog", true, loader), "loadAllTransports");
            catalogue.setAccessible(true); List<Object> edges = new ArrayList<>((List<?>) catalogue.invoke(null));
            Object bridge = get(f.walker, "networkTransportSupport");
            Method rows = WalkerTestReflection.declaredMethod(bridge.getClass(), "loadSharedCatalog"); rows.setAccessible(true);
            Method convert = WalkerTestReflection.declaredMethod(bridge.getClass(), "buildSharedCatalogTransport",
                    net.runelite.client.plugins.microbot.shortestpath.Transport.class, WorldPoint.class);
            convert.setAccessible(true);
            for (var row : (List<net.runelite.client.plugins.microbot.shortestpath.Transport>) rows.invoke(bridge)) {
                Object edge = convert.invoke(bridge, row, OUTSIDE); if (edge != null) edges.add(edge);
            }
            Class<?> planner = Class.forName(shadow + ".CollisionRoutePlanner", true, loader);
            Method build = WalkerTestReflection.declaredMethod(planner, "create", List.class, java.util.function.Predicate.class); build.setAccessible(true);
            Object graph = build.invoke(null, edges, (java.util.function.Predicate<Object>) edge -> true);
            Method plan = WalkerTestReflection.declaredMethod(planner, "planRoute", WorldPoint.class, Collection.class, Set.class, Set.class, Set.class);
            plan.setAccessible(true);
            for (WorldPoint start : List.of(OUTSIDE, new WorldPoint(3245, 9500, 2))) {
                Object result = plan.invoke(graph, start, List.of(JUNA), Set.of(), Set.of(), new HashSet<>(edges));
                List<WorldPoint> path = (List<WorldPoint>) get(result, "path");
                assertFalse(path.isEmpty()); assertEquals(JUNA, path.get(path.size() - 1));
                assertEquals(start.equals(OUTSIDE), LumbridgeSwampCaveAccess.routeRequiresLight(path));
            }
        }
    }

    public static class Engine {
        List<WorldPoint> path = List.of(HOME, OUTSIDE, INSIDE, JUNA);
        WorldPoint destination = JUNA;
        EfficientWalker.Status status = EfficientWalker.Status.WALKING;
        int bankWalks, resumes;
        public EfficientWalker.Status getStatus() { return status; }
        public EfficientWalker.PlanningFailure getPlanningFailure() { return EfficientWalker.PlanningFailure.NONE; }
        public WorldPoint getDestination() { return destination; }
        public List<WorldPoint> getRemainingRoute() { return path; }
        public void cancel() { status = EfficientWalker.Status.IDLE; }
        public void shutdown() { }
        public boolean walkToNearestBank() { bankWalks++; destination = HOME; status = EfficientWalker.Status.WALKING; return true; }
        public boolean startRouteTo(WorldPoint target) { resumes++; destination = target; status = EfficientWalker.Status.WALKING; return true; }
        public boolean clickWidgetAction(Widget widget, String action) { return true; }
    }

    private static final class Fixture implements AutoCloseable {
        // Callback arguments and inline mock handlers can retain an entire isolated walker.
        // These mocks provide state; only the inventory static scope is interaction-verified.
        private final List<Object> ownedMocks = new ArrayList<>();
        private final java.util.Deque<MockedStatic<?>> staticMocks = new ArrayDeque<>();
        private final Map<Long, Rs2ItemModel> itemModels = new HashMap<>();
        private boolean closed;
        Client client; EfficientWalkerConfig config;
        final AtomicInteger tick = new AtomicInteger(1), epoch = new AtomicInteger(1), level = new AtomicInteger(99);
        final AtomicBoolean bankOpen = new AtomicBoolean(), onClient = new AtomicBoolean(true);
        final Map<Integer,Integer> carried = new LinkedHashMap<>(), bankCounts = new HashMap<>();
        final List<Integer> withdrawals = new ArrayList<>(), lighting = new ArrayList<>();
        MockedStatic<Microbot> dro;
        MockedStatic<Rs2Player> player;
        MockedStatic<Rs2Inventory> inventory;
        MockedStatic<TravelEquipment> equipment;
        MockedStatic<Rs2Bank> bank;
        MockedStatic<InputArbiter> input;
        MockedStatic<DroSetupMouse> host;
        final Engine engine = new Engine();
        EfficientWalker walker; Object coordinator, realEngine; Class<?> realEngineClass;
        private Field combatPolicyConfig, mouseConfig;
        private Object previousCombatPolicyConfig;

        Fixture() throws Exception {
            try {
                client = mockOwned(Client.class); config = mockOwned(EfficientWalkerConfig.class);
                dro = trackStatic(mockStatic(Microbot.class, withSettings().stubOnly()));
                player = trackStatic(mockStatic(Rs2Player.class, withSettings().stubOnly()));
                inventory = trackStatic(mockStatic(Rs2Inventory.class));
                equipment = trackStatic(mockStatic(TravelEquipment.class, withSettings().stubOnly()));
                bank = trackStatic(mockStatic(Rs2Bank.class, withSettings().stubOnly()));
                input = trackStatic(mockStatic(InputArbiter.class, withSettings().stubOnly()));
                host = trackStatic(mockStatic(DroSetupMouse.class, withSettings().stubOnly()));
                Microbot.pauseAllScripts.set(false);
                when(client.isClientThread()).thenAnswer(i -> onClient.get());
                when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getWorld()).thenReturn(301);
                when(client.getTickCount()).thenAnswer(i -> tick.get()); when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
                Player actor = mockOwned(Player.class); when(actor.getWorldLocation()).thenReturn(HOME); when(client.getLocalPlayer()).thenReturn(actor);
                dro.when(Microbot::getClient).thenReturn(client); ClientThread thread = mockOwned(ClientThread.class);
                when(thread.invoke(any(Supplier.class))).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
                when(thread.runOnClientThreadOptional(any())).thenAnswer(i -> {
                    boolean before = onClient.getAndSet(true);
                    try { return Optional.ofNullable(((Callable<?>) i.getArgument(0)).call()); }
                    finally { onClient.set(before); }
                }); dro.when(Microbot::getClientThread).thenReturn(thread);
                player.when(Rs2Player::getWorldLocation).thenReturn(HOME);
                player.when(() -> Rs2Player.getRealSkillLevel(Skill.FIREMAKING)).thenAnswer(i -> level.get());
                player.when(() -> Rs2Player.getQuestState(any())).thenReturn(QuestState.FINISHED);
                equipment.when(TravelEquipment::wornItems).thenReturn(Map.of());
                inventory.when(() -> Rs2Inventory.itemQuantity(anyInt())).thenAnswer(i -> carried.getOrDefault(i.getArgument(0), 0));
                inventory.when(Rs2Inventory::all).thenAnswer(i -> {
                    List<Rs2ItemModel> rows = new ArrayList<>();
                    carried.forEach((id, count) -> rows.add(itemModel(id, count)));
                    return rows;
                });
                inventory.when(() -> Rs2Inventory.combine(eq(ItemID.TINDERBOX), anyInt())).thenAnswer(i -> {
                    assertFalse(onClient.get(), "Lighting input must remain on the existing worker"); lighting.add(i.getArgument(1)); return true;
                });
                ItemContainer container = mockOwned(ItemContainer.class); when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
                when(container.getItems()).thenAnswer(i -> carried.entrySet().stream().map(e -> new Item(e.getKey(), e.getValue())).toArray(Item[]::new));
                when(client.getItemDefinition(anyInt())).thenReturn(mockOwned(ItemComposition.class));
                bank.when(Rs2Bank::isOpen).thenAnswer(i -> bankOpen.get()); bank.when(Rs2Bank::getBankLiveEpoch).thenAnswer(i -> epoch.get());
                bank.when(Rs2Bank::bankItems).thenReturn(List.of()); bank.when(() -> Rs2Bank.count(anyInt())).thenAnswer(i -> bankCounts.getOrDefault(i.getArgument(0), 0));
                bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(true);
                bank.when(() -> Rs2Bank.withdrawOne(anyInt())).thenAnswer(i -> { withdrawals.add(i.getArgument(0)); return true; });
                host.when(DroSetupMouse::isHostInputAllowed).thenReturn(true);
                when(config.useItemsFromBank()).thenReturn(true); when(config.useTeleportations()).thenReturn(true);
                capturePolicyOwnership();
                walker = new EfficientWalker(client, config, TestRouteSubmissions.immediate()); coordinator = get(walker, "bankTeleportBootstrap");
                realEngine = get(walker, "engine"); realEngineClass = (Class<?>) get(walker, "engineClass");
                ((ExecutorService) get(coordinator, "bankExecutor")).shutdownNow();
                ExecutorService worker = mockOwned(ExecutorService.class);
                when(worker.submit(any(Callable.class))).thenAnswer(i -> {
                    boolean before = onClient.getAndSet(false);
                    try { FutureTask<Object> task = new FutureTask<>((Callable<Object>) i.getArgument(0)); task.run(); return task; }
                    finally { onClient.set(before); }
                });
                set(coordinator, "bankExecutor", worker); set(walker, "engine", engine); set(walker, "engineClass", Engine.class);
                // Explicit opt-in is normally established by the public walkTo entry point.
                set(walker, "bankSuppliesAllowed", true);
            } catch (Exception | Error failure) {
                try { close(); } catch (Throwable cleanup) { if (failure != cleanup) failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private <T> T mockOwned(Class<T> type) {
            T value = mock(type, withSettings().stubOnly());
            ownedMocks.add(value);
            return value;
        }

        private <T> MockedStatic<T> trackStatic(MockedStatic<T> scope) {
            staticMocks.push(scope);
            return scope;
        }

        private Rs2ItemModel itemModel(int id, int quantity) {
            // Keep each returned inventory snapshot immutable if carried changes later.
            long key = ((long) id << 32) | (quantity & 0xffffffffL);
            return itemModels.computeIfAbsent(key, ignored -> {
                Rs2ItemModel row = mockOwned(Rs2ItemModel.class);
                when(row.getId()).thenReturn(id);
                when(row.getQuantity()).thenReturn(quantity);
                return row;
            });
        }

        private void capturePolicyOwnership() throws Exception {
            combatPolicyConfig = WalkerTestReflection.declaredField(TravelCombatPolicy.class, "config");
            combatPolicyConfig.setAccessible(true);
            previousCombatPolicyConfig = combatPolicyConfig.get(null);
            mouseConfig = WalkerTestReflection.declaredField(WalkerMouseControl.class, "config");
            mouseConfig.setAccessible(true);
        }

        private void restoreCombatPolicy() throws Exception {
            if (combatPolicyConfig != null && combatPolicyConfig.get(null) == config)
                combatPolicyConfig.set(null, previousCombatPolicyConfig);
        }

        private void shutdownOwnedMouse() throws Exception {
            synchronized (WalkerMouseControl.class) {
                if (mouseConfig != null && mouseConfig.get(null) == config)
                    WalkerMouseControl.shutdown();
            }
        }

        boolean prepare() throws Exception { return (Boolean) call(walker, "prepareSelectedCaveLight", new Class<?>[0]); }
        void bankTick() throws Exception { call(coordinator, "beforeEngineTick", new Class<?>[0]); }
        String state() throws Exception { return get(coordinator, "state").toString(); }

        public void close() throws Exception {
            if (closed) return;
            closed = true;
            Throwable failure = null;
            try {
                if (walker != null) {
                    // The adapters still own this engine, even while the facade uses our fake.
                    if (realEngine != null) failure = cleanup(failure, () -> set(walker, "engine", realEngine));
                    if (realEngineClass != null) failure = cleanup(failure, () -> set(walker, "engineClass", realEngineClass));
                    failure = cleanup(failure, walker::shutdown);
                }
            } finally {
                // A failed walker constructor/shutdown can leave a static policy owning this mock.
                failure = cleanup(failure, this::restoreCombatPolicy);
                failure = cleanup(failure, this::shutdownOwnedMouse);
                while (!staticMocks.isEmpty()) {
                    MockedStatic<?> scope = staticMocks.pop();
                    failure = cleanup(failure, scope::close);
                }
                if (!ownedMocks.isEmpty())
                    failure = cleanup(failure, () -> clearInvocations(ownedMocks.toArray()));
                for (Object owned : ownedMocks)
                    failure = cleanup(failure, () -> framework().clearInlineMock(owned));
                ownedMocks.clear(); itemModels.clear();
                Microbot.pauseAllScripts.set(false);
            }
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof Exception) throw (Exception) failure;
            if (failure != null) throw new IllegalStateException("Fixture cleanup failed", failure);
        }

        @FunctionalInterface private interface Cleanup { void run() throws Exception; }
        private static Throwable cleanup(Throwable failure, Cleanup action) {
            try { action.run(); }
            catch (Throwable next) {
                if (failure == null) return next;
                if (failure != next) failure.addSuppressed(next);
            }
            return failure;
        }

    }

    private static Object get(Object object, String name) throws Exception { Field f = WalkerTestReflection.declaredField(object.getClass(), name); f.setAccessible(true); return f.get(object); }
    private static void set(Object object, String name, Object value) throws Exception { Field f = WalkerTestReflection.declaredField(object.getClass(), name); f.setAccessible(true); f.set(object, value); }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WalkerTestReflection.declaredMethod(object.getClass(), name, types); m.setAccessible(true); return m.invoke(object, args);
    }
}
