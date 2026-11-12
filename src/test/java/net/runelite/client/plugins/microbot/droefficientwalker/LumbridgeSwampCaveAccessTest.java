package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportObjectClick;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.DroSetupMouse;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LumbridgeSwampCaveAccessTest {
    private static final WorldPoint OUTSIDE = new WorldPoint(3168, 3172, 0), INSIDE = new WorldPoint(3168, 9572, 0);
    private static final String QUESTION = "The cave is very dark and you don't have a light source<br>"
            + "or a tinderbox. Are you sure you want to go down?";
    // Synthetic interface number: recognition must use the observed panel, not a guessed group ID.
    private static final int GROUP = 1200;

    @Test void onlyTheRecordedSurfaceEntranceRequiresLightAndRechecksItBeforeExecution() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(LumbridgeSwampCaveAccess.isEntrance(f.transport));
            assertFalse(LumbridgeSwampCaveAccess.isEntrance(new Transport(INSIDE, OUTSIDE, "Exit",
                    TransportType.TRANSPORT, true, "Climb-up", "Ladder", 5946)));
            assertFalse(LumbridgeSwampCaveAccess.isEntrance(new Transport(new WorldPoint(3200, 3200, 0), INSIDE,
                    "Other cave", TransportType.TRANSPORT, true, "Climb-down", "Dark hole", 5947)));
            f.lights.when(CaveLightSource::hasLight).thenReturn(false);
            assertEquals(false, call(f.bridge, "sharedRequirementsSatisfiedNow", f.transport));
            assertEquals(false, call(f.bridge, "executeTerminalCatalogTransport", f.transport));
            f.objects.verifyNoInteractions();
            f.micro.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
        }
    }

    @Test void legacyWarningDispatchBindsTheActualYesWidgetAndDoesNotProveLanding() throws Exception {
        try (Fixture f = new Fixture()) {
            var warning = LumbridgeSwampCaveAccess.warning(f.client);
            assertEquals(GROUP, warning.group);
            assertTrue(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            NewMenuEntry sent = f.sent.get();
            assertEquals(MenuAction.WIDGET_TYPE_1, sent.getType());
            assertEquals("Yes", sent.getOption());
            assertEquals(f.yes.getId(), sent.getParam1());
            assertSame(f.yes, sent.getWidget());
            assertNull(sent.getGameObject());
            assertEquals(OUTSIDE, f.here.get());
            assertFalse(f.landed());
        }
    }

    @Test void listenerBasedWarningUsesItsRealParentOperationAndTheAffirmativeTextBounds() {
        try (Fixture f = new Fixture()) {
            when(f.yes.isIf3()).thenReturn(true);
            when(f.yes.getParent()).thenReturn(f.root);
            when(f.root.getActions()).thenReturn(new String[] {"Close", "Yes"});
            var warning = LumbridgeSwampCaveAccess.warning(f.client);
            assertTrue(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            assertEquals(MenuAction.CC_OP, f.sent.get().getType());
            assertEquals(2, f.sent.get().getIdentifier());
            assertSame(f.root, f.sent.get().getWidget());
            assertEquals(f.yes.getBounds(), f.clicked.get());
        }
    }

    @Test void aLoadingOrHiddenPanelAndAnAffirmativeFromAnotherInterfaceCannotBeClicked() {
        try (Fixture f = new Fixture()) {
            when(f.yes.isIf3()).thenReturn(true); // No active operation yet.
            var warning = LumbridgeSwampCaveAccess.warning(f.client);
            assertEquals(GROUP, warning.group);
            assertNull(warning.selection);
            assertFalse(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            when(f.yes.isIf3()).thenReturn(false);
            when(f.yes.getId()).thenReturn((GROUP + 1) << 16 | 4);
            assertNull(LumbridgeSwampCaveAccess.warning(f.client).selection);
            when(f.root.isHidden()).thenReturn(true);
            assertEquals(-1, LumbridgeSwampCaveAccess.warning(f.client).group);
            when(f.client.getWidgetRoots()).thenReturn(null);
            assertNull(LumbridgeSwampCaveAccess.warning(f.client));
            assertNull(f.sent.get());
        }
    }

    @Test void aLostLightChangedWidgetOrUnrelatedWarningRevokesThePreparedConfirmation() {
        try (Fixture f = new Fixture()) {
            var warning = LumbridgeSwampCaveAccess.warning(f.client);
            f.lights.when(CaveLightSource::hasLight).thenReturn(false);
            assertFalse(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            f.lights.when(CaveLightSource::hasLight).thenReturn(true);
            when(f.question.getText()).thenReturn("Do you want to go DEEP into the Wilderness?");
            assertFalse(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            when(f.question.getText()).thenReturn(QUESTION);
            Widget replacement = widget(GROUP, 8, "I'm NOT scared of the dark");
            when(f.root.getChildren()).thenReturn(new Widget[] {f.question, replacement, f.no});
            when(f.root.getDynamicChildren()).thenReturn(null);
            assertFalse(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            assertNull(f.sent.get());
        }
    }

    @Test void pauseHandoffHumanTakeoverAndLoggingOutPreventWarningInput() {
        try (Fixture f = new Fixture();
             var human = mockStatic(InputArbiter.class); var setup = mockStatic(DroSetupMouse.class)) {
            setup.when(DroSetupMouse::isHostInputAllowed).thenReturn(true);
            var warning = LumbridgeSwampCaveAccess.warning(f.client);
            Microbot.pauseAllScripts.set(true);
            try { assertFalse(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning)); }
            finally { Microbot.pauseAllScripts.set(false); }
            // Standalone handoff authority is the existing setup mouse host gate below.
            human.when(InputArbiter::isHuman).thenReturn(true);
            assertFalse(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            human.when(InputArbiter::isHuman).thenReturn(false);
            setup.when(DroSetupMouse::isHostInputAllowed).thenReturn(false);
            assertFalse(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            setup.when(DroSetupMouse::isHostInputAllowed).thenReturn(true);
            when(f.client.getGameState()).thenReturn(GameState.LOADING);
            assertFalse(LumbridgeSwampCaveAccess.accept(f.client, f.transport, warning));
            assertNull(f.sent.get());
        }
    }

    @Test void existingWarningIsReusedAndTheHoleIsNeverClickedThroughIt() throws Exception {
        try (Fixture f = new Fixture()) {
            f.landOnConfirmation.set(true);
            assertEquals(true, call(f.bridge, "executeTerminalCatalogTransport", f.transport));
            assertEquals(INSIDE, f.here.get());
            assertNotNull(f.sent.get());
            f.objects.verifyNoInteractions();
        }
    }

    @Test void aFreshHoleClickThatOpensTheWarningIsNotRepeatedBeforeArrival() throws Exception {
        try (Fixture f = new Fixture(); var gameObjects = mockStatic(SceneObjects.class)) {
            f.warningVisible.set(false);
            TileObject hole = mock(TileObject.class);
            when(hole.getWorldLocation()).thenReturn(OUTSIDE);
            gameObjects.when(() -> SceneObjects.getAll(any(Predicate.class), eq(OUTSIDE), eq(10)))
                    .thenReturn(List.of(hole));
            gameObjects.when(() -> SceneObjects.hasAction(hole, "Climb-down", true)).thenReturn(true);
            f.objects.when(() -> TransportObjectClick.click(hole, "Climb-down")).thenAnswer(call -> {
                f.warningVisible.set(true);
                return true;
            });
            f.landOnConfirmation.set(true);
            assertEquals(true, call(f.bridge, "executeTerminalCatalogTransport", f.transport));
            f.objects.verify(() -> TransportObjectClick.click(hole, "Climb-down"), times(1));
            assertEquals(MenuAction.WIDGET_TYPE_1, f.sent.get().getType());
        }
    }

    @Test void anAcceptedClosingWarningCannotRestartTheHoleWhileTheLandingIsDelayed() throws Exception {
        try (Fixture f = new Fixture()) {
            f.landOnConfirmation.set(true);
            f.landingDelay = 3_000;
            f.closeWarningOnConfirmation = true;
            assertEquals(true, call(f.bridge, "executeTerminalCatalogTransport", f.transport));
            assertEquals(INSIDE, f.here.get());
            f.objects.verifyNoInteractions();
            f.micro.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), times(1));
        }
    }

    @Test void undergroundArrivalRequiresTheLoggedInSettledPlayerAndNeverSurfaceCoordinates() throws Exception {
        try (Fixture f = new Fixture(); var movement = mockStatic(Rs2Player.class)) {
            assertFalse(f.landed());
            f.here.set(INSIDE);
            assertTrue(f.landed());
            movement.when(Rs2Player::isMoving).thenReturn(true);
            assertFalse(f.landed());
            movement.when(Rs2Player::isMoving).thenReturn(false);
            when(f.player.getAnimation()).thenReturn(827);
            assertFalse(f.landed());
            when(f.player.getAnimation()).thenReturn(-1);
            when(f.client.getGameState()).thenReturn(GameState.LOADING);
            assertFalse(f.landed());
        }
    }

    @Test void observedWarningTextAcceptsCaseTagsAndApostrophesWithoutMatchingTheNegativeChoice() {
        assertTrue(LumbridgeSwampCaveAccess.isQuestion(QUESTION));
        assertTrue(LumbridgeSwampCaveAccess.isAffirmative("<col=00ff00>I’m NOT scared of the dark</col>"));
        assertFalse(LumbridgeSwampCaveAccess.isAffirmative("I AM scared of the dark"));
        assertFalse(LumbridgeSwampCaveAccess.isAffirmative("Yes"));
        assertFalse(LumbridgeSwampCaveAccess.isQuestion("It is dark outside; light a fire."));
    }

    private static Object call(Object target, String method, Transport transport) throws Exception {
        Method found = WalkerTestReflection.declaredMethod(target.getClass(), method, Transport.class);
        found.setAccessible(true);
        return found.invoke(target, transport);
    }
    private static Widget widget(int group, int child, String text) {
        Widget widget = mock(Widget.class);
        when(widget.getId()).thenReturn(group << 16 | child);
        when(widget.getIndex()).thenReturn(-1);
        when(widget.getText()).thenReturn(text);
        when(widget.getBounds()).thenReturn(new Rectangle(250, 300, 250, 20));
        return widget;
    }
    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Microbot> micro = mockStatic(Microbot.class);
        final MockedStatic<CaveLightSource> lights = mockStatic(CaveLightSource.class);
        final MockedStatic<Rs2Dialogue> dialogues = mockStatic(Rs2Dialogue.class);
        final MockedStatic<TransportObjectClick> objects = mockStatic(TransportObjectClick.class);
        final Client client = mock(Client.class);
        final Player player = mock(Player.class);
        final ClientThread thread = mock(ClientThread.class);
        final Widget root = widget(GROUP, 0, ""), question = widget(GROUP, 2, QUESTION);
        final Widget yes = widget(GROUP, 4, "I'm NOT scared of the dark"), no = widget(GROUP, 5, "I AM scared of the dark");
        final AtomicReference<WorldPoint> here = new AtomicReference<>(OUTSIDE);
        final AtomicReference<NewMenuEntry> sent = new AtomicReference<>();
        final AtomicReference<Rectangle> clicked = new AtomicReference<>();
        final AtomicBoolean warningVisible = new AtomicBoolean(true), landOnConfirmation = new AtomicBoolean();
        final AtomicBoolean inSnapshot = new AtomicBoolean();
        final AtomicLong confirmedAt = new AtomicLong(-1);
        long landingDelay;
        boolean closeWarningOnConfirmation;
        final Transport transport = new Transport(OUTSIDE, INSIDE, "Lumbridge Swamp Caves", TransportType.TRANSPORT,
                true, "Climb-down", "Dark hole", 5947);
        final NetworkTransportSupport bridge;

        Fixture() {
            micro.when(Microbot::getClient).thenReturn(client);
            micro.when(Microbot::getClientThread).thenReturn(thread);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call -> snapshot((Callable<?>) call.getArgument(0)));
            when(thread.invoke(any(Supplier.class))).thenAnswer(call -> snapshot(() -> ((Supplier<?>) call.getArgument(0)).get()).orElse(null));
            when(client.getLocalPlayer()).thenReturn(player);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(player.getWorldLocation()).thenAnswer(call -> {
                if (confirmedAt.get() >= 0 && System.currentTimeMillis() - confirmedAt.get() >= landingDelay) here.set(INSIDE);
                return here.get();
            });
            when(player.getAnimation()).thenReturn(-1);
            when(root.getChildren()).thenReturn(new Widget[] {question, yes, no});
            when(root.getDynamicChildren()).thenReturn(new Widget[] {question, yes, no, root}); // Sparse/duplicate/cyclic trees.
            when(client.getWidgetRoots()).thenAnswer(call -> warningVisible.get() ? new Widget[] {root} : new Widget[0]);
            lights.when(CaveLightSource::hasLight).thenReturn(true);
            micro.when(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class))).thenAnswer(call -> {
                assertFalse(inSnapshot.get(), "physical input belongs to the transport worker");
                sent.set(call.getArgument(0)); clicked.set(call.getArgument(1));
                if (landOnConfirmation.get()) {
                    confirmedAt.set(System.currentTimeMillis());
                    if (landingDelay == 0) here.set(INSIDE);
                }
                if (closeWarningOnConfirmation) warningVisible.set(false);
                return null;
            });
            bridge = new NetworkTransportSupport(client, mock(EfficientWalkerConfig.class), getClass().getClassLoader(),
                    Object.class, new Object(), "unused", mock(CatalogTeleportSupport.class));
        }
        Optional<?> snapshot(Callable<?> call) throws Exception {
            boolean was = inSnapshot.get(); inSnapshot.set(true);
            try { return Optional.ofNullable(call.call()); } finally { inSnapshot.set(was); }
        }
        boolean landed() throws Exception {
            Method method = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "sharedTransportLanded", WorldPoint.class, Transport.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, here.get(), transport);
        }
        public void close() { bridge.shutdown(); objects.close(); dialogues.close(); lights.close(); micro.close(); }
    }
}
