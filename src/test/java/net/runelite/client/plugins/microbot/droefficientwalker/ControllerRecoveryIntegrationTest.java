package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.Hull;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static net.runelite.client.plugins.microbot.droefficientwalker.EfficientSailingController.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controller transitions with delayed game evidence, rather than only pure policy fixtures. */
public class ControllerRecoveryIntegrationTest {
    private static final WorldPoint DOCK = new WorldPoint(2685, 3162, 0);

    static void set(Object object, String name, Object value) throws Exception {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true);
        field.set(object, value);
    }

    static Object get(Object object, String name) throws Exception {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true);
        return field.get(object);
    }

    static void call(Object object, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = WalkerTestReflection.declaredMethod(object.getClass(), name, types);
        method.setAccessible(true);
        try {
            method.invoke(object, arguments);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception) throw (Exception) ex.getCause();
            throw ex;
        }
    }

    static final class Fixture implements AutoCloseable {
        final AtomicLong now = new AtomicLong(1000);
        final EfficientSailingConfig config = mock(EfficientSailingConfig.class, CALLS_REAL_METHODS);
        final PluginManager plugins = mock(PluginManager.class);
        final MicrobotSailingFacade io = mock(MicrobotSailingFacade.class);
        final SailingWalkerJourney walker = mock(SailingWalkerJourney.class);
        final EfficientSailingController controller;
        final MockedStatic<MicrobotSailingFacade> facade;

        Fixture() throws Exception {
            facade = mockStatic(MicrobotSailingFacade.class);
            facade.when(MicrobotSailingFacade::now).thenAnswer(invocation -> now.get());
            facade.when(MicrobotSailingFacade::humanOrPaused).thenReturn(false);
            when(config.persistAtlas()).thenReturn(false);
            when(config.useReferenceChart()).thenReturn(false); // Local-chart transitions are synchronous in this fixture.
            when(plugins.getPlugins()).thenReturn(Collections.emptyList());
            controller = new EfficientSailingController(mock(Client.class), config, plugins, mock(EfficientWalker.class));
            set(controller, "io", io);
            set(controller, "walker", walker);
            set(controller, "log", mock(VoyageLog.class));
            when(io.ui()).thenReturn(new MicrobotSailingFacade.UiState(Collections.emptyList(), false, false));
            set(controller, "started", now.get());
            set(controller, "phaseStarted", now.get());
        }

        void status(EfficientSailingController.Status status) throws Exception {
            set(controller, "status", status);
            set(controller, "phaseStarted", now.get());
            set(controller, "nextAction", 0L);
        }

        EfficientSailingController.Status status() throws Exception {
            return (EfficientSailingController.Status) get(controller, "status");
        }

        void tick(String method, SailingSnapshot snapshot, long time) throws Exception {
            now.set(time);
            when(io.latest()).thenReturn(snapshot);
            call(controller, method, new Class<?>[]{SailingSnapshot.class, long.class}, snapshot, time);
        }

        void tabletRecovery() throws Exception {
            set(controller, "needRecovery", true);
            when(config.useSummonBoatTablet()).thenReturn(true);
            when(config.allowCargoLoss()).thenReturn(true);
            when(config.boatName()).thenReturn("Giant Mind");
        }

        @Override public void close() throws Exception {
            // No scheduler is started; shut down allocated executors without any game cleanup.
            ((ExecutorService) get(controller, "control")).shutdownNow();
            ((ExecutorService) get(controller, "planning")).shutdownNow();
            ((ExecutorService) get(controller, "seaPlanning")).shutdownNow();
            facade.close();
        }
    }

    private static SailingSnapshot bank(int tick, int coins, int tablets) {
        return snapshot(tick, DOCK, true, coins, tablets);
    }

    private static SailingSnapshot land(int tick, WorldPoint player) {
        return snapshot(tick, player, false, 5000, 1);
    }

    private static SailingSnapshot snapshot(int tick, WorldPoint player, boolean bank, int coins, int tablets) {
        return new SailingSnapshot(1000, tick, 99, 99, coins, 20, 100000,
                0, -1, true, false, false, false, bank, false, player,
                null, "none", new Hull(3, 8, 0), null, Collections.emptyList(),
                -1, null, tablets, 10);
    }

    private static PortCatalog.Candidate khazard() {
        return new PortCatalog.Candidate(PortCatalog.byKey("PORT_KHAZARD"), DOCK,
                20, 20, "fixture dock", false);
    }

    @Test public void consumedTabletWithoutSelectionWindowReturnsToGangplankOnlyAfterFreshEvidence() throws Exception {
        try (Fixture f = new Fixture()) {
            f.tabletRecovery(); f.status(SUMMONING_BOAT);
            PortCatalog.Candidate port = khazard(); set(f.controller, "selected", port);
            when(f.io.atDock(port.port.gangplankId)).thenReturn(true);
            when(f.io.breakSummonTablet(port.port.gangplankId)).thenReturn(true);
            f.tick("tickSummon", snapshot(10, DOCK, false, 5000, 1), 1000);
            f.tick("tickSummon", snapshot(10, DOCK, false, 5000, 0), 2500);
            assertEquals(SUMMONING_BOAT, f.status());
            f.tick("tickSummon", snapshot(11, DOCK, false, 5000, 0), 4000);
            assertEquals(SUMMONING_BOAT, f.status());
            f.tick("tickSummon", snapshot(12, DOCK, false, 5000, 0), 5000);
            assertEquals(SUMMONING_BOAT, f.status());
            f.tick("tickSummon", snapshot(13, DOCK, false, 5000, 0), 6000);
            assertEquals(SUMMONING_BOAT, f.status());
            f.tick("tickSummon", snapshot(14, DOCK, false, 5000, 0), 7000);
            assertEquals(AT_PORT, f.status());
            assertEquals(true, get(f.controller, "recoveryFinished"));
            when(f.io.gangplankPoint(port.port.gangplankId)).thenReturn(DOCK);
            when(f.io.board(port.port.gangplankId)).thenReturn(true);
            f.tick("tickDock", snapshot(15, DOCK, false, 5000, 0), 8000);
            assertEquals(BOARDING, f.status());
            verify(f.io, times(1)).breakSummonTablet(anyInt());
            verify(f.io, times(1)).board(port.port.gangplankId);
        }
    }

    @Test public void bankReturnRechecksLoadedDockAndSkipsTransportSupplyBanking() throws Exception {
        try (Fixture f = new Fixture()) {
            f.status(BANKING); set(f.controller, "selected", khazard());
            set(f.controller, "needRecovery", true);
            when(f.config.useSummonBoatTablet()).thenReturn(true);
            WorldPoint bank = DOCK.dy(-25), plank = DOCK, approach = DOCK.dy(-1);
            when(f.io.gangplankPoint(anyInt())).thenReturn(plank);
            when(f.walker.dockApproach(bank, plank)).thenReturn(approach);
            when(f.walker.walkToPreparedLocal(approach)).thenReturn(true);
            f.tick("tickBank", snapshot(1, bank, false, 5000, 1), 2000);
            assertEquals(WALKING_TO_PORT, f.status());
            verify(f.walker).walkToPreparedLocal(approach);
            verify(f.walker, never()).walkTo(any());
            f.status(AT_PORT);
            f.tick("tickDock", land(2, approach), 3000);
            assertEquals(NEEDS_RECOVERY_SETTINGS, f.status());
            verify(f.io, never()).board(anyInt());
        }
    }

    @Test public void delayedTabletWithdrawalEvenWithFalseHelperReturnNeverDispatchesTwice() throws Exception {
        try (Fixture f = new Fixture()) {
            f.tabletRecovery();
            f.status(BANKING);
            when(f.io.withdrawSummonTablet()).thenReturn(false);
            f.tick("tickBank", bank(1, 5000, 0), 1000);
            f.tick("tickBank", bank(2, 5000, 0), 3000);
            f.tick("tickBank", bank(3, 5000, 0), 8000);
            verify(f.io, times(1)).withdrawSummonTablet();
            verify(f.io, never()).withdrawCoins(anyInt());
            assertEquals(BANKING, f.status());
            f.tick("tickBank", bank(4, 5000, 0), 11000);
            assertEquals(STOPPING, f.status());
            assertTrue(((String) get(f.controller, "failure")).contains("Withdrawal not confirmed"));
            verify(f.io, times(1)).withdrawSummonTablet();
        }
    }

    @Test public void freshTabletAcknowledgementAllowsOnlyTheNextMissingCoinWithdrawal() throws Exception {
        try (Fixture f = new Fixture()) {
            f.tabletRecovery();
            f.status(BANKING);
            f.tick("tickBank", bank(1, 1000, 0), 1000);
            // A copied count from the dispatch tick is insufficient acknowledgement.
            f.tick("tickBank", bank(1, 1000, 1), 3000);
            verify(f.io, never()).withdrawCoins(anyInt());
            f.tick("tickBank", bank(2, 1000, 1), 4000);
            verify(f.io, times(1)).withdrawSummonTablet();
            verify(f.io, times(1)).withdrawCoins(4000);
            f.tick("tickBank", bank(3, 1000, 1), 6000);
            verify(f.io, times(1)).withdrawCoins(anyInt());
            f.tick("tickBank", bank(4, 5000, 1), 7000);
            verify(f.io, times(1)).closeBank();
            verify(f.walker, never()).walkToBank();
        }
    }

    @Test public void elsewhereBoardingMessageKeepsKhazardAsTheTabletRecoveryDock() throws Exception {
        try (Fixture f = new Fixture()) {
            f.tabletRecovery();
            f.status(BOARDING);
            PortCatalog.Candidate selected = khazard();
            set(f.controller, "selected", selected);
            set(f.controller, "boardingBeforeRecovery", true);
            set(f.controller, "localBoarding", true);
            f.controller.onGameMessage("You cannot board your boat as it is currently docked at the Summer Shore.");
            f.tick("tickBoarding", land(1, DOCK), 2000);
            assertEquals("SUMMER_SHORE", get(f.controller, "knownBoatLocation"));
            assertSame(selected, get(f.controller, "selected"));
            assertEquals(NEEDS_RECOVERY_SETTINGS, f.status());
            f.tick("prepareRecovery", land(2, DOCK), 3000);
            assertEquals(SUMMONING_BOAT, f.status());
            assertSame(selected, get(f.controller, "selected"));
            verify(f.walker, never()).walkTo(any());
            verify(f.walker, never()).estimate(any(), any(), any(), any());
        }
    }

    @Test public void missingGangplankAfterSummonCannotReselectAnotherPort() throws Exception {
        try (Fixture f = new Fixture()) {
            f.tabletRecovery();
            f.status(AT_PORT);
            PortCatalog.Candidate selected = khazard();
            PortCatalog.Candidate other = new PortCatalog.Candidate(PortCatalog.byKey("SUMMER_SHORE"), DOCK.dx(100),
                    30, 400, "other fixture", false);
            set(f.controller, "selected", selected);
            set(f.controller, "candidates", Arrays.asList(selected, other));
            set(f.controller, "recoveryFinished", true);
            set(f.controller, "summonIssued", true);
            f.tick("tickDock", land(1, DOCK), 6000);
            assertEquals(AT_PORT, f.status());
            f.tick("tickDock", land(2, DOCK), 14000);
            assertEquals(STOPPING, f.status());
            assertSame(selected, get(f.controller, "selected"));
            assertEquals(0, get(f.controller, "portIndex"));
            verify(f.walker, never()).walkTo(any());
            verify(f.io, never()).breakSummonTablet(anyInt());
        }
    }

    @Test public void issuedSummonWaitsForExactSelectionAndEvidenceWithoutAnotherTablet() throws Exception {
        try (Fixture f = new Fixture()) {
            f.tabletRecovery();
            f.status(SUMMONING_BOAT);
            PortCatalog.Candidate selected = khazard();
            set(f.controller, "selected", selected);
            when(f.io.atDock(selected.port.gangplankId)).thenReturn(true);
            when(f.io.breakSummonTablet(selected.port.gangplankId)).thenReturn(true);
            f.tick("tickSummon", land(1, DOCK), 1000);
            f.tick("tickSummon", land(2, DOCK), 3000);
            f.tick("tickSummon", land(3, DOCK), 9000);
            assertEquals(SUMMONING_BOAT, f.status());
            verify(f.io, times(1)).breakSummonTablet(anyInt());
            f.tick("tickSummon", land(4, DOCK), 22000);
            assertEquals(STOPPING, f.status());
            assertTrue(((String) get(f.controller, "failure")).contains("no repeat tablet"));
            verify(f.io, times(1)).breakSummonTablet(anyInt());
            verify(f.walker, never()).walkTo(any());
        }
    }

    @Test public void walkerArrivalClearingDestinationCompletesBeforeOwnershipLossCheck() throws Exception {
        try (Fixture f = new Fixture()) {
            f.status(APPROACHING_GANGPLANK);
            set(f.controller, "walkCompletion", AT_PORT);
            SailingWalkerJourney.JourneyState arrival = new SailingWalkerJourney.JourneyState("ARRIVED", "Done", "NONE", null);
            when(f.walker.observe()).thenReturn(arrival);
            when(f.walker.arrivedOwned(arrival, DOCK)).thenReturn(true);
            // Completed routes clear destination; this stale ownership result must not win.
            when(f.walker.ownershipIntact(arrival)).thenReturn(false);
            f.tick("tickWalk", land(1, DOCK), 2000);
            assertEquals(AT_PORT, f.status());
            verify(f.walker).cancelOwned();
            verify(f.walker, never()).ownershipIntact(any());
            verify(f.walker, never()).userTookOver();
        }
    }

    @Test public void walkerArrivalWaitsForNextPlayerSnapshotWithoutYieldingTheRequest() throws Exception {
        try (Fixture f = new Fixture()) {
            f.status(APPROACHING_GANGPLANK);
            set(f.controller, "walkCompletion", AT_PORT);
            SailingWalkerJourney.JourneyState arrival = new SailingWalkerJourney.JourneyState("ARRIVED", "Done", "NONE", null);
            WorldPoint stalePlayer = DOCK.dx(-20);
            when(f.walker.observe()).thenReturn(arrival);
            when(f.walker.ownershipIntact(arrival)).thenReturn(true);
            when(f.walker.arrivedOwned(arrival, DOCK)).thenReturn(true);
            f.tick("tickWalk", land(1, stalePlayer), 2000);
            f.tick("tickWalk", land(1, stalePlayer), 3000);
            assertEquals(APPROACHING_GANGPLANK, f.status());
            verify(f.walker, never()).userTookOver();
            verify(f.walker, never()).cancelOwned();
            f.tick("tickWalk", land(2, DOCK), 3500);
            assertEquals(AT_PORT, f.status());
            verify(f.walker).cancelOwned();
        }
    }

    @Test public void idleControllerDoesNotSendInputsEvenWithAnotherEnabledSailer() throws Exception {
        try (Fixture f = new Fixture()) {
            net.runelite.client.plugins.Plugin salvager =
                    mock(net.runelite.client.plugins.Plugin.class);
            when(f.plugins.getPlugins()).thenReturn(Collections.singletonList(salvager));
            when(f.plugins.isPluginEnabled(salvager)).thenReturn(true);
            when(f.io.latest()).thenReturn(land(1, DOCK));
            call(f.controller, "tick", new Class<?>[0]);
            assertEquals(IDLE, f.status());
            verify(f.io, never()).closeMap();
            verify(f.io, never()).navigate();
            verify(f.io, never()).sails(anyBoolean());
            verify(f.io, never()).steer(any(), anyInt());
            verify(f.io, never()).withdrawCoins(anyInt());
            verify(f.io, never()).withdrawSummonTablet();
            verify(f.walker, never()).cancelOwned();
            verify(f.walker, never()).walkTo(any());
            verify(f.plugins, never()).getPlugins();
        }
    }
}
