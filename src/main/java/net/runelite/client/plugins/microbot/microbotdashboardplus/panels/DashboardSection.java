package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/**
 * Base for every dashboard section panel. Provides:
 * <ul>
 *     <li>A header row that NEVER truncates the title: a {@link GridBagLayout}
 *         with title + subtitle pinned left (weightx=0), a flex spacer in the
 *         middle (weightx=1), and controls pinned right (weightx=0).</li>
 *     <li>BorderLayout body with the section content in the center.</li>
 *     <li>Auto-registration with the {@link GameStatePoller} on construct and
 *         {@link #detach()} cleanup on plugin shutdown.</li>
 * </ul>
 *
 * <p>The header uses GridBagLayout rather than BorderLayout(WEST/EAST) +
 * FlowLayout so the title JLabel doesn't auto-ellipsize when its preferred
 * width is calculated tightly against the custom RuneLite font.
 */
public abstract class DashboardSection extends JPanel {

    protected final GameStatePoller poller;
    private final Consumer<PollSnapshot> snapshotListener;
    private final JLabel subtitleLabel;
    private final JPanel headerRight;

    protected DashboardSection(String title, GameStatePoller poller) {
        super(new BorderLayout(0, 4));
        this.poller = poller;
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel header = new JPanel(new GridBagLayout());
        header.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 0;
        c.weighty = 0;
        c.insets = new Insets(0, 0, 0, 6);

        // Title (col 0).
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        c.gridx = 0;
        header.add(titleLabel, c);

        // Subtitle (col 1).
        subtitleLabel = new JLabel("");
        subtitleLabel.setForeground(Color.GRAY);
        subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
        c.gridx = 1;
        header.add(subtitleLabel, c);

        // Flex spacer (col 2).
        c.gridx = 2;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        header.add(spacer, c);

        // Controls (col 3).
        c.gridx = 3;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 6, 0, 0);
        headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        headerRight.setOpaque(false);
        header.add(headerRight, c);

        add(header, BorderLayout.NORTH);

        snapshotListener = this::applySnapshot;
        poller.addListener(snapshotListener);
    }

    public void detach() {
        poller.removeListener(snapshotListener);
    }

    protected void setSubtitle(String text) {
        subtitleLabel.setText(text == null ? "" : text);
    }

    /** Add an in-header control (right-aligned). */
    protected void addHeaderControl(Component component) {
        headerRight.add(component);
    }

    /** Called on every snapshot. Always invoked on the EDT. */
    protected abstract void applySnapshot(PollSnapshot snapshot);
}
