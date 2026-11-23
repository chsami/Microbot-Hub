package net.runelite.client.plugins.microbot.anvilsmither;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayColoursTest {
    @Test
    void thePanelKeepsItsHouseColours() {
        assertEquals(new Color(105, 114, 107), AnvilSmitherOverlay.FRAME_INNER);
        assertEquals(new Color(101, 100, 77), AnvilSmitherOverlay.SPARK);
        assertEquals(new Color(65, 84, 84), AnvilSmitherOverlay.TRACK);
    }

    /** Paints both panel sizes to build/ so the layout can be looked at without a client. */
    @Test
    void thePanelPaintsWithoutAClient() throws Exception {
        AnvilSmitherOverlay.View v = new AnvilSmitherOverlay.View();
        v.status = AnvilSmitherOverlay.ACTIVE;
        v.time = "1h 12m";
        v.activity = "Smithing Rune scimitar";
        v.made = "143";
        v.pending = " +13";
        v.madeLabel = "Rune scimitar made";
        v.perHour = "119";
        v.spark = new int[]{-1, -1, -1, -1, -1, 18, 21, 20, 13, 22, 20, 19};
        v.xp = "118,300";
        v.xpPerHour = "98.6K";
        v.level = 91;
        v.toLevel = "1h 12m";
        v.meterPct = 62;
        v.footer = AnvilSmitherOverlay.footer(1240, "bars", 2, 119);
        v.compact = "143 Rune scimitar · 119/hr";
        for (boolean compact : new boolean[]{false, true}) {
            BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            Dimension d = AnvilSmitherOverlay.paint(g, v, compact);
            g.dispose();
            assertTrue(d.width > 0 && d.width <= 320 && d.height > 0 && d.height <= 200, d.toString());
            File out = new File("build/anvil-panel-" + (compact ? "compact" : "full") + ".png");
            out.getParentFile().mkdirs();
            ImageIO.write(img.getSubimage(0, 0, d.width, d.height), "png", out);
        }
    }

    @Test
    void thePanelNumbersAreHonest() {
        long now = 10_000_000, start = now - 25 * 60_000;   // 25 minutes in
        int[] b = AnvilSmitherOverlay.buckets(java.util.List.of(
                new long[]{now - 60_000, 13}, new long[]{now - 120_000, 13}, new long[]{now - 15 * 60_000, 9}), start, now);
        assertEquals(AnvilSmitherOverlay.SPARK_POINTS, b.length);
        assertEquals(-1, b[0]);    // before the session: blank, not zero
        assertEquals(-1, b[8]);
        assertEquals(0, b[9]);
        assertEquals(9, b[10]);
        assertEquals(26, b[11]);
        assertEquals("Counting bars at the bank", AnvilSmitherOverlay.footer(-1, "bars", 2, 0));
        assertEquals("1,240 bars left", AnvilSmitherOverlay.footer(1240, "bars", 2, 0));
        assertEquals("1,439 left to alch · out in 1h 26m", AnvilSmitherOverlay.footer(1439, "to alch", 1, 1000));
        assertEquals("1,240 bars left · out in 5h 12m", AnvilSmitherOverlay.footer(1240, "bars", 2, 119));
    }

    /** The launcher shows these under the thumbnail, in this order. */
    @Test
    void publishedStatsAreTheHeadlineFigures() {
        AnvilSmitherOverlay.View v = new AnvilSmitherOverlay.View();
        v.madeLabel = "Mithril platebody made";
        v.made = "1,240";
        v.pending = " +3";
        v.perHour = "119";
        v.footer = AnvilSmitherOverlay.footer(1240, "bars", 5, 119);
        assertEquals("{Mithril platebody made=1,240 +3, Per hour=119, " + v.footer + "=}", AnvilSmitherOverlay.stats(v).toString());
        assertEquals("scriptStats", java.util.Arrays.stream(AnvilSmitherPlugin.class.getMethods())
                .filter(m -> m.getReturnType() == java.util.Map.class).findFirst().orElseThrow().getName());
    }
}
