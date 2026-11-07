package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real prompt pipeline without a client, input emitter or scheduler. */
class FlipperMouseCancellationTest {
    @Test
    void promptMovementRejectsDetachedOrChangedWidgetsEvenAtTheSamePosition() throws Exception {
        Field clientField = field(Microbot.class, "client");
        Object previousClient = clientField.get(null);
        try {
            for (boolean dynamic : new boolean[]{false, true}) {
                Widget[] widgets = new Widget[2];
                java.util.concurrent.atomic.AtomicReference<String> text =
                    new java.util.concurrent.atomic.AtomicReference<>("Copilot item: synthetic item");
                String[] actions = {"Select"};
                java.util.concurrent.atomic.AtomicBoolean hidden = new java.util.concurrent.atomic.AtomicBoolean();
                widgets[0] = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getChild")) return widgets[1];
                        if (method.getName().equals("isHidden")) return false;
                        throw new AssertionError(method.getName());
                    });
                widgets[1] = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getId": return 101;
                            case "getIndex": return dynamic ? 2 : -1;
                            case "getParent": return widgets[0];
                            case "getBounds": return new java.awt.Rectangle(20, 20, 60, 20);
                            case "getText": return text.get();
                            case "getActions": return actions;
                            case "isHidden": return hidden.get();
                            default: throw new AssertionError(method.getName());
                        }
                    });
                Widget original = widgets[1];
                java.util.concurrent.atomic.AtomicReference<Widget> live =
                    new java.util.concurrent.atomic.AtomicReference<>(dynamic ? widgets[0] : original);
                Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getWidget")) return live.get();
                        throw new AssertionError(method.getName());
                    });
                clientField.set(null, client);
                FlipperScript.PromptClick target = FlipperScript.PromptClick.capture(original);
                assertNotNull(target);
                assertTrue(target.matches());
                live.set(null);
                assertFalse(target.matches(), "A detached old widget must not authorize a click");
                live.set(dynamic ? widgets[0] : original);
                text.set("Different item at the same position");
                assertFalse(target.matches());
                text.set("Copilot item: synthetic item");
                actions[0] = "Different action";
                assertFalse(target.matches(), "Actions must be copied before movement");
                actions[0] = "Select";
                hidden.set(true);
                assertFalse(target.matches());
                hidden.set(false);
                if (dynamic) {
                    widgets[1] = null;
                    assertFalse(target.matches(), "A replaced dynamic child must not authorize a click");
                }
            }
        } finally {
            clientField.set(null, previousClient);
        }
    }

    @Test
    void cancelledItemClickConsumesTheTickBeforeEnterOrAnotherClick() throws Exception {
        PromptScript script = new PromptScript(true);

        assertTrue(processPrompt(script));

        assertEquals(1, script.clicks);
        assertSame(script.item, script.clicked);
        assertEquals(0, script.waits, "A cancelled click cannot enter the keyboard fallback pipeline");
    }

    @Test
    void cancelledPriceClickConsumesTheTickBeforePopulatingOrSubmittingTheValue() throws Exception {
        PromptScript script = new PromptScript(false);

        assertTrue(processPrompt(script));

        assertEquals(1, script.clicks);
        assertSame(script.button, script.clicked);
        assertEquals(0, script.waits, "A cancelled click cannot enter value, typing or Enter fallbacks");
    }

    @Test
    void acceptedFinishRequestInvalidatesAnAlreadyCapturedTradingMovement() throws Exception {
        Field clientField = field(Microbot.class, "client");
        Object previousClient = clientField.get(null);
        boolean previousPause = Microbot.pauseAllScripts.get();
        boolean previousInputDisabled = InputArbiter.isDisabled();
        try {
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getGameState")) return GameState.LOGGED_IN;
                    throw new AssertionError("Movement guards cannot read UI or schedule client work: " + method.getName());
                });
            clientField.set(null, client);
            Microbot.pauseAllScripts.set(false);
            // Isolate generation invalidation without clearing or replacing the arbiter's input state.
            InputArbiter.setDisabled(true);
            PromptScript script = new PromptScript(true);
            configure(script);
            Method capture = FlipperScript.class.getDeclaredMethod("mouseMovementGuard");
            capture.setAccessible(true);
            BooleanSupplier earlierMovement = (BooleanSupplier) capture.invoke(script);

            assertTrue(earlierMovement.getAsBoolean());
            assertTrue(script.requestFinish(() -> {}));
            assertTrue(script.isFinishing());
            assertFalse(earlierMovement.getAsBoolean(), "Finish must cancel an earlier normal trading movement");
            assertFalse(script.requestFinish(() -> {}), "A replay cannot create another Finish request");
        } finally {
            InputArbiter.setDisabled(previousInputDisabled);
            Microbot.pauseAllScripts.set(previousPause);
            clientField.set(null, previousClient);
        }
    }

    @Test
    void changingSlotActionModeCancelsCapturedGestureWithoutBlockingNewGestures() throws Exception {
        Field clientField = field(Microbot.class, "client");
        Object previousClient = clientField.get(null);
        boolean previousPause = Microbot.pauseAllScripts.get();
        boolean previousInputDisabled = InputArbiter.isDisabled();
        try {
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getGameState")) return GameState.LOGGED_IN;
                    throw new AssertionError("Movement guards cannot read UI or schedule client work: " + method.getName());
                });
            clientField.set(null, client);
            Microbot.pauseAllScripts.set(false);
            InputArbiter.setDisabled(true);
            PromptScript script = new PromptScript(true);
            java.util.concurrent.atomic.AtomicReference<FlipperConfig.SlotAction> mode =
                new java.util.concurrent.atomic.AtomicReference<>(FlipperConfig.SlotAction.COPILOT_LEFT_CLICK);
            FlipperConfig config = new FlipperConfig() {
                @Override public boolean randomizeMouseSpeed() { return true; }
                @Override public SlotAction slotAction() { return mode.get(); }
            };
            field(FlipperScript.class, "config").set(script, config);
            FlipperPlugin plugin = new FlipperPlugin();
            field(FlipperPlugin.class, "config").set(plugin, config);
            field(FlipperPlugin.class, "flipperScript").set(plugin, script);
            Method capture = FlipperScript.class.getDeclaredMethod("mouseMovementGuard");
            capture.setAccessible(true);
            BooleanSupplier earlierMovement = (BooleanSupplier) capture.invoke(script);
            assertTrue(earlierMovement.getAsBoolean());

            ConfigChanged event = new ConfigChanged();
            event.setGroup("Other plugin");
            event.setKey("slotActionMode");
            plugin.onConfigChanged(event);
            assertTrue(earlierMovement.getAsBoolean(), "Another plugin's similarly named setting is unrelated");

            mode.set(FlipperConfig.SlotAction.MENU_OPTION);
            event.setGroup("Flipper Config");
            plugin.onConfigChanged(event);

            assertFalse(earlierMovement.getAsBoolean(), "A mode change must cancel a previously captured slot gesture");
            BooleanSupplier freshMovement = (BooleanSupplier) capture.invoke(script);
            assertTrue(freshMovement.getAsBoolean(), "The new mode can create a fresh gesture normally");
        } finally {
            InputArbiter.setDisabled(previousInputDisabled);
            Microbot.pauseAllScripts.set(previousPause);
            clientField.set(null, previousClient);
        }
    }

    private static void configure(PromptScript script) throws Exception {
        field(FlipperScript.class, "config").set(script, new FlipperConfig() {
            @Override public SelectionMethod selectionMethod() { return SelectionMethod.MOUSE; }
            @Override public boolean randomizeMouseSpeed() { return true; }
        });
    }

    private static boolean processPrompt(PromptScript script) throws Exception {
        configure(script);
        Method method = FlipperScript.class.getDeclaredMethod("checkAndPressCopilotKeybind");
        method.setAccessible(true);
        return (Boolean) method.invoke(script);
    }

    private static final class PromptScript extends FlipperScript {
        private final Widget item = widget(101, "Copilot item: synthetic item");
        private final Widget prompt = widget(102, "Set a price for each item:");
        private final Widget button = widget(103, "Press [E] to set to Copilot price: 123 gp");
        private final boolean itemSelection;
        private int clicks, waits;
        private Widget clicked;

        private PromptScript(boolean itemSelection) { this.itemSelection = itemSelection; }

        @Override public boolean isRunning() { return true; }
        @Override Widget findSuggestedItemWidget() { return itemSelection ? item : null; }
        @Override boolean isUiWidgetVisible(int id) { return true; }
        @Override Widget findUiWidget(String text, List<Widget> children, boolean exact) {
            if (itemSelection) fail("Cancelled item selection cannot inspect later prompts");
            if (text.equals("Set a price for each item:")) return prompt;
            if (text.equals("How many do you wish to ")) return null;
            if (text.equals("to set to Copilot")) return button;
            throw new AssertionError("Unexpected prompt lookup: " + text);
        }
        @Override boolean clickTradingWidget(Widget widget) {
            clicks++;
            clicked = widget;
            return false;
        }
        @Override boolean waitForUi(BooleanSupplier condition, int timeoutMs) {
            waits++;
            throw new AssertionError("Cancelled movement entered a fallback wait");
        }
    }

    private static Widget widget(int id, String text) {
        return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getId": return id;
                    case "getText": return text;
                    case "isHidden": return false;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "synthetic-prompt-widget";
                    default: throw new AssertionError("Unexpected widget access: " + method.getName());
                }
            });
    }
}
