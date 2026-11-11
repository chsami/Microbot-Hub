package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.Hull;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import org.junit.jupiter.api.Test;
import static net.runelite.client.plugins.microbot.droefficientwalker.ControllerRecoveryIntegrationTest.*;
import static net.runelite.client.plugins.microbot.droefficientwalker.EfficientSailingController.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SailingLostBoatRecoveryTest {
    private static final WorldPoint PEST_DOCK = new WorldPoint(2651, 2677, 0);
    private static final String LOST = "You cannot board your boat as it is lost at sea. You'llneed to speak to a shipwright to recover it. To find ashipwright, just look for the relevant icon on your map.";
    private static final MicrobotSailingFacade.UiState CLOSED = new MicrobotSailingFacade.UiState(Collections.emptyList(), false, false);

    private static MicrobotSailingFacade.UiState message(int group, String text) {
        Widget widget = mock(Widget.class);
        when(widget.getId()).thenReturn(group << 16 | 2);
        when(widget.getText()).thenReturn(text);
        when(widget.getBounds()).thenReturn(new Rectangle(10, 10, 400, 60));
        return new MicrobotSailingFacade.UiState(List.of(new MicrobotSailingFacade.UiElement(widget, text)), true, false);
    }

    private static SailingSnapshot land(int tick, long now, int coins, boolean bank, WorldPoint point, int npc) {
        return new SailingSnapshot(now, tick, 99, 99, coins, 20, 100000, 0, -1,
                true, false, false, false, bank, false, point, null, "none", new Hull(1, 3, 0), null,
                Collections.emptyList(), npc, npc < 0 ? null : point, 0, 0);
    }

    private static void lostAfterSummon(Fixture f) throws Exception {
        f.tabletRecovery(); f.status(BOARDING);
        var port = new PortCatalog.Candidate(PortCatalog.byKey("VOID_KNIGHTS_OUTPOST"), PEST_DOCK, 0, 140, "dock", false);
        set(f.controller, "selected", port); set(f.controller, "summonIssued", true);
        set(f.controller, "recoveryFinished", true); set(f.controller, "goal", new SeaPoint(2633, 2544));
        var denial = message(193, LOST);
        when(f.io.ui()).thenReturn(denial);
        when(f.walker.connect()).thenReturn(true);
        when(f.walker.walkToBank()).thenReturn(true);
        f.tick("tickBoarding", land(1, 2000, 5000, false, PEST_DOCK, -1), 2000);
        assertEquals(NEEDS_RECOVERY_SETTINGS, f.status());
    }

    @Test void liveLostBoatDialogueClosesBeforeShipwrightFundingAndTravel() throws Exception {
        try (var f = new Fixture()) {
            lostAfterSummon(f);
            f.tick("prepareRecovery", land(2, 3000, 5000, false, PEST_DOCK, -1), 3000);
            verify(f.io).continueRecovery(any());
            verify(f.walker, never()).walkToBank();
            when(f.io.ui()).thenReturn(CLOSED);
            f.tick("prepareRecovery", land(3, 4000, 5000, false, PEST_DOCK, -1), 4000);
            assertEquals(WALKING_TO_BANK, f.status());
            verify(f.walker).walkToBank();
            verify(f.io, never()).breakSummonTablet(anyInt());
        }
    }

    @Test void lostRecoveryFundsOnlyMissingCoinsAndSelectsARegisteredShipwrightPort() throws Exception {
        try (var f = new Fixture()) {
            lostAfterSummon(f); when(f.io.ui()).thenReturn(CLOSED);
            f.tick("prepareRecovery", land(2, 3000, 5000, false, PEST_DOCK, -1), 3000);
            f.status(BANKING);
            f.tick("tickBank", land(3, 4000, 5000, true, PEST_DOCK, -1), 4000);
            verify(f.io).withdrawCoins(75000);
            verify(f.io, never()).withdrawSummonTablet();
            f.tick("tickBank", land(4, 6500, 80000, true, PEST_DOCK, -1), 6500);
            verify(f.io).closeBank();
            var ready = land(5, 7500, 80000, false, PEST_DOCK, -1);
            f.tick("tickBank", ready, 7500); assertEquals(COMPARING_PORTS, f.status());
            when(f.walker.walkTo(any())).thenReturn(true);
            when(f.walker.estimateNearestDock(any(), any(), any(), any())).thenAnswer(call -> {
                SeaPoint sea = call.getArgument(0);
                return new SailingWalkerJourney.Estimate(new WorldPoint(sea.x + 1, sea.y, 0), 0, "fixture dock");
            });
            f.tick("tickPortComparison", ready, 8000);
            ((Future<?>) get(f.controller, "portFuture")).get(2, TimeUnit.SECONDS);
            f.tick("tickPortComparison", ready, 9000);
            var selected = (PortCatalog.Candidate) get(f.controller, "selected");
            assertEquals("PORT_KHAZARD", selected.port.key);
            assertTrue(PortCatalog.supportsNpcRecovery(selected.port.key));
            assertEquals(WALKING_TO_PORT, f.status());
            verify(f.io, never()).breakSummonTablet(anyInt());
        }
    }

    @Test void exactBoatRecoveryPaysOnceThenVerifiesBoardingWithoutAnotherTablet() throws Exception {
        try (var f = new Fixture()) {
            lostAfterSummon(f); when(f.io.ui()).thenReturn(CLOSED);
            var port = PortCatalog.byKey("PORT_KHAZARD");
            var dock = new WorldPoint(port.sea.x + 1, port.sea.y, 0);
            set(f.controller, "selected", new PortCatalog.Candidate(port, dock, 0, 0, "shipwright", false));
            when(f.io.beginRecovery(42, true)).thenReturn(true);
            when(f.io.chooseBoat(any(), eq(true))).thenReturn(true);
            when(f.io.payRecovery(any(), eq(75000L), eq(80000), eq(true))).thenReturn(true);
            var s = land(2, 3000, 80000, false, dock, 42);
            f.tick("prepareRecovery", s, 3000); assertEquals(RECOVERING_BOAT, f.status());
            f.tick("tickRecovery", s, 4000);
            f.tick("tickRecovery", s, 5100);
            f.tick("tickRecovery", s, 6200); assertEquals(AT_PORT, f.status());
            when(f.io.gangplankPoint(port.gangplankId)).thenReturn(dock);
            when(f.io.board(port.gangplankId)).thenReturn(true);
            f.tick("tickDock", s, 7300); assertEquals(BOARDING, f.status());
            var denial = message(193, LOST);
            when(f.io.ui()).thenReturn(denial);
            f.tick("tickBoarding", s, 8400); assertEquals(STOPPING, f.status());
            verify(f.io).chooseBoat(any(), eq(true));
            verify(f.io).payRecovery(any(), eq(75000L), eq(80000), eq(true));
            verify(f.io).board(port.gangplankId);
            verify(f.io, never()).breakSummonTablet(anyInt());
            verify(f.io, never()).payRecovery(any(), anyLong(), anyInt());
        }
    }

    @Test void chatHistoryAndGenericBoardingTimeoutDoNotAuthorizeShipwrightRecovery() throws Exception {
        try (var f = new Fixture()) {
            f.tabletRecovery(); f.status(BOARDING);
            var port = PortCatalog.byKey("VOID_KNIGHTS_OUTPOST");
            set(f.controller, "selected", new PortCatalog.Candidate(port, PEST_DOCK, 0, 0, "dock", false));
            set(f.controller, "summonIssued", true); set(f.controller, "recoveryFinished", true);
            var chat = message(162, LOST);
            when(f.io.ui()).thenReturn(chat);
            f.tick("tickBoarding", land(1, 2000, 5000, false, PEST_DOCK, -1), 2000);
            assertEquals(BOARDING, f.status());
            f.tick("tickBoarding", land(2, 12000, 5000, false, PEST_DOCK, -1), 12000);
            assertEquals(STOPPING, f.status());
            verify(f.io, never()).beginRecovery(anyInt());
            verify(f.io, never()).payRecovery(any(), anyLong(), anyInt(), anyBoolean());
        }
    }

    @Test void lostBoatPaymentRetainsExplicitQuoteCapFundsAndInputPermissionChecks() {
        var config = mock(EfficientSailingConfig.class); when(config.useSummonBoatTablet()).thenReturn(true);
        var io = new MicrobotSailingFacade(mock(Client.class), config, ignored -> { });
        io.permit(() -> true);
        var row = message(219, "Yes, pay 1000 coins.");
        var quote = new MicrobotSailingFacade.UiState(row.elements, false, true);
        var genericRow = message(219, "Yes.");
        var generic = new MicrobotSailingFacade.UiState(genericRow.elements, false, true);
        boolean paused = Microbot.pauseAllScripts.get(); Microbot.pauseAllScripts.set(false);
        try (var dro = mockStatic(Microbot.class); var arbiter = mockStatic(InputArbiter.class);
             var dialogue = mockStatic(Rs2Dialogue.class)) {
            dro.when(Microbot::isLoggedIn).thenReturn(true);
            dialogue.when(() -> Rs2Dialogue.clickOption("Yes, pay 1000 coins.", true)).thenReturn(true);
            assertFalse(io.payRecovery(quote, 25000, 30000));
            assertFalse(io.payRecovery(generic, 25000, 30000, true));
            assertThrows(IllegalStateException.class, () -> io.payRecovery(quote, 500, 30000, true));
            assertThrows(IllegalStateException.class, () -> io.payRecovery(quote, 25000, 500, true));
            assertTrue(io.payRecovery(quote, 25000, 30000, true));
            assertFalse(io.payRecovery(quote, 25000, 30000, true));
            io.permit(() -> false);
            assertFalse(io.payRecovery(quote, 25000, 30000, true));
            dialogue.verify(() -> Rs2Dialogue.clickOption("Yes, pay 1000 coins.", true), times(1));
        } finally { Microbot.pauseAllScripts.set(paused); }
    }
}
