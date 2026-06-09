package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

/**
 * Guide section: in-dashboard reference for panels + config options. Lives at
 * the bottom of the floating window. Default ON; users untick
 * {@code showGuide} in the Layout config once they're familiar.
 *
 * <p>Content is static HTML rendered into a {@link JEditorPane} so we get
 * hyperlinks, bullets, and bold formatting for free.
 */
public class GuidePanel extends DashboardSection {

    private static final String GUIDE_HTML =
            "<html><body>" +
            "<p style='margin-top:0'><b>Panels</b> (toggle visibility in plugin config &rarr; Layout):</p>" +
            "<ul>" +
            "<li><b>Player</b>: name, combat level, login state, world, profile, session duration, position, animation.</li>" +
            "<li><b>Active Scripts</b>: user-facing Microbot plugins currently enabled, per-plugin runtime, Stop button per row.</li>" +
            "<li><b>Inventory</b>: slot grid with item names + quantities; noted items styled distinctly.</li>" +
            "<li><b>Skills</b>: all 22 skills with current level, total XP, gain since session start, rolling 5-min XP/hr, and an ETA to your target level (or the next level while training).</li>" +
            "<li><b>Nearby NPCs</b>: NPC list sorted by distance, max-distance spinner, random-event NPCs highlighted orange.</li>" +
            "<li><b>Antiban State</b>: tells a silent stall apart from an intentional anti-AFK pause. Shows the current state, antiban on/off, action cooldown, micro break, global pause, and blocking-event handlers.</li>" +
            "<li><b>XP Over Time</b>: Java2D line chart with skill + window selectors (5m to 24h). Selection persists across launches.</li>" +
            "<li><b>Event Log</b>: rolling 10-entry ring buffer of login / logout / world-hop events.</li>" +
            "</ul>" +
            "<p><b>Config options</b>:</p>" +
            "<ol>" +
            "<li><b>Auto-open dashboard on startup</b>: open the floating window when the plugin enables.</li>" +
            "<li><b>Poll interval (sec)</b>: refresh rate from game state (1-60, default 5).</li>" +
            "<li><b>Nearby NPCs max distance</b>: filter for the NPC panel (1-200 tiles, default 20).</li>" +
            "<li><b>Layout toggles</b>: nine on/off switches, one per panel (including this Guide).</li>" +
            "<li><b>Discord webhook URL</b>: paste your channel webhook (field is masked; blank disables Discord).</li>" +
            "<li><b>Notify on level-up / session lifecycle / alert threshold</b>: three independent toggles.</li>" +
            "<li><b>Alert thresholds</b>: comma-separated <code>SKILL:LEVEL</code> pairs (e.g. <code>MINING:60, WOODCUTTING:80</code>). Crossings show an in-window banner and (if Discord is set) send a notification.</li>" +
            "<li><b>Skill targets (ETA)</b>: comma-separated <code>SKILL:LEVEL</code> pairs (e.g. <code>MINING:70, AGILITY:60</code>) that drive the Skills ETA column.</li>" +
            "</ol>" +
            "<p style='margin-bottom:0'>Hide this section by unticking <b>Show Guide</b> in plugin config &rarr; Layout.</p>" +
            "</body></html>";

    public GuidePanel(GameStatePoller poller) {
        super("Guide", poller);
        setSubtitle("(hide via Layout config once familiar)");

        JEditorPane editor = new JEditorPane();
        editor.setEditable(false);
        editor.setOpaque(false);
        editor.setBorder(new EmptyBorder(4, 4, 4, 4));

        // Use an HTMLEditorKit with a stylesheet that matches the RuneLite
        // dark theme.
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet css = kit.getStyleSheet();
        String font = FontManager.getRunescapeSmallFont().getFamily();
        int fontSize = FontManager.getRunescapeSmallFont().getSize();
        css.addRule("body { color: #c0c0c0; font-family: '" + font + "'; font-size: " + fontSize + "pt; }");
        css.addRule("b { color: #ffffff; }");
        css.addRule("p { margin: 4px 0; }");
        css.addRule("ul, ol { margin-top: 4px; margin-bottom: 8px; padding-left: 18px; }");
        css.addRule("li { margin: 2px 0; }");
        css.addRule("code { color: #ffeb91; font-family: monospace; }");

        editor.setEditorKit(kit);
        editor.setText(GUIDE_HTML);
        editor.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        editor.setForeground(Color.LIGHT_GRAY);

        JScrollPane scroll = new JScrollPane(editor);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setPreferredSize(new Dimension(780, 320));
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        // Static content; nothing to update.
    }
}
