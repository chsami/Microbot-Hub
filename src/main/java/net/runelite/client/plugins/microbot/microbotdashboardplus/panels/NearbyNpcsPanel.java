package net.runelite.client.plugins.microbot.microbotdashboardplus.panels;

import net.runelite.client.plugins.microbot.microbotdashboardplus.data.PollSnapshot;
import net.runelite.client.plugins.microbot.microbotdashboardplus.poller.GameStatePoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Collections;
import java.util.List;

/**
 * Nearby NPCs section. JList sorted by distance, with an in-header spinner
 * to adjust the max-distance filter (1-200 tiles). Updates {@link GameStatePoller#setNpcMaxDistance(int)}
 * immediately so the next poll reflects the new bound.
 */
public class NearbyNpcsPanel extends DashboardSection {

    private final DefaultListModel<PollSnapshot.NearbyNpc> model = new DefaultListModel<>();
    private final JList<PollSnapshot.NearbyNpc> list;
    private final JSpinner maxDistanceSpinner;

    public NearbyNpcsPanel(GameStatePoller poller) {
        super("Nearby NPCs", poller);

        // Header spinner: max distance.
        JLabel maxLbl = new JLabel("Max");
        maxLbl.setForeground(Color.LIGHT_GRAY);
        maxLbl.setFont(FontManager.getRunescapeSmallFont());
        addHeaderControl(maxLbl);

        maxDistanceSpinner = new JSpinner(new SpinnerNumberModel(poller.getNpcMaxDistance(), 1, 200, 1));
        maxDistanceSpinner.setPreferredSize(new Dimension(60, 20));
        maxDistanceSpinner.addChangeListener((ChangeListener) e -> {
            int v = (Integer) maxDistanceSpinner.getValue();
            poller.setNpcMaxDistance(v);
            poller.refreshNow();
        });
        addHeaderControl(maxDistanceSpinner);

        JLabel tilesLbl = new JLabel("tiles");
        tilesLbl.setForeground(Color.LIGHT_GRAY);
        tilesLbl.setFont(FontManager.getRunescapeSmallFont());
        addHeaderControl(tilesLbl);

        // List body.
        list = new JList<>(model);
        list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        list.setForeground(Color.WHITE);
        list.setFont(FontManager.getRunescapeSmallFont());
        list.setSelectionForeground(Color.WHITE);
        list.setSelectionBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        list.setCellRenderer(new NpcCellRenderer());

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setPreferredSize(new Dimension(380, 240));
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    protected void applySnapshot(PollSnapshot snapshot) {
        if (snapshot == null) return;
        List<PollSnapshot.NearbyNpc> npcs = snapshot.getNearbyNpcs();
        if (npcs == null) npcs = Collections.emptyList();
        model.clear();
        for (PollSnapshot.NearbyNpc n : npcs) model.addElement(n);
        setSubtitle("(" + npcs.size() + " within " + poller.getNpcMaxDistance() + " tiles)");
    }

    private static class NpcCellRenderer extends DefaultListCellRenderer {
        NpcCellRenderer() { setFont(FontManager.getRunescapeSmallFont()); }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (!(value instanceof PollSnapshot.NearbyNpc)) return this;
            PollSnapshot.NearbyNpc npc = (PollSnapshot.NearbyNpc) value;
            String text = npc.getName()
                    + (npc.getCombatLevel() > 0 ? " (lvl " + npc.getCombatLevel() + ")" : "")
                    + " - " + npc.getDistance() + " tiles";
            setText(text);
            setForeground(npc.isRandomEvent() ? ColorScheme.PROGRESS_INPROGRESS_COLOR : Color.WHITE);
            setBackground(isSelected ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
            return this;
        }
    }
}
