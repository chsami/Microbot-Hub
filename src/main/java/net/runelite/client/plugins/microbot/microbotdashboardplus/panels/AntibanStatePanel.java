package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Antiban state section: shows whether the script is genuinely running or is
 * being held by intentional anti-AFK behavior.
 *
 * <p>When a session looks stuck this panel tells you whether it is a real
 * stall (nothing holding it, so something is wrong) or a deliberate pause (a
 * micro break or action cooldown is running, which is expected). It reads the
 * in-process antiban flags, the global pause switch, and the blocking-event
 * handlers; nothing here touches the disk.
 *
 * <ul>
 *     <li><b>State</b> - one-line plain reason ("Running", "Micro break in
 *         progress", "All scripts paused", and so on).</li>
 *     <li><b>Antiban</b> - whether antiban is globally enabled.</li>
 *     <li><b>Action cooldown</b> / <b>Micro break</b> - the two anti-AFK holds.</li>
 *     <li><b>All scripts paused</b> - the global pause switch.</li>
 *     <li><b>Blocking events</b> - registered handler count, with "(running)"
 *         appended when one is executing.</li>
 * </ul>
 */
public class AntibanStatePanel extends DashboardSection {

    private final JLabel state = mkValue();
    private final JLabel antiban = mkValue();
    private final JLabel cooldown = mkValue();
    private final JLabel microBreak = mkValue();
    private final JLabel paused = mkValue();
    private final JLabel blocking = mkValue();

    public AntibanStatePanel(GameStatePoller poller) {
        super("Antiban State", poller);
        setSubtitle("(stall vs intentional pause)");
        add(buildGrid(), java.awt.BorderLayout.CENTER);
    }

    private JPanel buildGrid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 4, 2, 4);

        int row = 0;
        addRow(grid, c, row++, "State", state);
        addRow(grid, c, row++, "Antiban", antiban);
        addRow(grid, c, row++, "Action cooldown", cooldown);
        addRow(grid, c, row++, "Micro break", microBreak);
        addRow(grid, c, row++, "All scripts paused", paused);
        addRow(grid, c, row, "Blocking events", blocking);

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
        l.setForeground(Color.WHITE);
        l.setFont(FontManager.getRunescapeSmallFont());
        return l;
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null || snapshot.getAntibanState() == null) {
            state.setText("--");
            return;
        }
        PollSnapshot.AntibanState a = snapshot.getAntibanState();

        String summary = a.getSummary() == null ? "--" : a.getSummary();
        state.setText(summary);
        // Green when genuinely running, gold when intentionally held, gray when
        // unknown. Lets the user read the state at a glance.
        boolean held = a.isAllScriptsPaused() || a.isMicroBreakActive()
                || a.isActionCooldownActive() || Boolean.TRUE.equals(a.getBlockingEventRunning());
        if ("--".equals(summary) || "unavailable".equals(summary)) {
            state.setForeground(Color.GRAY);
        } else if (held) {
            state.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
        } else {
            state.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        }

        antiban.setText(a.isAntibanEnabled() ? "on" : "off");
        antiban.setForeground(a.isAntibanEnabled() ? Color.WHITE : Color.GRAY);

        setBool(cooldown, a.isActionCooldownActive());
        setBool(microBreak, a.isMicroBreakActive());
        setBool(paused, a.isAllScriptsPaused());

        blocking.setText(blockingText(a));
        blocking.setForeground(Color.WHITE);
    }

    /** "yes" highlighted gold when an anti-AFK hold is active, "no" muted otherwise. */
    private static void setBool(JLabel label, boolean active) {
        label.setText(active ? "yes" : "no");
        label.setForeground(active ? ColorScheme.PROGRESS_INPROGRESS_COLOR : Color.GRAY);
    }

    private static String blockingText(PollSnapshot.AntibanState a) {
        int count = a.getBlockingEventCount();
        Boolean running = a.getBlockingEventRunning();
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(count == 1 ? " handler" : " handlers");
        if (Boolean.TRUE.equals(running)) {
            sb.append(" (running)");
        }
        return sb.toString();
    }
}
