package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.GeWarningDialog.Result.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for chat text stealing the normal Copilot highlight path. */
public class GeWarningDialogTest {
    @Test
    public void visibleEmptyPopupScaffoldDoesNotBlockIdleMovement() {
        Node scaffold = new Node(InterfaceID.GeOffers.POPUP, "");
        assertFalse(GeWarningDialog.hasVisibleContent(scaffold.widget));
        scaffold.actions = new String[]{null, " ", "<br>"};
        assertFalse(GeWarningDialog.hasVisibleContent(scaffold.widget));
    }

    @Test
    public void realPopupTextBlocksMovementBeforeAButtonIsReady() {
        assertTrue(GeWarningDialog.hasVisibleContent(popup("Your offer is much too high").widget));
        assertTrue(GeWarningDialog.hasVisibleContent(popup("A different GE message").widget));
    }

    @Test
    public void interactivePopupContentBlocksMovementEvenWithoutText() {
        Node button = new Node(InterfaceID.GeOffers.POPUP, "");
        button.actions = new String[]{"Continue"};
        Node scaffold = popup("", button);
        assertTrue(GeWarningDialog.hasVisibleContent(scaffold.widget));
        button.hidden = true;
        assertFalse(GeWarningDialog.hasVisibleContent(scaffold.widget));
    }

    @Test
    public void publicChatAndTheOldDiagnosticCannotBecomeAGeWarning() {
        for (String text : new String[]{"Are you sure", "Your offer is much too low",
            "Price warning dialog detected ('Your offer is much' / 'Are you sure'). Clicking 'Yes' to confirm..."}) {
            Node chat = new Node((162 << 16) | 55, text);
            chat.children = new Widget[]{yes().widget};
            assertFalse(GeWarningDialog.isWarningVisible(chat.widget));
            assertNull(GeWarningDialog.findYesButton(chat.widget));
            FakeUi ui = new FakeUi(chat);
            assertEquals(NO_DIALOG, GeWarningDialog.confirm(ui));
            assertEquals(0, ui.clicks);
            assertEquals(0, ui.dismissalChecks);
        }
    }

    @Test
    public void anotherGeContainerWithTheSameWordsIsNotThePopup() {
        Node offer = new Node(InterfaceID.GeOffers.SETUP, "Are you sure");
        offer.children = new Widget[]{yes().widget};
        assertFalse(GeWarningDialog.isWarningVisible(offer.widget));
        assertNull(GeWarningDialog.findYesButton(offer.widget));
    }

    @Test
    public void visibleWarningResolvesTheExactYesOperationAtAnyDynamicIndex() {
        Node yes = yes();
        Node popup = popup("Your offer is much lower than the guide price. Are you sure?", yes);
        // Dynamic children share the packed popup ID; their position must not select the button.
        popup.children = null;
        popup.dynamicChildren = new Widget[]{new Node(InterfaceID.GeOffers.POPUP, "No").widget,
            null, yes.widget};
        assertTrue(GeWarningDialog.isWarningVisible(popup.widget));
        assertSame(yes.widget, GeWarningDialog.findYesButton(popup.widget));
    }

    @Test
    public void formattedYesTextMustStillExposeAWidgetOperation() {
        Node yes = new Node(InterfaceID.GeOffers.POPUP, "<col=ffffff> YES </col>");
        yes.actions = new String[]{"Select"};
        assertSame(yes.widget, GeWarningDialog.findYesButton(popup("Are you sure?", yes).widget));
        yes.actions = null;
        assertNull(GeWarningDialog.findYesButton(popup("Are you sure?", yes).widget));
        yes.actions = new String[]{null, ""};
        assertNull(GeWarningDialog.findYesButton(popup("Are you sure?", yes).widget));
    }

    @Test
    public void exactYesActionCanIdentifyAButtonWithoutASeparateTextLabel() {
        Node yes = new Node(InterfaceID.GeOffers.POPUP, "");
        yes.actions = new String[]{"<col=ffffff>Yes</col>"};
        assertSame(yes.widget, GeWarningDialog.findYesButton(popup("Are you sure?", yes).widget));
    }

    @Test
    public void yesterdayAndOtherPartialYesMatchesNeverSelectAControl() {
        for (String label : new String[]{"yesterday", "Yes please", "Say Yes", "Yes later"}) {
            Node candidate = new Node(InterfaceID.GeOffers.POPUP, label);
            candidate.actions = new String[]{label};
            Node popup = popup("Are you sure?", candidate);
            assertTrue(GeWarningDialog.isWarningVisible(popup.widget));
            assertNull(GeWarningDialog.findYesButton(popup.widget));
            FakeUi ui = new FakeUi(popup);
            assertEquals(NOT_READY, GeWarningDialog.confirm(ui));
            assertEquals(0, ui.clicks);
        }
    }

    @Test
    public void hiddenPopupCannotUseItsVisibleChildren() {
        Node popup = popup("Are you sure?", yes());
        popup.hidden = true;
        assertFalse(GeWarningDialog.isWarningVisible(popup.widget));
        assertNull(GeWarningDialog.findYesButton(popup.widget));
    }

    @Test
    public void hiddenIntermediateContainerCannotExposeAYesButton() {
        Node container = new Node(InterfaceID.GeOffers.POPUP, "");
        container.hidden = true;
        container.children = new Widget[]{yes().widget};
        Node popup = popup("Are you sure?", container);
        assertTrue(GeWarningDialog.isWarningVisible(popup.widget));
        assertNull(GeWarningDialog.findYesButton(popup.widget));
    }

