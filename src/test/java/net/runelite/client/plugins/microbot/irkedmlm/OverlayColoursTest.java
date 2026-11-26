package net.runelite.client.plugins.microbot.irkedmlm;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMStatus;
import net.runelite.client.plugins.microbot.irkedmlm.session.SessionSnapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayColoursTest {
    private static final long NOW = 10_000_000_000L;

    @Test
    void thePanelKeepsItsHouseColours() {
        assertEquals(new Color(105, 114, 107), IrkedMLMOverlay.FRAME_INNER);
        assertEquals(new Color(101, 100, 77), IrkedMLMOverlay.SPARK);
        assertEquals(new Color(65, 84, 84), IrkedMLMOverlay.TRACK);
    }

    @Test
    void numbersAndTimesFormatTheHouseWay() {
        assertEquals("9,999", IrkedMLMOverlay.compact(9_999));
        assertEquals("124.3K", IrkedMLMOverlay.compact(124_300));
        assertEquals("1.25M", IrkedMLMOverlay.compact(1_250_000));
        assertEquals("0m", IrkedMLMOverlay.hoursMinutes(-5));
        assertEquals("1h 12m", IrkedMLMOverlay.hoursMinutes(72 * 60_000L));
        assertEquals("Deposit hopper", IrkedMLMOverlay.tidy("DEPOSIT_HOPPER"));
    }

    @Test
    void ratesWaitForAMinuteAndThenEnoughOres() {
        assertFalse(IrkedMLMOverlay.ratesReady(59_000, 100));
        assertFalse(IrkedMLMOverlay.ratesReady(120_000, 2));
        assertTrue(IrkedMLMOverlay.ratesReady(120_000, 3));
        assertTrue(IrkedMLMOverlay.ratesReady(300_000, 0));
        assertEquals("-", IrkedMLMOverlay.view(snapshot(30_000), NOW).perHour);
    }

    @Test
    void bucketsAreTenMinuteWindowsBlankBeforeTheStart() {
        List<long[]> credits = List.of(new long[]{NOW - 5_000, 4}, new long[]{NOW - 1_000, 2},
                new long[]{NOW - 650_000, 7});
        int[] b = IrkedMLMOverlay.buckets(credits, NOW - 1_500_000, NOW);
        assertEquals(12, b.length);
        assertArrayEquals(new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 7, 6}, b);
    }

    @Test
    void statusesMapToColoursAndPlainWords() {
        assertEquals(IrkedMLMOverlay.ACTIVE, IrkedMLMOverlay.statusColour(MLMStatus.MINING));
        assertEquals(IrkedMLMOverlay.AWAY, IrkedMLMOverlay.statusColour(MLMStatus.FIXING_WATERWHEEL));
        assertEquals(IrkedMLMOverlay.AWAY, IrkedMLMOverlay.statusColour(MLMStatus.DROP_GEMS));
        assertEquals(IrkedMLMOverlay.IDLE, IrkedMLMOverlay.statusColour(MLMStatus.WAITING_FOR_REPAIR));
        assertEquals(IrkedMLMOverlay.RECOVERING, IrkedMLMOverlay.statusColour(MLMStatus.RECOVERY));
        assertEquals("Mining a vein", IrkedMLMOverlay.activity(MLMStatus.MINING, "Mining Vein"));
        assertEquals("Walking to the hopper", IrkedMLMOverlay.activity(MLMStatus.DEPOSIT_HOPPER, "Walking to Hopper"));
        assertEquals("Emptying the sack", IrkedMLMOverlay.activity(MLMStatus.EMPTY_SACK, "Withdrawing Ores"));
        assertEquals("Waiting for a wheel fix", IrkedMLMOverlay.activity(MLMStatus.WAITING_FOR_REPAIR, "Waiting for repair"));
        assertEquals("Getting back on track", IrkedMLMOverlay.activity(MLMStatus.RECOVERY, "Recovering (2)"));
        assertEquals("Empty sack", IrkedMLMOverlay.activity(MLMStatus.EMPTY_SACK, "Something New"));
    }

    @Test
    void anUnknownSackShowsAnEmptyBarAndAQuestionMark() {
        IrkedMLMOverlay.View v = IrkedMLMOverlay.view(new SessionSnapshot(MLMStatus.MINING, null, SackState.UNKNOWN,
                108, true, NOW, 0L, 0, 0, 0, 0, 0, 0, 0, 0, "Mining Vein", NOW, 0L), NOW);
        assertEquals("Sack ? / 108", v.sack);
        assertEquals(0.0, v.meterPct);
    }

    @Test
    void activeTimeStopsWhileLoggedOutAndOresAreTimestamped() {
        Map<Integer, Integer> inv = new HashMap<>();
        MlmSessionStats stats = new MlmSessionStats(id -> inv.getOrDefault(id, 0), id -> 100);
        stats.reset();
        stats.trackLoggedIn(true, 1_000);
        stats.trackLoggedIn(true, 61_000);
        stats.trackLoggedIn(false, 121_000);
        stats.trackLoggedIn(false, 500_000);
        stats.trackLoggedIn(true, 600_000);
        assertEquals(150_000, stats.activeMs(630_000));
        inv.put(ItemID.COAL, 5);
        stats.update();
        assertEquals(1, stats.oreCredits().size());
        assertEquals(5, stats.oreCredits().get(0)[1]);
        stats.reset();
        assertEquals(0, stats.oreCredits().size());
        assertEquals(0, stats.activeMs(700_000));
    }

    /** Paints both panel sizes to build/ so the layout can be looked at without a client. */
    @Test
    void thePanelPaintsWithoutAClient() throws Exception {
        IrkedMLMOverlay.View v = IrkedMLMOverlay.view(snapshot(72 * 60_000L), NOW);
        assertEquals("1h 12m", v.time);
        assertEquals("Mining a vein", v.activity);
        assertEquals("Sack 54 / 108", v.sack);
        assertEquals("Mining · 1,230 ores · 1,025/hr", v.compact);
        assertEquals("37 nuggets · 12 sacks emptied", v.footer);
        for (boolean compact : new boolean[]{false, true}) {
            BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            Dimension d = IrkedMLMOverlay.paint(g, v, compact);
            g.dispose();
            assertTrue(d.width > 0 && d.width <= 320 && d.height > 0 && d.height <= 200, d.toString());
            File out = new File("build/irkedmlm-panel-" + (compact ? "compact" : "full") + ".png");
            out.getParentFile().mkdirs();
            ImageIO.write(img.getSubimage(0, 0, d.width, d.height), "png", out);
        }
    }

    private static SessionSnapshot snapshot(long activeMs) {
        long start = NOW - activeMs - 120_000;
        List<long[]> credits = new java.util.ArrayList<>();
        int[] perWindow = {150, 170, 160, 120, 190, 180, 175, 85};
        for (int i = 0; i < perWindow.length; i++) {
            credits.add(new long[]{NOW - (perWindow.length - 1 - i) * IrkedMLMOverlay.SPARK_BUCKET_MS - 1_000, perWindow[i]});
        }
        return new SessionSnapshot(MLMStatus.MINING, MLMMiningSpot.WEST_UPPER, 54, 108, false, start,
                412_350L, 37, 5_000_000, 5_118_300, 20, 110, 300, 400, 400, "Mining Vein", NOW, NOW)
                .withActiveMs(activeMs).withSacksEmptied(12).withOreCredits(credits);
    }
}
