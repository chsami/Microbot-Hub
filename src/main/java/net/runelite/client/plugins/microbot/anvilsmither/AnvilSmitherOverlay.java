package net.runelite.client.plugins.microbot.anvilsmither;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.util.Locale;
import javax.inject.Inject;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class AnvilSmitherOverlay extends Overlay {
    static final Color SURFACE = new Color(28, 25, 21, 228);
    static final Color FRAME_OUTER = new Color(0, 0, 0, 200);
    static final Color FRAME_INNER = new Color(105, 114, 107);
    static final Color HAIRLINE = new Color(255, 255, 255, 26);
    static final Color TEXT = new Color(255, 255, 255);
    static final Color MUTED = new Color(168, 160, 147);
    static final Color ACTIVE = new Color(0x62a533);
    static final Color AWAY = new Color(0x3b8ad1);
    static final Color RECOVERING = new Color(0xd9534f);
    static final Color IDLE = new Color(168, 160, 147);
    static final Color TRACK = new Color(65, 84, 84);
    static final Color SPARK = new Color(101, 100, 77);

    static final int FULL_WIDTH = 204;
    private static final int PAD = 9;
    private static final int STRIP = 3;
    private static final int METER_H = 6;
    private static final String TITLE = "Anvil Smither";
    private static final String VERSION = "v" + AnvilSmitherPlugin.version;
    private static final String TARGET = TITLE + " " + VERSION + " panel";

    static final int SPARK_POINTS = 12;
    static final long SPARK_BUCKET_MS = 600_000;
    private static final long RATE_READY_MS = 60_000;

    /** Everything the panel shows, so it can be painted without a client. */
    static final class View {
        Color status = IDLE;
        String time = "0m";
        String activity = "-";
        String made = "0";
        String pending = "";
        String madeLabel = "made";
        String perHour = "-";
        int[] spark = new int[0];
        String xpLabel = "Smithing XP";
        String xp = "0";
        String xpPerHour = "-";
        int level;
        String toLevel;
        double meterPct;
        String footer = "";
        String compact = "-";
    }

    private final AnvilSmitherConfig config;
    private final ConfigManager configManager;
    @Setter
    private AnvilSmitherScript script;
    private Boolean menuFor;

    @Inject
    AnvilSmitherOverlay(AnvilSmitherPlugin plugin, AnvilSmitherConfig config, ConfigManager configManager) {
        super(plugin);
        this.config = config;
        this.configManager = configManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        AnvilSmitherConfig.InfoPanel mode = config.infoPanel();
        if (script == null || !script.isRunning() || mode == AnvilSmitherConfig.InfoPanel.OFF || !Microbot.isLoggedIn()) return null;
        boolean compact = mode == AnvilSmitherConfig.InfoPanel.COMPACT;
        refreshMenu(compact);
        return paint(graphics, view(), compact);
    }

    private View view() {
        View v = new View();
        AnvilSmitherScript.State state = script.getState();
        v.status = state == AnvilSmitherScript.State.SMITHING || state == AnvilSmitherScript.State.ALCHING ? ACTIVE
                : state == AnvilSmitherScript.State.BANKING ? AWAY
                : state == AnvilSmitherScript.State.RECOVERY ? RECOVERING : IDLE;
        // Once the bars are gone and alching starts, every figure is the alch stage's: its count,
        // its rate over its own time, Magic XP and level, and the items left in the stack.
        boolean alch = script.isAlching();
        long now = System.currentTimeMillis();
        v.time = hoursMinutes(script.getElapsedTime());
        long ms = alch ? script.getAlchElapsed() : script.getElapsedTime();
        v.activity = script.getActivity();
        String item = alch ? script.getAlchName() : script.getTargetName();
        int made = alch ? script.getAlchCasts() : script.getItemsMade();
        v.made = String.format(Locale.ENGLISH, "%,d", made);
        int pending = alch ? 0 : script.getPendingItems();
        v.pending = pending > 0 ? " +" + pending : "";
        v.madeLabel = item + (alch ? " alched" : " made");
        // rates mean nothing in the first minute or before the first finished load
        boolean ready = ms >= RATE_READY_MS && made > 0;
        int itemsHr = ready ? (int) (made * 3_600_000L / ms) : 0;
        v.perHour = ready ? compact(itemsHr) : "-";
        v.spark = buckets(alch ? script.getAlchCredits() : script.getCredits(), now - ms, now);
        int gained = alch ? script.getAlchXpGained() : script.getXpGained();
        Skill skill = alch ? Skill.MAGIC : Skill.SMITHING;
        v.xpLabel = alch ? "Magic XP" : "Smithing XP";
        int xpHr = ready ? (int) (gained * 3_600_000L / ms) : 0;
        v.xp = String.format(Locale.ENGLISH, "%,d", gained);
        v.xpPerHour = ready ? compact(xpHr) : "-";

        Client client = Microbot.getClient();
        int xp = client.getSkillExperience(skill);
        v.level = client.getRealSkillLevel(skill);
        if (v.level >= Experience.MAX_REAL_LEVEL) {
            v.meterPct = 100;
        } else {
            int from = Experience.getXpForLevel(v.level);
            int to = Experience.getXpForLevel(v.level + 1);
            v.meterPct = Math.max(0, Math.min(100, (xp - from) * 100.0 / (to - from)));
            v.toLevel = xpHr > 0 ? hoursMinutes((to - xp) * 3_600_000L / xpHr) : null;
        }
        v.footer = alch ? footer(script.getAlchLeft(), "to alch", 1, itemsHr)
                : footer(script.getMaterialsLeft(), script.getMaterialWord(), script.getMaterialsPerItem(), itemsHr);
        v.compact = v.made + " " + item + (alch ? " alched" : "") + (ready ? " · " + v.perHour + "/hr" : "");
        return v;
    }

    /**
     * The headline figures for the launcher's Clients view, label to value in display order; an empty value is a
     * line of its own. Empty when there is nothing to show. XP and level are left out: the client adds those itself.
     */
    java.util.Map<String, String> stats() {
        return script == null || !script.isRunning() || !Microbot.isLoggedIn() ? java.util.Map.of() : stats(view());
    }

    static java.util.Map<String, String> stats(View v) {
        java.util.Map<String, String> stats = new java.util.LinkedHashMap<>();
        stats.put(v.madeLabel, v.made + v.pending);
        stats.put("Per hour", v.perHour);
        stats.put(v.footer, "");
        return stats;
    }

    /** "1,240 bars left · out in 2h 5m"; says so plainly before the bank has been seen. */
    static String footer(int left, String word, int perItem, int itemsHr) {
        if (left < 0) return "Counting " + word + " at the bank";
        String s = String.format(Locale.ENGLISH, word.startsWith("to ") ? "%,d left %s" : "%,d %s left", left, word);
        return itemsHr > 0 ? s + " · out in " + hoursMinutes(left / perItem * 3_600_000L / itemsHr) : s;
    }

    /** Items per 10-minute window, newest last; windows before the session started are -1 (blank, not zero). */
    static int[] buckets(java.util.List<long[]> credits, long startMs, long nowMs) {
        int[] out = new int[SPARK_POINTS];
        for (int i = 0; i < SPARK_POINTS; i++) {
            long to = nowMs - (long) (SPARK_POINTS - 1 - i) * SPARK_BUCKET_MS;
            long from = to - SPARK_BUCKET_MS;
            if (to <= startMs) {
                out[i] = -1;
                continue;
            }
            for (long[] c : credits) if (c[0] > from && c[0] <= to) out[i] += (int) c[1];
        }
        return out;
    }

    private void refreshMenu(boolean compact) {
        if (menuFor != null && menuFor == compact) return;
        menuFor = compact;
        getMenuEntries().clear();
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, compact ? "Expand" : "Minimise", TARGET, e ->
                configManager.setConfiguration(AnvilSmitherConfig.GROUP, AnvilSmitherConfig.INFO_PANEL_KEY,
                        compact ? AnvilSmitherConfig.InfoPanel.FULL : AnvilSmitherConfig.InfoPanel.COMPACT));
    }

    static Dimension paint(Graphics2D g, View v, boolean compact) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font regular = FontManager.getRunescapeFont();
        Font small = FontManager.getRunescapeSmallFont();
        Font bold = FontManager.getRunescapeBoldFont();
        return compact ? paintCompact(g, v, regular, small) : paintFull(g, v, regular, small, bold);
    }

    private static Dimension paintCompact(Graphics2D g, View v, Font regular, Font small) {
        g.setFont(regular);
        FontMetrics fm = g.getFontMetrics();
        FontMetrics fs = g.getFontMetrics(small);
        String main = fit(fm, v.compact, 240);
        int w = PAD + STRIP + 4 + 14 + fm.stringWidth(main) + 10 + fs.stringWidth(v.time) + PAD;
        int h = fm.getHeight() + 8;
        frame(g, w, h, v.status);
        int base = (h + fm.getAscent() - fm.getDescent()) / 2;
        dot(g, PAD + STRIP, base - fm.getAscent() / 2 - 3, v.status);
        text(g, main, PAD + STRIP + 14, base, TEXT);
        g.setFont(small);
        text(g, v.time, w - PAD - fs.stringWidth(v.time), base, MUTED);
        return new Dimension(w, h);
    }

    private static Dimension paintFull(Graphics2D g, View v, Font regular, Font small, Font bold) {
        int w = FULL_WIDTH;
        int left = PAD + STRIP + 3;
        int right = w - PAD;
        FontMetrics fr = g.getFontMetrics(regular), fs = g.getFontMetrics(small), fb = g.getFontMetrics(bold);
        Font heroFont = bold.deriveFont(bold.getSize2D() * 2);
        FontMetrics fh = g.getFontMetrics(heroFont);
        int titleBase = PAD + fb.getAscent();
        int statusBase = titleBase + fr.getHeight() + 1;
        int div1 = statusBase + fr.getDescent() + 6;
        int heroBase = div1 + 7 + fh.getAscent() - 4;
        int heroLabelBase = heroBase + fs.getHeight();
        int div2 = heroLabelBase + fs.getDescent() + 6;
        int xpBase = div2 + 6 + fs.getAscent();
        int xpValueBase = xpBase + fr.getHeight();
        int meterLabelBase = xpValueBase + fr.getDescent() + 4 + fs.getAscent();
        int meterY = meterLabelBase + fs.getDescent() + 3;
        int footerBase = meterY + METER_H + 6 + fs.getAscent();
        int h = footerBase + fs.getDescent() + PAD - 2;

        frame(g, w, h, v.status);

        g.setFont(bold);
        text(g, TITLE, left, titleBase, TEXT);
        g.setFont(small);
        text(g, VERSION, left + fb.stringWidth(TITLE) + 6, titleBase, MUTED);
        g.setFont(regular);
        text(g, v.time, right - fr.stringWidth(v.time), titleBase, MUTED);
        dot(g, left, statusBase - fr.getAscent() / 2 - 2, v.status);
        text(g, fit(fr, v.activity, right - left - 12), left + 12, statusBase, TEXT);
        hairline(g, left, right, div1);

        // hero: the count on the left; its own rate and trend on the right, so both columns are items
        String rate = v.perHour + " /hr";
        int sparkW = 84;
        g.setFont(heroFont);
        text(g, v.made, left, heroBase, TEXT);
        int pendingX = left + fh.stringWidth(v.made);
        g.setFont(bold);
        if (pendingX + fb.stringWidth(v.pending) < right - sparkW - 4) text(g, v.pending, pendingX, heroBase, SPARK);
        text(g, rate, right - fb.stringWidth(rate), div1 + 7 + fb.getAscent(), TEXT);
        int sparkY = div1 + 7 + fb.getHeight() + 3;
        sparkline(g, v.spark, right - sparkW, sparkY, sparkW, heroBase - sparkY + 2, v.status);
        g.setFont(small);
        text(g, fit(fs, v.madeLabel, right - left), left, heroLabelBase, MUTED);
        hairline(g, left, right, div2);

        int mid = left + (right - left) / 2 + 4;
        text(g, v.xpLabel, left, xpBase, MUTED);
        text(g, "XP per hour", mid, xpBase, MUTED);
        g.setFont(regular);
        text(g, v.xp, left, xpValueBase, TEXT);
        text(g, v.xpPerHour, mid, xpValueBase, TEXT);

        g.setFont(small);
        text(g, "Level " + v.level, left, meterLabelBase, MUTED);
        if (v.level < Experience.MAX_REAL_LEVEL) {
            String to = (v.level + 1) + (v.toLevel != null ? " in " + v.toLevel : "");
            text(g, to, right - fs.stringWidth(to), meterLabelBase, MUTED);
        }
        g.setColor(TRACK);
        g.fillRoundRect(left, meterY, right - left, METER_H, METER_H, METER_H);
        int fill = (int) Math.round((right - left) * v.meterPct / 100.0);
        if (fill > 0) {
            g.setColor(ACTIVE);   // progress, not status: it must not turn blue while banking
            g.fillRoundRect(left, meterY, Math.max(METER_H, fill), METER_H, METER_H, METER_H);
        }

        text(g, fit(fs, v.footer, right - left), left, footerBase, MUTED);
        return new Dimension(w, h);
    }

    private static void sparkline(Graphics2D g, int[] v, int x, int y, int w, int h, Color accent) {
        if (v.length < 2 || h < 6) return;
        int max = 1;
        for (int n : v) max = Math.max(max, n);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(SPARK);
        int px = -1, py = -1;
        for (int i = 0; i < v.length; i++) {
            if (v[i] < 0) {
                px = -1;
                continue;
            }
            int cx = x + i * (w - 1) / (v.length - 1);
            int cy = y + h - 1 - v[i] * (h - 2) / max;
            if (px >= 0) g.drawLine(px, py, cx, cy);
            px = cx;
            py = cy;
        }
        g.setStroke(old);
        if (px >= 0) {
            g.setColor(accent);
            g.fill(new Ellipse2D.Double(px - 2.5, py - 2.5, 5, 5));
        }
    }

    private static void frame(Graphics2D g, int w, int h, Color status) {
        g.setColor(SURFACE);
        g.fillRect(0, 0, w, h);
        g.setColor(FRAME_OUTER);
        g.drawRect(0, 0, w - 1, h - 1);
        g.setColor(FRAME_INNER);
        g.drawRect(1, 1, w - 3, h - 3);
        g.setColor(status);
        g.fillRect(2, 2, STRIP, h - 4);
    }

    static String fit(FontMetrics fm, String text, int width) {
        if (fm.stringWidth(text) <= width) return text;
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "...") > width) t = t.substring(0, t.length() - 1);
        return t.trim() + "...";
    }

    static String compact(long n) {
        if (n < 10_000) return String.format(Locale.ENGLISH, "%,d", n);
        if (n < 1_000_000) return String.format(Locale.ENGLISH, "%.1fK", n / 1_000.0);
        return String.format(Locale.ENGLISH, "%.2fM", n / 1_000_000.0);
    }

    static String hoursMinutes(long ms) {
        long m = Math.max(0, ms) / 60_000;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    private static void hairline(Graphics2D g, int x1, int x2, int y) {
        g.setColor(HAIRLINE);
        g.drawLine(x1, y, x2, y);
    }

    private static void dot(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x, y, 7, 7));
    }

    private static void text(Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(Color.BLACK);
        g.drawString(s, x + 1, y + 1);
        g.setColor(c);
        g.drawString(s, x, y);
    }
}
