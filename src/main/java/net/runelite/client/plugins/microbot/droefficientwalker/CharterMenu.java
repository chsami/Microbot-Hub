package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** The original list-menu lookup, with map-menu support retained. Call on the client thread. */
final class CharterMenu {
    // Prefer the working list layout when both representations are present.
    private static final int[][] CONTAINERS = {{885, 4}, {72, 3}};
    private CharterMenu() { }

    static boolean open(Client client) {
        for (int[] container : CONTAINERS) {
            Widget parent = client.getWidget(container[0], container[1]);
            if (parent != null && !parent.isHidden()) return true;
        }
        return false;
    }

    static Widget destination(Client client, String name) {
        if (normalize(name).isEmpty()) return null;
        for (int[] container : CONTAINERS) {
            Widget parent = client.getWidget(container[0], container[1]);
            if (parent == null || parent.isHidden()) continue;
            if (container[0] == 885) return listDestination(parent, name);
            Set<Widget> matches = identitySet();
            collect(parent, parent, name, identitySet(), matches);
            if (matches.size() > 1) return null;
            if (!matches.isEmpty()) return matches.iterator().next();
        }
        return null;
    }

    // List widgets use the original recursive text lookup and implicit operation 1.
    // They can repeat labels; duplicate map-marker rules do not apply to this interface.
    private static Widget listDestination(Widget root, String name) {
        Widget match = firstListMatch(root, name, identitySet());
        if (match == null) return null;
        Set<Widget> parents = identitySet();
        for (Widget current = match; current != null && parents.add(current); current = current.getParent()) {
            if (firstListOption(current) != null) return current;
            if (current == root) break;
        }
        return match;
    }

    private static String firstListOption(Widget widget) {
        String[] actions = widget.getActions();
        if (actions != null) for (String action : actions) {
            if (action != null && !action.isBlank()) return action;
        }
        return null;
    }

    private static Widget firstListMatch(Widget widget, String name, Set<Widget> visited) {
        if (widget == null || !visited.add(widget) || widget.isHidden()) return null;
        if (textMatches(widget.getText(), name) || textMatches(widget.getName(), name)) return widget;
        String[] actions = widget.getActions();
        if (actions != null) for (String action : actions) if (textMatches(action, name)) return widget;
        // Preserve the working list's traversal order. Generic children are a final fallback.
        for (Widget[] children : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(),
            widget.getNestedChildren(), widget.getChildren()}) {
            if (children != null) for (Widget child : children) {
                Widget match = firstListMatch(child, name, visited);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void collect(Widget widget, Widget root, String name, Set<Widget> visited,
                                Set<Widget> matches) {
        if (widget == null || !visited.add(widget) || widget.isHidden()) return;
        if (widget != root && (actionIndex(widget, name) > 0
            || textMatches(widget.getText(), name) || textMatches(widget.getName(), name))) {
            // List entries put the destination in a text child and Select/Travel on its parent.
            // The old walker searched all child kinds and used that clickable ancestor.
            Widget clickable = widget;
            Set<Widget> parents = identitySet();
            while (clickable != null && clickable != root && parents.add(clickable)) {
                if (firstActionIndex(clickable) > 0) break;
                clickable = clickable.getParent();
            }
            matches.add(clickable == null || clickable == root ? widget : clickable);
        }
        for (Widget[] children : new Widget[][]{widget.getChildren(), widget.getStaticChildren(),
            widget.getDynamicChildren(), widget.getNestedChildren()}) {
            if (children != null) for (Widget child : children) collect(child, root, name, visited, matches);
        }
    }

    static int actionIndex(Widget widget, String destination) {
        if (widget == null || normalize(destination).isEmpty() || widget.getActions() == null) return -1;
        String[] actions = widget.getActions();
        for (int i = 0; i < actions.length; i++) if (labelMatches(actions[i], destination)) return i + 1;
        return -1;
    }

    private static int firstActionIndex(Widget widget) {
        String[] actions = widget.getActions();
        if (actions != null) for (int i = 0; i < actions.length; i++) {
            String action = normalize(actions[i]);
            if (!action.isEmpty() && !action.equals("examine") && !action.equals("close")
                && !action.equals("cancel")) return i + 1;
        }
        return -1;
    }

    private static boolean labelMatches(String label, String destination) {
        String expected = normalize(destination), actual = normalize(label);
        if (expected.isEmpty()) return false;
        // The list includes numbered/lettered shortcuts. Shipyard's list label includes Karamja.
        actual = actual.replaceFirst("^[0-9a-z]+\\s*:\\s*", "");
        return actual.equals(expected) || expected.equals("shipyard") && actual.equals("karamja shipyard");
    }

    private static boolean textMatches(String label, String destination) {
        String expected = normalize(destination);
        // Preserve the original text/name search, including shortcut and fare decorations.
        return !expected.isEmpty() && normalize(label).contains(expected);
    }

    private static String normalize(String text) {
        return text == null ? "" : Text.removeTags(text).replace('\u00a0', ' ')
            .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Set<Widget> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    static Selection selection(Client client, String name) {
        if (normalize(name).isEmpty()) return null;
        Widget list = client.getWidget(885, 4);
        if (list != null && !list.isHidden()) {
            Widget widget = listDestination(list, name);
            if (widget == null) return null;
            String option = firstListOption(widget);
            return new Selection(widget.getId(), widget.getIndex(), 1,
                option == null ? name : option, widget.getBounds());
        }
        Widget widget = destination(client, name);
        if (widget == null) return null;
        int action = actionIndex(widget, name);
        if (action < 1) action = firstActionIndex(widget);
        // Preserve the old list's implicit first operation when the label has no action array.
        String option = action > 0 ? widget.getActions()[action - 1] : name;
        return new Selection(widget.getId(), widget.getIndex(), Math.max(1, action), option, widget.getBounds());
    }

    /** A visible interface root alone does not mean its destination entries have loaded. */
    static boolean populated(Client client) {
        for (int[] container : CONTAINERS) {
            Widget parent = client.getWidget(container[0], container[1]);
            if (populated(parent, identitySet())) return true;
        }
        return false;
    }

    static boolean missing(Client client, String name) {
        if (destination(client, name) != null || !populated(client)) return false;
        for (int[] container : CONTAINERS) {
            Widget parent = client.getWidget(container[0], container[1]);
            Set<Widget> matches = identitySet();
            collect(parent, parent, name, identitySet(), matches);
            if (!matches.isEmpty()) return false; // Ambiguity is not an account unlock failure.
        }
        return true;
    }

    private static boolean populated(Widget widget, Set<Widget> visited) {
        if (widget == null || !visited.add(widget) || widget.isHidden()) return false;
        if (firstActionIndex(widget) > 0) return true;
        for (Widget[] children : new Widget[][]{widget.getChildren(), widget.getStaticChildren(),
            widget.getDynamicChildren(), widget.getNestedChildren()}) {
            if (children != null) for (Widget child : children) if (populated(child, visited)) return true;
        }
        return false;
    }

    /** Primitive click data captured together on the client thread; no worker widget reads. */
    static final class Selection {
        final int widgetId, index, actionIndex;
        final String option;
        final Rectangle bounds;

        Selection(int widgetId, int index, int actionIndex, String option, Rectangle bounds) {
            this.widgetId = widgetId;
            this.index = index;
            this.actionIndex = actionIndex;
            this.option = option;
            this.bounds = bounds == null ? new Rectangle(1, 1) : new Rectangle(bounds);
        }
    }
}
