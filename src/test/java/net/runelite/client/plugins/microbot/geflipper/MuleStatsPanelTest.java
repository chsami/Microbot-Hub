package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuleStatsPanelTest {
    private static List<MuleOverlayView.Row> sampleRows() {
        return MuleOverlayView.receiverRows(
            new MuleConnectionMonitor.Snapshot(MuleConnectionMonitor.State.LISTENING, false, false, 1L),
            new MuleLedger.Snapshot(3_000_000L, 12_500_000_000L, 2_147_000_000L, 3_600_000L), false,
            "Mule: standby (logged out, W301, port 43599)");
    }

    @Test
    void sizeFollowsTheRowsAndStaysWithinTheLimits() {
        MuleStatsPanel panel = new MuleStatsPanel();
        panel.setContent("Microbot Flipper v1", sampleRows());
        Dimension size = panel.getPreferredSize();
        assertTrue(size.width >= MuleStatsPanel.MIN_WIDTH && size.width <= MuleStatsPanel.MAX_WIDTH, size.toString());

        panel.setContent("Microbot Flipper v1", new ArrayList<MuleOverlayView.Row>());
        Dimension titleOnly = panel.getPreferredSize();
        assertTrue(titleOnly.height < size.height, "Fewer rows make a shorter window");

        List<MuleOverlayView.Row> long1 = new ArrayList<>();
        long1.add(new MuleOverlayView.Row(new String(new char[400]).replace('\0', 'x'), "", MuleOverlayView.Tone.WARNING));
        panel.setContent("t", long1);
        assertEquals(MuleStatsPanel.MAX_WIDTH, panel.getPreferredSize().width);
    }

    @Test
    void paintingDrawsTheTitleAndToneColouredValuesWithoutThrowing() {
        MuleStatsPanel panel = new MuleStatsPanel();
        panel.setContent(MuleOverlayView.title(), sampleRows());
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        int background = new Color(0x1B1B1B).getRGB();
        int cyan = 0, green = 0, orange = 0, white = 0;
        for (int y = 2; y < size.height - 2; y++) {
            for (int x = 2; x < size.width - 2; x++) {
                int rgb = image.getRGB(x, y);
                if (rgb == background) continue;
                if (rgb == Color.CYAN.getRGB()) cyan++;
                if (rgb == MuleOverlayView.POSITIVE_COLOR.getRGB()) green++;
                if (rgb == Color.ORANGE.getRGB()) orange++;
                if (rgb == Color.WHITE.getRGB()) white++;
            }
        }
        assertTrue(cyan > 0, "The title is drawn in the overlay's title colour");
        assertTrue(green > 0, "Positive values are drawn in the overlay's green");
        assertTrue(orange > 0, "Warnings and the status message are drawn in orange");
        assertTrue(white > 0, "Labels are drawn in white");
    }

    @Test
    void fitShortensWithAnEllipsisAndHandlesEmptyAndZeroWidth() {
        MuleStatsPanel panel = new MuleStatsPanel();
        FontMetrics metrics = panel.getFontMetrics(panel.getFont());
        assertEquals("short", MuleStatsPanel.fit("short", 1_000, metrics));
        String fitted = MuleStatsPanel.fit("Mule: waiting for the confirmed trade's inventory update.", 90, metrics);
        assertTrue(fitted.endsWith("..."), fitted);
        assertTrue(metrics.stringWidth(fitted) <= 90, fitted);
        assertEquals("", MuleStatsPanel.fit("anything", 0, metrics));
        assertEquals("", MuleStatsPanel.fit("", 100, metrics));
        assertEquals("", MuleStatsPanel.fit(null, 100, metrics));
        assertEquals("", MuleStatsPanel.fit("wide text", 1, metrics), "Nothing fits, so nothing is drawn");
    }

    @Test
    void nullContentIsTreatedAsEmpty() {
        MuleStatsPanel panel = new MuleStatsPanel();
        panel.setContent(null, null);
        assertTrue(panel.getPreferredSize().width >= MuleStatsPanel.MIN_WIDTH);
    }
}