    @Test
    public void hiddenWarningTextDoesNotAuthorizeAnotherVisibleYes() {
        Node warning = new Node(InterfaceID.GeOffers.POPUP, "Are you sure?");
        warning.hidden = true;
        Node popup = popup("", warning, yes());
        assertFalse(GeWarningDialog.isWarningVisible(popup.widget));
        assertNull(GeWarningDialog.findYesButton(popup.widget));
    }

    @Test
    public void unrelatedPopupAndMissingPopupCannotBecomeAConfirmation() {
        Node popup = popup("Nothing to confirm", yes());
        assertFalse(GeWarningDialog.isWarningVisible(popup.widget));
        assertNull(GeWarningDialog.findYesButton(popup.widget));
        assertFalse(GeWarningDialog.isWarningVisible(null));
        assertNull(GeWarningDialog.findYesButton(null));
    }

    @Test
    public void zeroSizedYesControlCannotBeClicked() {
        Node yes = yes();
        yes.bounds = new Rectangle(100, 100, 0, 20);
        assertNull(GeWarningDialog.findYesButton(popup("Are you sure?", yes).widget));
        yes.bounds = null;
        assertNull(GeWarningDialog.findYesButton(popup("Are you sure?", yes).widget));
    }

    @Test
    public void nestedAndStaticControlsAreSupportedWithoutRepeatedOrCyclicTraversal() {
        Node yes = yes();
        Node nested = new Node(InterfaceID.GeOffers.POPUP, "Are you sure?");
        nested.staticChildren = new Widget[]{yes.widget};
        Node popup = popup("", nested);
        popup.children = null;
        popup.nestedChildren = new Widget[]{nested.widget};
        nested.children = new Widget[]{nested.widget, popup.widget};
        assertSame(yes.widget, GeWarningDialog.findYesButton(popup.widget));
    }

    @Test
    public void successfulClickMustBeFollowedByPopupDismissal() {
        Node popup = popup("Your offer is much higher than the guide price.", yes());
        FakeUi ui = new FakeUi(popup);
        ui.dismissOnClick = true;
        assertEquals(CONFIRMED, GeWarningDialog.confirm(ui));
        assertEquals(1, ui.clicks);
        assertEquals(1, ui.dismissalChecks);
        assertFalse(GeWarningDialog.isWarningVisible(popup.widget));
    }

    @Test
    public void dispatchedClickWithoutDismissalDoesNotReportSuccess() {
        FakeUi ui = new FakeUi(popup("Are you sure?", yes()));
        assertEquals(NOT_DISMISSED, GeWarningDialog.confirm(ui));
        assertEquals(1, ui.clicks);
        assertEquals(1, ui.dismissalChecks);
    }

    @Test
    public void failedClickDoesNotWaitOrReportSuccess() {
        FakeUi ui = new FakeUi(popup("Are you sure?", yes()));
        ui.clickSucceeds = false;
        assertEquals(CLICK_FAILED, GeWarningDialog.confirm(ui));
        assertEquals(0, ui.clicks);
        assertEquals(0, ui.dismissalChecks);
    }

    @Test
    public void vanishedPopupBeforeFinalClickValidationIsNotClicked() {
        FakeUi ui = new FakeUi(popup("Are you sure?", yes()));
        ui.vanishBeforeClick = true;
        assertEquals(CLICK_FAILED, GeWarningDialog.confirm(ui));
        assertEquals(0, ui.clicks);
        assertEquals(0, ui.dismissalChecks);
    }

    private static Node yes() {
        Node yes = new Node(InterfaceID.GeOffers.POPUP, "Yes");
        yes.actions = new String[]{"Yes"};
        return yes;
    }

    private static Node popup(String text, Node... children) {
        Node popup = new Node(InterfaceID.GeOffers.POPUP, text);
        popup.children = new Widget[children.length];
        for (int i = 0; i < children.length; i++) popup.children[i] = children[i].widget;
        return popup;
    }

    private static final class FakeUi implements GeWarningDialog.Ui {
        final Node popup;
        boolean clickSucceeds = true;
        boolean dismissOnClick;
        boolean vanishBeforeClick;
        int clicks;
        int dismissalChecks;

        FakeUi(Node popup) { this.popup = popup; }
        public boolean warningVisible() { return GeWarningDialog.isWarningVisible(popup.widget); }
        public Rectangle yesButtonBounds() {
            Widget button = GeWarningDialog.findYesButton(popup.widget);
            return button == null ? null : new Rectangle(button.getBounds());
        }
        public boolean click(Rectangle bounds) {
            if (vanishBeforeClick) popup.hidden = true;
            if (!clickSucceeds || !bounds.equals(yesButtonBounds())) return false;
            clicks++;
            if (dismissOnClick) popup.hidden = true;
            return true;
        }
        public boolean awaitDismissal() {
            dismissalChecks++;
            return !warningVisible();
        }
    }

    private static final class Node {
        final Widget widget;
        boolean hidden;
        String[] actions;
        Rectangle bounds = new Rectangle(200, 170, 60, 20);
        Widget[] children;
        Widget[] dynamicChildren;
        Widget[] staticChildren;
        Widget[] nestedChildren;

        Node(int id, String text) {
            widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
                new Class<?>[]{Widget.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getId": return id;
                        case "getText": return text;
                        case "isHidden": return hidden;
                        case "getActions": return actions;
                        case "getBounds": return bounds;
                        case "getChildren": return children;
                        case "getDynamicChildren": return dynamicChildren;
                        case "getStaticChildren": return staticChildren;
                        case "getNestedChildren": return nestedChildren;
                        default: throw new AssertionError("Unexpected Widget call: " + method.getName());
                    }
                });
        }
    }
}
