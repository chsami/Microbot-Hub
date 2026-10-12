package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real prompt pipeline without a client, input emitter or scheduler. */
class FlipperMouseCancellationTest {
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
