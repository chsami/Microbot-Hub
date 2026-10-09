package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GeUiStateTest {
    @Test
    public void unavailableReadDiffersFromASuccessfullyMissingControl() {
        assertThrows(GeUiState.UiUnavailable.class,
            () -> GeUiState.requireRead(Optional.<Optional<Widget>>empty()));
        assertNull(GeUiState.requireRead(Optional.of(Optional.<Widget>empty())));
        assertEquals(Boolean.FALSE,
            GeUiState.requireRead(Optional.of(Optional.of(Boolean.FALSE))));
    }

    @Test
    public void failedOfferReadCannotReportAClosedOfferThroughAGlobalStyleWait() {
        AtomicBoolean reportedClosed = new AtomicBoolean();
        assertThrows(GeUiState.UiUnavailable.class, () -> reportedClosed.set(GeUiState.waitForUi(
            () -> !unknownSnapshot().offerOpen, 100, swallowingWait())));
        assertFalse(reportedClosed.get());
    }

    @Test
    public void genuineClosedSnapshotCompletesTheClosureWait() {
        GeUiState closed = GeUiState.requireRead(Optional.of(Optional.of(
            GeUiState.capture(null, null, null, null, null))));
        assertFalse(closed.exchangeOpen);
        assertFalse(closed.offerOpen);
        assertTrue(GeUiState.waitForUi(() -> !closed.offerOpen, 100, swallowingWait()));
    }

    @Test
    public void unavailableSetupReadPropagatesInsteadOfBecomingAnUnsuccessfulModify() {
        AtomicBoolean modifyRejected = new AtomicBoolean();
        assertThrows(GeUiState.UiUnavailable.class, () -> {
            if (!GeUiState.waitForUi(() -> unknownSnapshot().setupOpen, 100, swallowingWait())) {
                modifyRejected.set(true);
            }
        });
        assertFalse(modifyRejected.get());
    }

    @Test
    public void unavailableWarningReadCannotConfirmPopupDismissal() {
        GeWarningDialog.Ui ui = new GeWarningDialog.Ui() {
            public boolean warningVisible() { return true; }
            public Rectangle yesButtonBounds() { return new Rectangle(20, 20, 40, 20); }
            public boolean click(Rectangle bounds) { return true; }
            public boolean awaitDismissal() {
                return GeUiState.waitForUi(() -> !GeUiState.requireRead(
                    Optional.<Optional<Boolean>>empty()), 100, swallowingWait());
            }
        };
        assertThrows(GeUiState.UiUnavailable.class, () -> GeWarningDialog.confirm(ui));
    }

    @Test
    public void unavailableInitialWarningReadCannotBecomeNoDialog() {
        GeWarningDialog.Ui ui = new GeWarningDialog.Ui() {
            public boolean warningVisible() {
                return GeUiState.requireRead(Optional.<Optional<Boolean>>empty());
            }
            public Rectangle yesButtonBounds() { fail("No control lookup after a failed read"); return null; }
            public boolean click(Rectangle bounds) { fail("No click after a failed read"); return false; }
            public boolean awaitDismissal() { fail("No wait after a failed read"); return false; }
        };
        assertThrows(GeUiState.UiUnavailable.class, () -> GeWarningDialog.confirm(ui));
    }

    @Test
    public void snapshotUsesActualOverviewSetupDetailsAndBankVisibility() {
        Node frame = new Node(InterfaceID.GeOffers.CONTENTS, "");
        Node overview = new Node(InterfaceID.GeOffers.INDEX, "");
        Node setup = new Node(InterfaceID.GeOffers.SETUP, "");
        Node details = new Node(InterfaceID.GeOffers.DETAILS, "");
        Node bank = new Node((12 << 16) | 1, "");
        setup.hidden = true;
        details.hidden = true;
        bank.hidden = true;
        GeUiState state = GeUiState.capture(frame.widget, overview.widget, setup.widget, details.widget, bank.widget);
        assertTrue(state.exchangeOpen);
        assertTrue(state.overviewOpen);
        assertFalse(state.offerOpen);
        assertFalse(state.bankOpen);
        overview.hidden = true;
        setup.hidden = false;
        bank.hidden = false;
        state = GeUiState.capture(frame.widget, overview.widget, setup.widget, details.widget, bank.widget);
        assertFalse(state.overviewOpen);
        assertTrue(state.setupOpen);
        assertTrue(state.offerOpen);
        assertTrue(state.bankOpen);
        setup.hidden = true;
        details.hidden = false;
        assertTrue(GeUiState.capture(frame.widget, null, setup.widget, details.widget, null).offerOpen);
    }

    @Test
    public void publicChatAndDiagnosticTextCannotBecomeTheMoneyError() {
        for (String text : new String[]{"Too much money!", "Offer has 'Too much money!' error. Backing out to GE overview."}) {
            Node chat = new Node((162 << 16) | 55, text);
            assertFalse(GeUiState.hasTooMuchMoney(chat.widget));
            assertFalse(GeUiState.capture(null, null, chat.widget, null, null).tooMuchMoney);
        }
        Node setup = new Node(InterfaceID.GeOffers.SETUP, "");
        assertFalse(GeUiState.hasTooMuchMoney(setup.widget));
    }

    @Test
    public void visibleSetupMoneyErrorIsRecognizedWithoutAFixedChildIndex() {
        Node setup = new Node(InterfaceID.GeOffers.SETUP, "");
        Node error = new Node(InterfaceID.GeOffers.SETUP, "<col=ff0000>Too much money!</col>");
        setup.dynamicChildren = new Widget[]{null, error.widget};
        assertTrue(GeUiState.hasTooMuchMoney(setup.widget));
        assertTrue(GeUiState.capture(null, null, setup.widget, null, null).tooMuchMoney);
        error.hidden = true;
        assertFalse(GeUiState.hasTooMuchMoney(setup.widget));
        error.hidden = false;
        setup.hidden = true;
        assertFalse(GeUiState.hasTooMuchMoney(setup.widget));
    }

    @Test
    public void suspendingAnIterationClearsWatchdogsWithoutIssuingRecovery() throws Exception {
        FlipperScript script = new FlipperScript();
        for (String name : new String[]{"geClosedSince", "strayPageSince", "offerScreenOpenTime"}) {
            Field field = FlipperScript.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setLong(script, 12345L);
        }
        Field actions = FlipperScript.class.getDeclaredField("offerScreenActionCount");
        actions.setAccessible(true);
        actions.setInt(script, 7);
        script.suspendForUnavailableUi();
        for (String name : new String[]{"geClosedSince", "strayPageSince", "offerScreenOpenTime"}) {
            Field field = FlipperScript.class.getDeclaredField(name);
            field.setAccessible(true);
            assertEquals(0L, field.getLong(script));
        }
        assertEquals(0, actions.getInt(script));
        assertEquals(State.GOING_TO_GE, script.state);
    }

    private static GeUiState unknownSnapshot() {
        return GeUiState.requireRead(Optional.<Optional<GeUiState>>empty());
    }

    /** Models Global.sleepUntil's exception-swallowing behavior around the production guard. */
    private static GeUiState.Wait swallowingWait() {
        return (condition, timeout) -> {
            try {
                return condition.getAsBoolean();
            } catch (Exception swallowed) {
                return false;
            }
        };
    }

    private static final class Node {
        final Widget widget;
        boolean hidden;
        Widget[] children;
        Widget[] dynamicChildren;

        Node(int id, String text) {
            widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
                new Class<?>[]{Widget.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getId": return id;
                        case "getText": return text;
                        case "isHidden": return hidden;
                        case "getChildren": return children;
                        case "getDynamicChildren": return dynamicChildren;
                        case "getStaticChildren":
                        case "getNestedChildren": return null;
                        default: throw new AssertionError("Unexpected Widget call: " + method.getName());
                    }
                });
        }
    }
}
