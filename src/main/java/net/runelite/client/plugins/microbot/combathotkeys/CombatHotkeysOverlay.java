package net.runelite.client.plugins.microbot.combathotkeys;

import com.google.common.base.Strings;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

public class CombatHotkeysOverlay extends Overlay {

    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 10;
    private static final int PANEL_W = 310;
    private static final int ROW_H  = 16;
    private static final int PAD    = 6;

    private static final Color BG_COLOR     = new Color(0, 0, 0, 180);
    private static final Color BORDER_COLOR = new Color(255, 165, 0, 220);   // orange
    private static final Color LABEL_COLOR  = new Color(180, 180, 180);
    private static final Color VALUE_COLOR  = Color.WHITE;
    private static final Color OK_COLOR     = new Color(80, 220, 80);
    private static final Color ERR_COLOR    = new Color(255, 80, 80);
    private static final Color TITLE_COLOR  = new Color(255, 165, 0);

    private final CombatHotkeysPlugin plugin;
    private final CombatHotkeysConfig config;

    @Inject
    CombatHotkeysOverlay(CombatHotkeysPlugin plugin, CombatHotkeysConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setNaughty();
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            // ----------------------------------------------------------------
            // Dance tiles (original behaviour — always rendered when enabled)
            // ----------------------------------------------------------------
            if (config.yesDance()) {
                drawTile(graphics, config.tile1(), Color.GREEN, "Tile 1", new BasicStroke(2));
                drawTile(graphics, config.tile2(), Color.GREEN, "Tile 2", new BasicStroke(2));
            }

            // ----------------------------------------------------------------
            // Debug panel — only rendered when debug mode is on
            // ----------------------------------------------------------------
            if (config.debugMode()) {
                renderDebugPanel(graphics);
            }

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // DEBUG PANEL
    // -------------------------------------------------------------------------

    /**
     * Renders a compact diagnostic panel in the top-left corner of the game
     * canvas.  All data is read atomically from the plugin's public fields so
     * this method never touches game state.
     *
     * Layout (each row is ROW_H px tall):
     *   ┌─ Combat Hotkeys DEBUG (v1.1.2) ──────────────────────────┐
     *   │ Last key received : protectMelee                          │
     *   │ Key age           : 0.3 s ago                            │
     *   │ Last action       : toggle prayer PROTECT_MELEE          │
     *   │ Submitted         : 4                                    │
     *   │ Succeeded         : 4   Failed: 0                        │
     *   │ Last error        : -                                    │
     *   │ Logged in         : true                                 │
     *   │ Thread            : AWT-EventQueue-0                     │
     *   └──────────────────────────────────────────────────────────┘
     *
     * Reading this panel tells you at a glance:
     *  - "Last key received" never updates  → keyPressed is not firing; the
     *    keybind in config does not match what you're pressing, or the
     *    KeyManager is not registered.
     *  - "Last key received" updates but "Last action" doesn't → dispatch()
     *    was reached but the executor was null/shutdown (logged as an error).
     *  - Submitted increments but Succeeded doesn't → Rs2Prayer.toggle()
     *    threw; check "Last error" and the RuneLite log.
     *  - Everything looks fine but prayer doesn't toggle → Rs2Prayer itself
     *    has a bug (e.g. prayer tab not open, out of prayer points).
     */
    private void renderDebugPanel(Graphics2D graphics) {
        Font originalFont = graphics.getFont();
        Font monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 11);
        graphics.setFont(monoFont);
        FontMetrics fm = graphics.getFontMetrics(monoFont);

        // Collect all rows to render first so we can size the background
        String keyReceived  = plugin.getLastKeyReceived().get();
        String lastAction   = plugin.getLastActionDispatched().get();
        int    submitted    = plugin.getTotalActionsSubmitted().get();
        int    succeeded    = plugin.getTotalActionsSucceeded().get();
        int    failed       = plugin.getTotalActionsFailed().get();
        String lastError    = plugin.getLastError().get();
        boolean loggedIn    = Microbot.isLoggedIn();

        // Age of last keypress
        String keyAge;
        long ts = plugin.getLastKeyTimestamp();
        if (ts == 0) {
            keyAge = "never";
        } else {
            long ageSec = (Instant.now().toEpochMilli() - ts);
            keyAge = String.format("%.1f s ago", ageSec / 1000.0);
        }

        String[] labels = {
                "Last key     :",
                "Key age      :",
                "Last action  :",
                "Submitted    :",
                "Succeeded    :",
                "Failed       :",
                "Last error   :",
                "Logged in    :",
        };
        String[] values = {
                keyReceived,
                keyAge,
                lastAction,
                String.valueOf(submitted),
                String.valueOf(succeeded),
                String.valueOf(failed),
                lastError,
                String.valueOf(loggedIn),
        };
        Color[] valueColors = {
                VALUE_COLOR,
                LABEL_COLOR,
                VALUE_COLOR,
                VALUE_COLOR,
                succeeded > 0 ? OK_COLOR : LABEL_COLOR,
                failed    > 0 ? ERR_COLOR : LABEL_COLOR,
                lastError.equals("-") ? LABEL_COLOR : ERR_COLOR,
                loggedIn ? OK_COLOR : ERR_COLOR,
        };

        int rows   = labels.length;
        int titleH = ROW_H + 4;
        int totalH = titleH + rows * ROW_H + PAD * 2;
        int x      = PANEL_X;
        int y      = PANEL_Y;

        // Background
        graphics.setColor(BG_COLOR);
        graphics.fillRoundRect(x, y, PANEL_W, totalH, 8, 8);

        // Border
        graphics.setColor(BORDER_COLOR);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.drawRoundRect(x, y, PANEL_W, totalH, 8, 8);

        // Title bar
        graphics.setColor(TITLE_COLOR);
        Font titleFont = monoFont.deriveFont(Font.BOLD, 11f);
        graphics.setFont(titleFont);
        String title = "Combat Hotkeys DEBUG  v" + CombatHotkeysPlugin.version;
        graphics.drawString(title, x + PAD, y + PAD + fm.getAscent());
        graphics.setFont(monoFont);

        // Divider under title
        graphics.setColor(BORDER_COLOR);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawLine(x + 1, y + titleH, x + PANEL_W - 1, y + titleH);

        // Data rows
        int rowY = y + titleH + PAD;
        for (int i = 0; i < rows; i++) {
            int baseY = rowY + i * ROW_H + fm.getAscent();

            // Label (dimmer)
            graphics.setColor(LABEL_COLOR);
            graphics.drawString(labels[i], x + PAD, baseY);

            // Value (coloured)
            graphics.setColor(valueColors[i]);
            int labelW = fm.stringWidth(labels[i]);
            // Truncate long values so they don't overflow the panel
            String val = truncate(values[i], fm, PANEL_W - labelW - PAD * 3);
            graphics.drawString(val, x + PAD + labelW + 4, baseY);
        }

        graphics.setFont(originalFont);
    }

    /** Truncate a string to fit within maxWidth pixels, appending "…" if needed. */
    private String truncate(String s, FontMetrics fm, int maxWidth) {
        if (s == null) return "null";
        if (fm.stringWidth(s) <= maxWidth) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    // -------------------------------------------------------------------------
    // DANCE TILE DRAWING (unchanged from original)
    // -------------------------------------------------------------------------

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color,
                          @Nullable String label, Stroke borderStroke)
    {
        if (point == null) return;

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) return;

        if (point.distanceTo(playerLocation) >= 32) return;

        LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient(), point);
        if (lp == null) return;

        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, 50), borderStroke);
        }

        if (!Strings.isNullOrEmpty(label)) {
            Point canvasTextLocation = Perspective.getCanvasTextLocation(
                    Microbot.getClient(), graphics, lp, label, 0);
            if (canvasTextLocation != null) {
                OverlayUtil.renderTextLocation(graphics, canvasTextLocation, label, color);
            }
        }
    }
}
