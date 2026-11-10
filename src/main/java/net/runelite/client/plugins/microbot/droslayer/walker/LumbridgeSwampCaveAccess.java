package net.runelite.client.plugins.microbot.droslayer.walker;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.support.DroSetupMouse;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

/** The Lumbridge Swamp cave warning is a widget, not a normal numbered dialogue. */
final class LumbridgeSwampCaveAccess {
    static boolean routeRequiresLight(List<WorldPoint> path) {
        if (path == null) return false;
        for (int i = 1; i < path.size(); i++)
            if (isEntrance(path.get(i - 1), path.get(i))) return true;
        return false;
    }

    static boolean isEntrance(Transport transport) {
        if (transport == null || transport.getType() != TransportType.TRANSPORT || transport.getObjectId() != 5947
                || !"Climb-down".equalsIgnoreCase(transport.getAction())) return false;
        return isEntrance(transport.getOrigin(), transport.getDestination());
    }

    private static boolean isEntrance(WorldPoint from, WorldPoint to) {
        return from != null && to != null && from.getPlane() == 0 && to.getPlane() == 0
                && from.getX() >= 3167 && from.getX() <= 3171 && from.getY() >= 3171 && from.getY() <= 3174
                && to.getX() >= 3166 && to.getX() <= 3172 && to.getY() >= 9570 && to.getY() <= 9575;
    }

    static boolean inputAllowed() {
        return !Thread.currentThread().isInterrupted() && !Microbot.pauseAllScripts.get()
                && !InputArbiter.isHuman() && DroSetupMouse.isHostInputAllowed();
    }

    static final class Warning {
        final int group;
        final Selection selection;
        Warning(int group, Selection selection) { this.group = group; this.selection = selection; }
    }

    static final class Selection {
        final Widget identity;
        final int id, index, operation;
        final MenuAction type;
        final String option;
        final Rectangle bounds;
        Selection(Widget identity, int operation, MenuAction type, String option, Rectangle bounds) {
            this.identity = identity; this.id = identity.getId(); this.index = identity.getIndex();
            this.operation = operation; this.type = type; this.option = option; this.bounds = new Rectangle(bounds);
        }
        NewMenuEntry menu() {
            return new NewMenuEntry().widget(identity).param0(index).param1(id).identifier(operation)
                    .type(type).option(option).target("").itemId(-1).forceLeftClick(false);
        }
    }

    /** CLIENT THREAD. Restrict the affirmative to the same interface as the observed cave warning. */
    static Warning warning(Client client) {
        Widget[] roots = client.getWidgetRoots();
        if (roots == null) return null;
        Map<Integer, List<Widget>> groups = new LinkedHashMap<>();
        Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Widget root : roots) collect(root, seen, groups);
        for (var group : groups.entrySet()) {
            if (group.getValue().stream().noneMatch(widget -> isQuestion(widget.getText()))) continue;
            for (Widget widget : group.getValue()) {
                if (!isAffirmative(widget.getText())) continue;
                Selection choice = selection(widget);
                if (choice != null) return new Warning(group.getKey(), choice);
            }
            return new Warning(group.getKey(), null); // A loading panel still blocks object clicks.
        }
        return new Warning(-1, null);
    }

    private static void collect(Widget widget, Set<Widget> seen, Map<Integer, List<Widget>> groups) {
        if (widget == null || !seen.add(widget) || widget.isHidden()) return;
        groups.computeIfAbsent(widget.getId() >>> 16, ignored -> new ArrayList<>()).add(widget);
        for (Widget[] children : new Widget[][] {widget.getChildren(), widget.getStaticChildren(),
                widget.getDynamicChildren(), widget.getNestedChildren()}) {
            if (children != null) for (Widget child : children) collect(child, seen, groups);
        }
    }

    private static Selection selection(Widget text) {
        Rectangle bounds = text.getBounds();
        if (bounds == null || bounds.isEmpty()) return null;
        if (!text.isIf3()) return new Selection(text, 0, MenuAction.WIDGET_TYPE_1, "Yes", bounds);
        Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Widget widget = text; widget != null && seen.add(widget) && !widget.isHidden(); widget = widget.getParent()) {
            if (widget.getId() >>> 16 != text.getId() >>> 16) break;
            String[] actions = widget.getActions();
            if (actions == null) continue;
            for (int i = 0; i < actions.length; i++) {
                String action = normalized(actions[i]);
                if (action.equals("yes") || action.equals("select") || isAffirmative(action))
                    return new Selection(widget, i + 1, MenuAction.CC_OP, actions[i], bounds);
            }
        }
        return null;
    }

    static boolean accept(Client client, Transport transport, Warning expected) {
        if (!isEntrance(transport) || expected == null || expected.group < 0 || expected.selection == null || !inputAllowed()) return false;
        Selection fresh = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!inputAllowed() || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
                    || client.getLocalPlayer().getWorldLocation() == null
                    || client.getLocalPlayer().getWorldLocation().distanceTo(transport.getOrigin()) > 10
                    || !CaveLightSource.hasLight()) return null;
            Warning current = warning(client);
            return current != null && current.group == expected.group && current.selection != null
                    && current.selection.identity == expected.selection.identity ? current.selection : null;
        }).orElse(null);
        if (fresh == null || !inputAllowed()) return false;
        // Bind the widget operation explicitly so an old Dark hole action cannot receive this click.
        Microbot.doInvoke(fresh.menu(), fresh.bounds);
        return true;
    }

    static boolean isQuestion(String text) {
        String value = normalized(text);
        return value.contains("cave is very dark") && value.contains("light source") && value.contains("tinderbox");
    }
    static boolean isAffirmative(String text) {
        return normalized(text).matches("(?:yes[.,]?\\s*)?(?:i'm|i am) not scared of the dark[.!]?");
    }
    private static String normalized(String text) {
        return text == null ? "" : text.replaceAll("<[^>]*>", " ").replace('\u2019', '\'')
                .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private LumbridgeSwampCaveAccess() { }
}
