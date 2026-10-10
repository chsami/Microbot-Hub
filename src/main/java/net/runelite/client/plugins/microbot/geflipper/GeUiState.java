package net.runelite.client.plugins.microbot.geflipper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** A successful UI read can describe a closed interface; a failed read cannot. */
final class GeUiState {
    final boolean exchangeOpen;
    final boolean overviewOpen;
    final boolean setupOpen;
    final boolean offerOpen;
    final boolean bankOpen;
    final boolean tooMuchMoney;

    private GeUiState(Widget contents, Widget overview, Widget setup, Widget details, Widget bank) {
        exchangeOpen = visible(contents);
        overviewOpen = visible(overview);
        setupOpen = visible(setup);
        offerOpen = setupOpen || visible(details);
        bankOpen = visible(bank);
        tooMuchMoney = hasTooMuchMoney(setup);
    }

    /** The caller inspects all these widgets together on ClientThread. */
    static GeUiState capture(Widget contents, Widget overview, Widget setup, Widget details, Widget bank) {
        return new GeUiState(contents, overview, setup, details, bank);
    }

    static final class UiUnavailable extends RuntimeException {
        UiUnavailable() { super("Client-thread UI read unavailable"); }
    }

    /** The inner Optional represents a successful read of a missing widget/control. */
    static <T> T requireRead(Optional<Optional<T>> read) {
        if (!read.isPresent()) throw new UiUnavailable();
        return read.get().orElse(null);
    }

    interface Wait {
        boolean until(BooleanSupplier condition, int timeoutMs);
    }

    /** Global.sleepUntil swallows exceptions, so rethrow unavailable reads outside its predicate. */
    static boolean waitForUi(BooleanSupplier condition, int timeoutMs, Wait wait) {
        UiUnavailable[] unavailable = new UiUnavailable[1];
        boolean completed = wait.until(() -> {
            try {
                return condition.getAsBoolean();
            } catch (UiUnavailable failure) {
                unavailable[0] = failure;
                return true;
            }
        }, timeoutMs);
        if (unavailable[0] != null) throw unavailable[0];
        return completed;
    }

    private static boolean visible(Widget widget) {
        return widget != null && !widget.isHidden();
    }

    static boolean hasTooMuchMoney(Widget setup) {
        if (setup == null || setup.getId() != InterfaceID.GeOffers.SETUP || !visible(setup)) return false;
        return containsMoneyError(setup, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean containsMoneyError(Widget widget, Set<Widget> visited) {
        if (widget == null || !visited.add(widget) || !visible(widget)) return false;
        String text = widget.getText();
        if (text != null && text.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ENGLISH)
            .startsWith("too much money")) return true;
        Widget[][] groups = {widget.getChildren(), widget.getDynamicChildren(),
            widget.getStaticChildren(), widget.getNestedChildren()};
        for (Widget[] children : groups) {
            if (children == null) continue;
            for (Widget child : children) if (containsMoneyError(child, visited)) return true;
        }
        return false;
    }
}
