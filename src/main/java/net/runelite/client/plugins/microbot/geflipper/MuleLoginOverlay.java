package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.GameState;

/**
 * Shows the Mule receiver's stats while the client is on the login screen.
 *
 * RuneLite draws its overlays only while a character is logged in, so a Mule that sits logged out between
 * trades would show nothing at all. This small window, owned by the RuneLite window, covers that gap. It
 * appears in the top-left corner of the game area (clear of the login box), never takes focus, and goes
 * away as soon as the character is logged in (the normal overlay takes over), when the overlay setting is
 * off, or when this plugin stops.
 */
final class MuleLoginOverlay implements AutoCloseable {
    static final int REFRESH_MS = 500;
    static final int MARGIN = 10;

    interface Source {
        /** The overlay is switched on, this plugin is running, and the role is Mule receiver. */
        boolean enabled();

        GameState gameState();

        /** The game canvas: its window owns the overlay window, or null while unknown. */
        Component anchor();

        /** The game area on screen, or null while it is not showing. */
        Rectangle canvasOnScreen();

        String title();

        List<MuleOverlayView.Row> rows();
    }

    /** Where the rows are drawn. Kept separate so the show/hide decisions can be tested without a display. */
    interface Surface {
        void show(Component anchor, String title, List<MuleOverlayView.Row> rows, Rectangle canvas);

        void hide();

        void dispose();
    }

    private final Source source;
    private final Surface surface;
    private Timer timer;
    private boolean closed;

    MuleLoginOverlay(Source source) {
        this(source, new SwingSurface());
    }

    MuleLoginOverlay(Source source, Surface surface) {
        this.source = Objects.requireNonNull(source);
        this.surface = Objects.requireNonNull(surface);
    }

    /** The states in which RuneLite shows no overlays of its own. */
    static boolean showsOn(GameState state) {
        if (state == null) return false;
        switch (state) {
            case LOGIN_SCREEN:
            case LOGIN_SCREEN_AUTHENTICATOR:
            case LOGGING_IN:
            case CONNECTION_LOST:
                return true;
            default:
                return false;
        }
    }

    /** Starts the periodic refresh. Without a display (tests, servers) there is nothing to show. */
    void start() {
        if (GraphicsEnvironment.isHeadless()) return;
        onEdt(() -> {
            if (closed || timer != null) return;
            timer = new Timer(REFRESH_MS, event -> refresh());
            timer.setCoalesce(true);
            timer.start();
            refresh();
        });
    }

    /** One show-or-hide decision. Must run on the Swing thread. */
    void refresh() {
        if (closed) return;
        try {
            if (!source.enabled() || !showsOn(source.gameState())) {
                surface.hide();
                return;
            }
            Component anchor = source.anchor();
            Rectangle canvas = source.canvasOnScreen();
            List<MuleOverlayView.Row> rows = source.rows();
            if (anchor == null || canvas == null || canvas.width <= 0 || canvas.height <= 0
                || rows == null || rows.isEmpty()) {
                surface.hide();
                return;
            }
            surface.show(anchor, source.title(), rows, canvas);
        } catch (RuntimeException unavailable) {
            // A display problem must never reach the game or the trading logic; hide until the next refresh.
            try {
                surface.hide();
            } catch (RuntimeException ignored) {
                // Nothing more to do.
            }
        }
    }

    @Override
    public void close() {
        onEdt(() -> {
            closed = true;
            if (timer != null) timer.stop();
            timer = null;
            surface.dispose();
        });
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    /** The real window: a focus-less window owned by the RuneLite window, so it never covers other programs. */
    static final class SwingSurface implements Surface {
        private JWindow window;
        private MuleStatsPanel panel;
        private Window owner;

        @Override
        public void show(Component anchor, String title, List<MuleOverlayView.Row> rows, Rectangle canvas) {
            Window newOwner = SwingUtilities.getWindowAncestor(anchor);
            if (newOwner == null) {
                hide();
                return;
            }
            if (window == null || owner != newOwner) {
                dispose();
                owner = newOwner;
                panel = new MuleStatsPanel();
                window = new JWindow(newOwner);
                window.setFocusableWindowState(false);
                window.setAutoRequestFocus(false);
                window.setContentPane(panel);
            }
            panel.setContent(title, rows);
            Dimension size = panel.getPreferredSize();
            int width = Math.max(1, Math.min(size.width, canvas.width - 2 * MARGIN));
            Rectangle bounds = new Rectangle(canvas.x + MARGIN, canvas.y + MARGIN, width, size.height);
            if (!bounds.equals(window.getBounds())) window.setBounds(bounds);
            if (!window.isVisible()) window.setVisible(true);
        }

        @Override
        public void hide() {
            if (window != null && window.isVisible()) window.setVisible(false);
        }

        @Override
        public void dispose() {
            if (window != null) window.dispose();
            window = null;
            panel = null;
            owner = null;
        }
    }
}
