package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinishCollectTest {
    @Test void usesVerifiedDynamicChildAndCopiesBounds() {
        Node root = root();
        Rectangle result = FinishCollect.bounds(root.widget, 765, 503);
        assertEquals(root.child.bounds, result);
        assertNotSame(root.child.bounds, result);
    }
    @Test void missingContainerOrChildCannotAuthorizeCollection() {
        assertNull(FinishCollect.bounds(null, 765, 503));
        Node root = root(); root.child = null;
        assertNull(FinishCollect.bounds(root.widget, 765, 503));
    }
    @Test void hiddenParentAndHiddenChildCannotAuthorizeCollection() {
        Node root = root(); root.hidden = true;
        assertNull(FinishCollect.bounds(root.widget, 765, 503));
        root.hidden = false; root.child.hidden = true;
        assertNull(FinishCollect.bounds(root.widget, 765, 503));
    }
    @Test void unrelatedWidgetOrWrongDynamicIndexIsRejected() {
        Node root = root(); root.id++;
        assertNull(FinishCollect.bounds(root.widget, 765, 503));
        root = root(); root.child.index = 1;
        assertNull(FinishCollect.bounds(root.widget, 765, 503));
        root = root(); root.child.id++;
        assertNull(FinishCollect.bounds(root.widget, 765, 503));
    }
    @Test void bankOnlyOrMissingOperationCannotAuthorizeInventoryCollection() {
        Node root = root();
        for (String[] actions : new String[][] {null, {}, {"Collect to bank"}, {"Repeat Offer"}}) {
            root.child.actions = actions;
            assertNull(FinishCollect.bounds(root.widget, 765, 503));
        }
    }
    @Test void offCanvasOrMalformedBoundsAreRejected() {
        Node root = root();
        for (Rectangle bounds : new Rectangle[] {null, new Rectangle(1, 1, 0, 20),
                new Rectangle(-1, 1, 50, 20), new Rectangle(750, 1, 50, 20),
                new Rectangle(1, 499, 50, 20), new Rectangle(Integer.MAX_VALUE, 1, 20, 20)}) {
            root.child.bounds = bounds;
            assertNull(FinishCollect.bounds(root.widget, 765, 503));
        }
    }
    @Test void actionsOnContainerCannotSubstituteForTheDynamicOperation() {
        Node root = root(); root.actions = new String[] {"Collect to inventory"}; root.child.actions = null;
        assertNull(FinishCollect.bounds(root.widget, 765, 503));
    }
    private static Node root() {
        Node root = new Node(); root.index = -1; root.actions = null; root.child = new Node();
        return root;
    }
    private static final class Node {
        int id = InterfaceID.GeOffers.COLLECTALL;
        int index;
        boolean hidden;
        Node child;
        String[] actions = {"Collect to inventory", "Collect to bank"};
        Rectangle bounds = new Rectangle(100, 100, 80, 20);
        final Widget widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getId": return id;
                    case "getIndex": return index;
                    case "isHidden": return hidden;
                    case "getActions": return actions;
                    case "getBounds": return bounds;
                    case "getChild": return child == null ? null : child.widget;
                    default: throw new AssertionError("Unexpected widget read: " + method.getName());
                }
            });
    }
}
