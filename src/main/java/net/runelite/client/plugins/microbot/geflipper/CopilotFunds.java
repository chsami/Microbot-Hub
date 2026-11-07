package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Component;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.Plugin;

/** Reads only the current Copilot display. Never call from ClientThread or while owning input. */
final class CopilotFunds {
    private static final int MAX_TEXT_LENGTH = 2048;
    private static final int EDT_TIMEOUT_MS = 250;
    private static final String CURRENCY = "(?:funds|gp|coins?|money|cash|balance|capital|gold(?: pieces)?)";
    private static final Pattern FUNDS = Pattern.compile(
        "(?<!not )(?<!no )(?<!don't )\\b(?:"
            + "(?:not enough|not sufficient|insufficient(?: available)?|(?:do not|don't) have (?:enough|sufficient)|"
            + "(?:needs?|requires?) (?:more|additional)|running low on|short of|no available|"
            + "low|too little|no|(?:run|ran|running) out of|"
            + "(?:add|deposit|withdraw) (?:more|additional)) (?:available |spare )?" + CURRENCY
            + "|(?:more|additional) " + CURRENCY + " (?:is |are )?(?:needed|required)"
            + "|" + CURRENCY + " (?:is |are )?(?:insufficient|low|too low|not enough|depleted|exhausted))\\b");
    private static final Pattern MINIMUM = Pattern.compile(
        "^add at least ([\\p{Nd}\\s,.'\\u2019]+) gp to your inventory to get a flip suggestion(?: |$)");

    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<FutureTask<Boolean>> pending = new AtomicReference<>();
    private final Consumer<Runnable> edtQueue;

    CopilotFunds() {
        this(SwingUtilities::invokeLater);
    }

    CopilotFunds(Consumer<Runnable> edtQueue) {
        this.edtQueue = edtQueue;
    }

    /** Worker-only bounded wait; EDT callers read directly. No display result is cached. */
    boolean current(Plugin owner, Object manager, Object suggestion) {
        if (owner == null || manager == null || suggestion == null) return false;
        long expected = generation.get();
        if (SwingUtilities.isEventDispatchThread()) {
            return read(owner, manager) && generation.get() == expected;
        }
        FutureTask<Boolean> task = new FutureTask<>(() -> generation.get() == expected
            && SwingUtilities.isEventDispatchThread() && read(owner, manager));
        if (!pending.compareAndSet(null, task)) return false;
        try {
            try {
                edtQueue.accept(() -> {
                    try {
                        task.run();
                    } finally {
                        pending.compareAndSet(task, null);
                    }
                });
            } catch (RuntimeException unavailable) {
                pending.compareAndSet(task, null);
                return false;
            }
            return task.get(EDT_TIMEOUT_MS, TimeUnit.MILLISECONDS) && generation.get() == expected;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException unavailable) {
            return false;
        } finally {
            // A finished read has nothing left to run on the Swing thread, so free the slot here. Leaving it to
            // the Swing thread's own last step let a read that followed at once be refused as busy. A read that
            // has not finished keeps the slot until the Swing thread drains it, so reads still never pile up.
            if (task.isDone()) pending.compareAndSet(task, null);
            task.cancel(false);
        }
    }

    void reset() {
        generation.incrementAndGet();
    }

    private static boolean read(Plugin owner, Object manager) {
        try {
            Object main = field(owner, "mainPanel");
            Object copilot = field(main, "copilotPanel");
            Object panel = field(copilot, "suggestionPanel");
            Object container = field(panel, "suggestionTextContainer");
            Object label = field(panel, "suggestionText");
            if (!(main instanceof Component) || !(copilot instanceof Component)
                || !(panel instanceof Component) || !(container instanceof Component)
                || !(label instanceof JLabel) || field(panel, "suggestionManager") != manager
                || !Boolean.FALSE.equals(field(panel, "isPreferencesPanelVisible"))) return false;
            Component text = (Component) label;
            if (!within(text, (Component) container) || !within((Component) container, (Component) panel)
                || !within((Component) panel, (Component) copilot)
                || !within((Component) copilot, (Component) main)
                || !visibleThrough(text, (Component) main)) return false;
            return fundsText(((JLabel) label).getText());
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        if (owner == null) return null;
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static boolean within(Component child, Component ancestor) {
        return child != ancestor && SwingUtilities.isDescendingFrom(child, ancestor);
    }

    private static boolean visibleThrough(Component component, Component ancestor) {
        for (int depth = 0; component != null && depth < 32; depth++, component = component.getParent()) {
            if (!component.isVisible()) return false;
            if (component == ancestor) return true;
        }
        return false;
    }

    static boolean fundsText(String text) {
        if (text == null || text.length() > MAX_TEXT_LENGTH) return false;
        String plain = text.replaceAll("(?i)<br\\s*/?>", " ")
            .replaceAll("<[^>]*>", " ")
            .replaceAll("(?i)&nbsp;|&#160;|&#x0*a0;", " ")
            .replaceAll("(?i)&apos;|&rsquo;|&#39;|&#x0*27;|&#8217;|&#x0*2019;", "'")
            .replace('\u2019', '\'')
            .replace('\u00a0', ' ').replace('\u202f', ' ')
            .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (FUNDS.matcher(plain).find()) return true;
        Matcher minimum = MINIMUM.matcher(plain);
        if (!minimum.find()) return false;
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < minimum.group(1).length(); index++) {
            int digit = Character.digit(minimum.group(1).charAt(index), 10);
            if (digit >= 0) digits.append(digit);
        }
        return "1000".contentEquals(digits);
    }
}
