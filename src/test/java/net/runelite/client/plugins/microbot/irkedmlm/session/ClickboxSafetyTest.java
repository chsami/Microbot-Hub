package net.runelite.client.plugins.microbot.irkedmlm.session;

import net.runelite.api.Point;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@link Session#clickboxMostlyOnScreen}, the pure geometry behind {@code hasSafeClickbox}.
 * The regression this locks in: a large/near clickbox that legitimately overflows a canvas edge must
 * still be considered clickable, or the click guards spin the camera forever (the old full-containment
 * check did exactly that). Only a null / degenerate / mostly-off-canvas box should be rejected.
 */
public class ClickboxSafetyTest {

    private static final int W = 765, H = 503;

    @Test
    public void fullyFramedBoxIsClickable() {
        assertTrue(Session.clickboxMostlyOnScreen(new Rectangle(300, 200, 80, 90), W, H));
    }

    @Test
    public void nullOrDegenerateBoxIsRejected() {
        assertFalse(Session.clickboxMostlyOnScreen(null, W, H));
        assertFalse(Session.clickboxMostlyOnScreen(new Rectangle(300, 200, 0, 50), W, H));
    }

    @Test
    public void tallNearBoxOverflowingTopIsStillClickable() {
        // The deadlock case: standing next to a vein/ladder at high zoom, its box overflows the top.
        // Old full-containment rejected this and turnTo could never fix it. Now: majority on-canvas.
        assertTrue(Session.clickboxMostlyOnScreen(new Rectangle(120, -50, 100, 600), W, H));
    }

    @Test
    public void edgeClippedButMostlyVisibleIsClickable() {
        assertTrue(Session.clickboxMostlyOnScreen(new Rectangle(-30, 150, 100, 100), W, H)); // ~64% shown
    }

    @Test
    public void heavilyClippedBoxIsRejected() {
        assertFalse(Session.clickboxMostlyOnScreen(new Rectangle(-80, 150, 100, 100), W, H)); // ~14% shown
    }

    @Test
    public void fullyOffscreenBoxIsRejected() {
        assertFalse(Session.clickboxMostlyOnScreen(new Rectangle(-500, 150, 100, 100), W, H));
    }

    /** The deposit box panel is drawn over the viewport: a box it touches at all is not clickable,
     *  because the click point is sampled anywhere inside the box and would hit the interface. */
    @Test
    public void panelCoveringTargetIsRejected() {
        Rectangle panel = new Rectangle(140, 100, 480, 300);
        assertFalse(Session.clickboxClearOfPanel(new Rectangle(300, 200, 80, 90), panel));  // fully behind
        assertFalse(Session.clickboxClearOfPanel(new Rectangle(100, 380, 80, 90), panel));  // clips a corner
        assertFalse(Session.clickboxClearOfPanel(null, panel));
    }

    @Test
    public void targetBesidePanelIsClickable() {
        Rectangle panel = new Rectangle(140, 100, 480, 300);
        assertTrue(Session.clickboxClearOfPanel(new Rectangle(640, 200, 80, 90), panel));   // right of panel
        assertTrue(Session.clickboxClearOfPanel(new Rectangle(300, 200, 80, 90), null));    // no panel open
    }

    /** The sack hidden above or below the player swings out to the side of a tall panel, the shorter way round. */
    @Test
    public void smallestTurnBringsTheTargetOutFromBehindThePanel() {
        Point player = new Point(500, 350);
        Rectangle panel = new Rectangle(350, 100, 300, 500);
        Rectangle sack = new Rectangle(540, 120, 40, 40); // up and to the right, behind the panel
        int turn = Session.clearingTurn(player, sack, panel, 1000, 700);
        assertTrue(turn > 0 && turn <= 512, "turns the short way, to the right: " + turn);
        // Straight down needs a clockwise or anticlockwise turn of the same size; either is fine, but it must clear.
        assertTrue(Session.clearingTurn(player, new Rectangle(480, 560, 40, 40), panel, 1000, 700) != 0);
    }

    @Test
    public void noTurnWhenThePanelFillsTheView() {
        assertEquals(0, Session.clearingTurn(new Point(260, 170), new Rectangle(270, 100, 30, 30),
                new Rectangle(10, 5, 490, 325), 765, 503));
    }
}
