package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;

/**
 * Player section: name, combat level, login state, world, profile, session
 * duration, position, animation. Static key-value grid laid out with
 * GridBagLayout.
 */
public class PlayerPanel extends DashboardSection {

    private final long sessionStartMillis = System.currentTimeMillis();

    private final JLabel name = mkValue();
    private final JLabel combat = mkValue();
    private final JLabel loggedIn = mkValue();
    private final JLabel gameState = mkValue();
    private final JLabel world = mkValue();
    private final JLabel profile = mkValue();
    private final JLabel sessionDuration = mkValue();
    private final JLabel position = mkValue();
    private final JLabel animation = mkValue();

    public PlayerPanel(GameStatePoller poller) {
        super("Player", poller);
        add(buildGrid(), java.awt.BorderLayout.CENTER);
    }

    private JPanel buildGrid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 4, 2, 4);
        c.weightx = 0;

        int row = 0;
        addRow(grid, c, row++, "Name", name);
        addRow(grid, c, row++, "Combat", combat);
        addRow(grid, c, row++, "Logged in", loggedIn);
        addRow(grid, c, row++, "Game state", gameState);
        addRow(grid, c, row++, "World", world);
        addRow(grid, c, row++, "Profile", profile);
        addRow(grid, c, row++, "Session", sessionDuration);
        addRow(grid, c, row++, "Position", position);
        addRow(grid, c, row, "Animation", animation);

        return grid;
    }

    private static void addRow(JPanel grid, GridBagConstraints c, int row, String label, JLabel value) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        lbl.setFont(FontManager.getRunescapeSmallFont());
        grid.add(lbl, c);

        c.gridx = 1;
        c.weightx = 1.0;
        grid.add(value, c);
    }

    private static JLabel mkValue() {
        JLabel l = new JLabel("--");
        l.setForeground(java.awt.Color.WHITE);
        l.setFont(FontManager.getRunescapeSmallFont());
        return l;
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null) return;
        name.setText(safe(snapshot.getPlayerName()));
        combat.setText(snapshot.getCombatLevel() <= 0 ? "--" : Integer.toString(snapshot.getCombatLevel()));
        loggedIn.setText(snapshot.isLoggedIn() ? "yes" : "no");
        gameState.setText(safe(snapshot.getGameState()));
        world.setText(snapshot.getWorldId() <= 0 ? "--" : Integer.toString(snapshot.getWorldId()));
        profile.setText(safe(snapshot.getProfileName()));
        sessionDuration.setText(formatDuration(System.currentTimeMillis() - sessionStartMillis));
        position.setText(safe(snapshot.getPositionText()));
        animation.setText(safe(snapshot.getAnimationText()));
    }

    private static String safe(String s) {
        return (s == null || s.isEmpty()) ? "--" : s;
    }

    private static String formatDuration(long millis) {
        if (millis < 0) millis = 0;
        Duration d = Duration.ofMillis(millis);
        long h = d.toHours();
        long m = d.minusHours(h).toMinutes();
        long s = d.minusHours(h).minusMinutes(m).getSeconds();
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }
}
