package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuleLoginOverlayTest {
    @Test
    void showsOnlyWhereRuneLiteDrawsNoOverlayOfItsOwn() {
        for (GameState state : GameState.values()) {
            boolean expected = state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR
                || state == GameState.LOGGING_IN || state == GameState.CONNECTION_LOST;
            assertEquals(expected, MuleLoginOverlay.showsOn(state), state.name());
        }
        assertFalse(MuleLoginOverlay.showsOn(null));
    }

    @Test
    void showsTheRowsOnTheLoginScreenAtTheGameAreasCorner() {
        Fixture f = new Fixture();
        f.state = GameState.LOGIN_SCREEN;
        f.overlay.refresh();
        assertEquals(1, f.surface.shows.size());
        Show shown = f.surface.shows.get(0);
        assertSame(f.anchor, shown.anchor);
        assertEquals(MuleOverlayView.title(), shown.title);
        assertEquals(f.rows, shown.rows);
        assertEquals(new Rectangle(100, 50, 765, 503), shown.canvas);
    }

    @Test
    void staysShownForTheWholeLoginSequenceAndHidesOnceLoggedIn() {
        Fixture f = new Fixture();
        for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.LOGIN_SCREEN_AUTHENTICATOR,
            GameState.LOGGING_IN, GameState.CONNECTION_LOST}) {
            f.state = state;
            f.overlay.refresh();
        }
        assertEquals(4, f.surface.shows.size());
        assertEquals(0, f.surface.hides);
        f.state = GameState.LOGGED_IN;
        f.overlay.refresh();
        assertEquals(1, f.surface.hides, "The normal in-game overlay takes over");
        assertEquals(4, f.surface.shows.size());
    }

    @Test
    void hidesWhenDisabledOrTheGameAreaIsNotShowing() {
        Fixture f = new Fixture();
        f.state = GameState.LOGIN_SCREEN;
        f.enabled = false;
        f.overlay.refresh();
        assertEquals(1, f.surface.hides);
        assertTrue(f.surface.shows.isEmpty());

        f.enabled = true;
        f.canvas = null;
        f.overlay.refresh();
        assertEquals(2, f.surface.hides);

        f.canvas = new Rectangle(0, 0, 0, 0);
        f.overlay.refresh();
        assertEquals(3, f.surface.hides);

        f.canvas = new Rectangle(100, 50, 765, 503);
        f.anchor = null;
        f.overlay.refresh();
        assertEquals(4, f.surface.hides);

        f.anchor = new JPanel();
        f.rows = Collections.emptyList();
        f.overlay.refresh();
        assertEquals(5, f.surface.hides);
        assertTrue(f.surface.shows.isEmpty());
    }

    @Test
    void aFailingSourceHidesTheWindowInsteadOfThrowing() {
        Fixture f = new Fixture();
        f.state = GameState.LOGIN_SCREEN;
        f.failRows = true;
        f.overlay.refresh();
        assertEquals(1, f.surface.hides);
        assertTrue(f.surface.shows.isEmpty());

        f.failRows = false;
        f.surface.failHide = true;
        f.failEnabled = true;
        assertDoesNotThrow(f.overlay::refresh, "Even a failing hide must not escape");
    }

    @Test
    void closeDisposesTheWindowAndStopsAnyFurtherShowing() throws Exception {
        Fixture f = new Fixture();
        f.state = GameState.LOGIN_SCREEN;
        f.overlay.refresh();
        f.overlay.close();
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, f.surface.disposes);
        f.overlay.refresh();
        assertEquals(1, f.surface.shows.size(), "Nothing is shown after close");
        f.overlay.close();
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(2, f.surface.disposes, "Closing twice is harmless");
    }

    @Test
    void withoutADisplayStartingDoesNothing() throws Exception {
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless());
        Fixture f = new Fixture();
        f.state = GameState.LOGIN_SCREEN;
        f.overlay.start();
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue(f.surface.shows.isEmpty());
        assertEquals(0, f.surface.hides);
    }

    private static final class Show {
        final Component anchor;
        final String title;
        final List<MuleOverlayView.Row> rows;
        final Rectangle canvas;

        Show(Component anchor, String title, List<MuleOverlayView.Row> rows, Rectangle canvas) {
            this.anchor = anchor;
            this.title = title;
            this.rows = rows;
            this.canvas = canvas;
        }
    }

    private static final class FakeSurface implements MuleLoginOverlay.Surface {
        final List<Show> shows = new ArrayList<>();
        int hides;
        int disposes;
        boolean failHide;

        @Override public void show(Component anchor, String title, List<MuleOverlayView.Row> rows, Rectangle canvas) {
            shows.add(new Show(anchor, title, rows, canvas));
        }

        @Override public void hide() {
            hides++;
            if (failHide) throw new IllegalStateException("window unavailable");
        }

        @Override public void dispose() { disposes++; }
    }

    private static final class Fixture {
        final FakeSurface surface = new FakeSurface();
        final MuleLoginOverlay overlay;
        volatile boolean enabled = true;
        volatile boolean failRows;
        volatile boolean failEnabled;
        volatile GameState state = GameState.LOGIN_SCREEN;
        volatile Component anchor = new JPanel();
        volatile Rectangle canvas = new Rectangle(100, 50, 765, 503);
        volatile List<MuleOverlayView.Row> rows = MuleOverlayView.receiverRows(null, null, false, "");

        Fixture() {
            overlay = new MuleLoginOverlay(new MuleLoginOverlay.Source() {
                @Override public boolean enabled() {
                    if (failEnabled) throw new IllegalStateException("script unavailable");
                    return enabled;
                }
                @Override public GameState gameState() { return state; }
                @Override public Component anchor() { return anchor; }
                @Override public Rectangle canvasOnScreen() { return canvas; }
                @Override public String title() { return MuleOverlayView.title(); }
                @Override public List<MuleOverlayView.Row> rows() {
                    if (failRows) throw new IllegalStateException("rows unavailable");
                    return rows;
                }
            }, surface);
        }
    }
}
