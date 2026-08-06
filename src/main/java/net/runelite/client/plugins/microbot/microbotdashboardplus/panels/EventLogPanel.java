package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Event Log section: rolling ring buffer of the last 10 state-change events
 * observed by the dashboard (login / logout / world hop). Spans full width.
 *
 * <p>Events are derived from snapshot diffs. The ring is in-memory only.
 */
public class EventLogPanel extends DashboardSection {

    private static final int RING_SIZE = 10;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter
            .ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DefaultListModel<LogEntry> model = new DefaultListModel<>();
    private final Deque<LogEntry> ring = new ArrayDeque<>(RING_SIZE);
    private final JList<LogEntry> list;

    private boolean lastLoggedIn = false;
    private int lastWorld = -1;
    private String lastPlayerName = null;
    private boolean firstSnapshot = true;

    public EventLogPanel(GameStatePoller poller) {
        super("Event Log", poller);
        setSubtitle("(last " + RING_SIZE + ")");

        list = new JList<>(model);
        list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        list.setForeground(Color.WHITE);
        list.setFont(FontManager.getRunescapeSmallFont());
        list.setCellRenderer(new EntryRenderer());

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setPreferredSize(new Dimension(780, 140));
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null) return;

        if (firstSnapshot) {
            // Establish baseline without firing events.
            lastLoggedIn = snapshot.isLoggedIn();
            lastWorld = snapshot.getWorldId();
            lastPlayerName = snapshot.getPlayerName();
            firstSnapshot = false;
        } else {
            if (snapshot.isLoggedIn() != lastLoggedIn) {
                add(snapshot.getTimestampMillis(),
                        snapshot.isLoggedIn() ? "Logged in" : "Logged out");
                lastLoggedIn = snapshot.isLoggedIn();
            }
            if (lastWorld != snapshot.getWorldId() && snapshot.getWorldId() > 0) {
                add(snapshot.getTimestampMillis(),
                        "World hop -> " + snapshot.getWorldId());
                lastWorld = snapshot.getWorldId();
            }
            String n = snapshot.getPlayerName();
            if (n != null && !n.equals(lastPlayerName) && !"--".equals(n)) {
                add(snapshot.getTimestampMillis(), "Player: " + n);
                lastPlayerName = n;
            }
        }
    }

    private void add(long timestampMillis, String text) {
        LogEntry entry = new LogEntry(timestampMillis, text);
        if (ring.size() >= RING_SIZE) ring.pollLast();
        ring.offerFirst(entry);
        // Rebuild model in order (newest first).
        model.clear();
        for (LogEntry e : ring) model.addElement(e);
    }

    private static final class LogEntry {
        final long timestamp;
        final String text;
        LogEntry(long t, String s) { this.timestamp = t; this.text = s; }
    }

    private static class EntryRenderer extends DefaultListCellRenderer {
        EntryRenderer() { setFont(FontManager.getRunescapeSmallFont()); }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (!(value instanceof LogEntry)) return this;
            LogEntry e = (LogEntry) value;
            String time = TIME_FMT.format(Instant.ofEpochMilli(e.timestamp));
            setText(time + "  " + e.text);
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setForeground(Color.WHITE);
            return this;
        }
    }
}
