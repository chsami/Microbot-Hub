package net.runelite.client.plugins.microbot.droslayer;

import java.util.Collections;
import java.util.Locale;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** One observed rewards-menu action per tick, scoped to task controls rather than reward labels. */
final class DroSlayerRewards {
    private String pendingTask;
    private Boolean pendingBlock;

    void reset() {
        pendingTask = null;
        pendingBlock = null;
    }

    boolean close() {
        return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Microbot.getClientThread().runOnClientThreadOptional(() ->
                        click(find(Microbot.getClient().getWidget(InterfaceID.SlayerRewards.BORDER), "Close"))).orElse(false));
    }

    boolean clickNext(boolean block, String task) {
        if (task == null || task.isBlank()) return false;
        return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    Widget confirmation = Microbot.getClient().getWidget(InterfaceID.SlayerRewards.CONFIRM);
                    if (visible(confirmation)) {
                        Widget info = Microbot.getClient().getWidget(InterfaceID.SlayerRewards.CONFIRM_INFO);
                        String text = confirmationText(info);
                        String verb = block ? "block" : "cancel";
                        if (pendingBlock == null || pendingBlock != block || !task.equalsIgnoreCase(pendingTask)
                                || !matchesConfirmation(block, text)) {
                            Microbot.status = "Waiting for matching task " + verb + " confirmation";
                            return false;
                        }
                        // CONFIRM (3) is the panel; CONFIRM_BUTTON (11) is the actual hit target.
                        return click(Microbot.getClient().getWidget(InterfaceID.SlayerRewards.CONFIRM_BUTTON));
                    }
                    Widget upper = Microbot.getClient().getWidget(InterfaceID.SlayerRewards.TASKS_CONTENT_UPPER);
                    Widget action = find(upper, block ? "Block" : "Cancel");
                    if (action != null) {
                        if (!click(action)) return false;
                        pendingTask = task;
                        pendingBlock = block;
                        return true;
                    }
                    Widget tabs = Microbot.getClient().getWidget(InterfaceID.SlayerRewards.TABS);
                    return click(find(tabs, "Tasks"));
                }).orElse(false));
    }

    private static Widget find(Widget root, String action) {
        if (!visible(root)) return null;
        Widget widget = Rs2Widget.findWidget(action, Collections.singletonList(root), true);
        return visible(widget) ? widget : null;
    }

    private static boolean visible(Widget widget) {
        return widget != null && !widget.isHidden();
    }

    private static boolean click(Widget widget) {
        return visible(widget) && widget.getBounds() != null && !widget.getBounds().isEmpty()
                && Rs2Widget.clickWidget(widget);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("<[^>]*>", " ").toLowerCase(Locale.ROOT);
    }

    static boolean matchesConfirmation(boolean block, String text) {
        String value = clean(text);
        boolean blocking = value.matches("(?s).*\\bblock(?:ing|ed)?\\b.*");
        boolean skipping = value.matches("(?s).*\\b(?:cancel(?:ling|ing|led|ed)?|skip(?:ping|ped)?)\\b.*");
        return block ? blocking && !skipping : skipping && !blocking;
    }

    private static String confirmationText(Widget root) {
        if (!visible(root)) return "";
        StringBuilder text = new StringBuilder(clean(root.getText()));
        appendChildren(text, root.getStaticChildren());
        appendChildren(text, root.getDynamicChildren());
        appendChildren(text, root.getNestedChildren());
        return text.toString();
    }

    private static void appendChildren(StringBuilder text, Widget[] children) {
        if (children == null) return;
        for (Widget child : children) text.append(' ').append(confirmationText(child));
    }
}
