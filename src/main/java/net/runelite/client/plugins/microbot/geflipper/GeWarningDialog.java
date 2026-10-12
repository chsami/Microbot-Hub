package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Finds GE confirmations in their popup, never in chat or another interface. */
final class GeWarningDialog {
    enum Result { NO_DIALOG, NOT_READY, CLICK_FAILED, NOT_DISMISSED, CONFIRMED }

    interface Ui {
        boolean warningVisible();
        Rectangle yesButtonBounds();
        boolean click(Rectangle bounds);
        boolean awaitDismissal();
    }

    static Result confirm(Ui ui) {
        if (!ui.warningVisible()) return Result.NO_DIALOG;
        Rectangle bounds = ui.yesButtonBounds();
        if (bounds == null) return Result.NOT_READY;
        if (!ui.click(bounds)) return Result.CLICK_FAILED;
        return ui.awaitDismissal() ? Result.CONFIRMED : Result.NOT_DISMISSED;
    }

    /** Widget inspection must be performed on the client thread. */
    static boolean isWarningVisible(Widget popup) {
        return visiblePopupWidgets(popup).stream().anyMatch(GeWarningDialog::isWarningText);
    }

    /** GE keeps an empty visible popup scaffold even when no dialog is open. */
    static boolean hasVisibleContent(Widget popup) {
        for (Widget widget : visiblePopupWidgets(popup)) {
            if (!clean(widget.getText()).isEmpty()) return true;
            String[] actions = widget.getActions();
            if (actions != null) {
                for (String action : actions) if (!clean(action).isEmpty()) return true;
            }
        }
        return false;
    }

    /** Resolves the live control without depending on the popup's dynamic child indices. */
    static Widget findYesButton(Widget popup) {
        List<Widget> widgets = visiblePopupWidgets(popup);
        if (widgets.stream().noneMatch(GeWarningDialog::isWarningText)) return null;
        for (Widget widget : widgets) {
            if (widget == popup || !isYesControl(widget)) continue;
            Rectangle bounds = widget.getBounds();
            if (bounds != null && bounds.width > 0 && bounds.height > 0) return widget;
        }
        return null;
    }

    private static boolean isWarningText(Widget widget) {
        String text = clean(widget.getText());
        return text.startsWith("your offer is much") || text.startsWith("are you sure");
    }

    private static boolean isYesControl(Widget widget) {
        String[] actions = widget.getActions();
        if (actions == null) return false;
        boolean actionable = false;
        for (String action : actions) {
            String label = clean(action);
            if ("yes".equals(label)) return true;
            actionable |= !label.isEmpty();
        }
        // A decorative Yes label is not a button; it must expose a widget operation.
        return actionable && "yes".equals(clean(widget.getText()));
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("<[^>]*>", "")
            .trim().toLowerCase(Locale.ENGLISH);
    }

    private static List<Widget> visiblePopupWidgets(Widget popup) {
        List<Widget> widgets = new ArrayList<>();
        if (popup == null || popup.getId() != InterfaceID.GeOffers.POPUP || popup.isHidden()) return widgets;
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectVisible(popup, widgets, visited);
        return widgets;
    }

    private static void collectVisible(Widget widget, List<Widget> widgets, Set<Widget> visited) {
        if (widget == null || !visited.add(widget) || widget.isHidden()) return;
        widgets.add(widget);
        Widget[][] groups = {widget.getChildren(), widget.getDynamicChildren(),
            widget.getStaticChildren(), widget.getNestedChildren()};
        for (Widget[] children : groups) {
            if (children == null) continue;
            for (Widget child : children) collectVisible(child, widgets, visited);
        }
    }
}
