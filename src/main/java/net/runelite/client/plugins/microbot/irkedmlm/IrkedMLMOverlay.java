package net.runelite.client.plugins.microbot.irkedmlm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMStatus;
import net.runelite.client.plugins.microbot.irkedmlm.session.SessionSnapshot;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Motherlode Mine session panel. Reads only the script's {@link SessionSnapshot}. */
public class IrkedMLMOverlay extends Overlay {
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
    private static final String TITLE = "Motherlode Mine";
    private static final String VERSION = "v" + IrkedMLMPlugin.version;
    private static final String TARGET = TITLE + " " + VERSION + " panel";

    static final int SPARK_POINTS = 12;
    static final long SPARK_BUCKET_MS = 600_000;

    /** Session phase labels in plain words; anything not listed falls back to the status name. */
    private static final Map<String, String> PHRASES = Map.ofEntries(
            Map.entry("Mining Vein", "Mining a vein"),
            Map.entry("Walking to Veins", "Walking to the veins"),
            Map.entry("Walking to Vein", "Walking to a vein"),
            Map.entry("Selecting Vein", "Picking a vein"),
            Map.entry("Clicking Vein", "Clicking a vein"),
            Map.entry("Waiting for Mining Start", "Starting to mine"),
            Map.entry("Repositioning", "Finding a better spot"),
            Map.entry("Clearing Rockfall", "Clearing a rockfall"),
            Map.entry("Vein Depleted", "Vein ran out"),
            Map.entry("Inventory Full", "Inventory full"),
            Map.entry("Sack Full", "Sack is full"),
            Map.entry("Climbing Ladder", "Climbing the ladder"),
            Map.entry("Walking to Hopper", "Walking to the hopper"),
            Map.entry("Depositing Pay-dirt", "Filling the hopper"),
            Map.entry("Verifying Deposit", "Checking the hopper"),
            Map.entry("Deposit Complete", "Hopper filled"),
            Map.entry("Withdrawing Ores", "Emptying the sack"),
            Map.entry("Depositing Ores", "Banking the ores"),
            Map.entry("Returning to Mining Spot", "Heading back to the veins"),
            Map.entry("Walking to Hammer Crate", "Fetching a hammer"),
            Map.entry("Searching Hammer Crate", "Fetching a hammer"),
            Map.entry("Checking for Hammer", "Checking for a hammer"),
            Map.entry("Evaluating Struts", "Checking the waterwheel"),
            Map.entry("Walking to Waterwheel", "Walking to the waterwheel"),
            Map.entry("Repairing Waterwheel", "Fixing the waterwheel"),
            Map.entry("Fixing Strut", "Fixing the waterwheel"),
            Map.entry("Dropping Hammer", "Dropping the hammer"),
            Map.entry("Repair Complete", "Waterwheel fixed"));

    /** Everything the panel shows, pre-formatted, so it can be painted without a client. */
    static final class View {
        Color status = IDLE;
        String time = "0m";
        String activity = "-";
        String ores = "0";
        String perHour = "-";
        int[] spark = new int[0];
        String xp = "0";
        String xpPerHour = "-";
        String gp = "0";
        String gpPerHour = "-";
        String sack = "Sack ? / 108";
        double meterPct;
        String footer = "";
        String compact = "-";
        /** Earlier sessions, or null before there are any. */
        String history;
    }

    private final IrkedMLMScript script;
    private final IrkedMLMConfig config;
    private final ConfigManager configManager;
    private Boolean menuFor;

    @Inject
    IrkedMLMOverlay(IrkedMLMPlugin plugin, IrkedMLMScript script, IrkedMLMConfig config, ConfigManager configManager) {
        super(plugin);
        this.script = script;
        this.config = config;
        this.configManager = configManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        IrkedMLMConfig.OverlayMode mode = config.overlayMode();
        if (mode == IrkedMLMConfig.OverlayMode.OFF || !script.isRunning() || !Microbot.isLoggedIn()) return null;
        boolean compact = mode == IrkedMLMConfig.OverlayMode.COMPACT;
        refreshMenu(compact);
        View v = view(script.getSnapshot(), System.currentTimeMillis());
        v.history = script.getHistoryLine();
        return paint(graphics, v, compact);
    }

    static View view(SessionSnapshot s, long now) {
        View v = new View();
        v.status = statusColour(s.getStatus());
        long ms = s.getActiveMs();
        v.time = hoursMinutes(ms);
        v.activity = activity(s.getStatus(), s.getSubStateLabel());
        int ores = s.totalOres();
        boolean ready = ratesReady(ms, ores);
        v.ores = compact(ores);
        v.perHour = ready ? compact(perHour(ores, ms)) : "-";
        v.spark = buckets(s.getOreCredits(), s.getStartTimeMs(), now);
        int xp = s.getStartXp() > 0 ? Math.max(0, s.getCurrentXp() - s.getStartXp()) : 0;
        v.xp = compact(xp);
        v.xpPerHour = ready ? compact(perHour(xp, ms)) : "-";
        v.gp = compact(s.getTotalValueGained());
        v.gpPerHour = ready ? compact(perHour(s.getTotalValueGained(), ms)) : "-";
        boolean known = s.getSackCount() != SackState.UNKNOWN;
        v.sack = "Sack " + (known ? s.getSackCount() : "?") + " / " + s.getMaxSackSize();
        v.meterPct = known && s.getMaxSackSize() > 0
                ? Math.max(0, Math.min(100, s.getSackCount() * 100.0 / s.getMaxSackSize())) : 0;
        v.footer = plural(s.getGainedNuggets(), "nugget") + " · " + plural(s.getSacksEmptied(), "sack") + " emptied";
        v.compact = shortStatus(s.getStatus()) + " · " + v.ores + " ores" + (ready ? " · " + v.perHour + "/hr" : "");
        return v;
    }

    /** Rates mean nothing in the first minute, or before five minutes unless a few ores are in. */
    static boolean ratesReady(long activeMs, int actions) {
        return activeMs >= 60_000 && (activeMs >= 300_000 || actions >= 3);
    }

    static long perHour(long amount, long ms) {
        return ms > 0 ? amount * 3_600_000L / ms : 0;
    }

    static Color statusColour(MLMStatus status) {
        switch (status == null ? MLMStatus.IDLE : status) {
            case MINING:
                return ACTIVE;
            case DEPOSIT_HOPPER:
            case EMPTY_SACK:
            case DROP_GEMS:
            case FIXING_WATERWHEEL:
                return AWAY;
            case RECOVERY:
                return RECOVERING;
            default:
                return IDLE;
        }
    }

    static String activity(MLMStatus status, String label) {
        if (status == null) return "Idle";
        switch (status) {
            case WAITING_FOR_REPAIR:
                return "Waiting for a wheel fix";
            case RECOVERY:
                return "Getting back on track";
            case DROP_GEMS:
                return "Dropping gems";
            case IDLE:
                return "Idle";
            default:
                String phrase = label == null ? null : PHRASES.get(label);
                return phrase != null ? phrase : tidy(status.name());
        }
    }

    private static String shortStatus(MLMStatus status) {
        switch (status == null ? MLMStatus.IDLE : status) {
            case MINING: return "Mining";
            case DEPOSIT_HOPPER: return "Hopper";
            case EMPTY_SACK: return "Sack";
            case FIXING_WATERWHEEL: return "Repairing";
            case WAITING_FOR_REPAIR: return "Waiting";
            case DROP_GEMS: return "Gems";
            case RECOVERY: return "Recovering";
            default: return "Idle";
        }
    }

    /** "DEPOSIT_HOPPER" to "Deposit hopper". */
    static String tidy(String name) {
        String s = name.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String plural(int n, String word) {
        return compact(n) + " " + word + (n == 1 ? "" : "s");
    }

    /** Ores per 10-minute window, newest last; windows before the session started are -1 (blank, not zero). */
    static int[] buckets(List<long[]> credits, long startMs, long nowMs) {
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
                configManager.setConfiguration(IrkedMLMConfig.configGroup, IrkedMLMConfig.overlayMode,
                        compact ? IrkedMLMConfig.OverlayMode.FULL : IrkedMLMConfig.OverlayMode.COMPACT));
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
        int statLabelBase = div2 + 6 + fs.getAscent();
        int statValueBase = statLabelBase + fr.getHeight();
        int statRateBase = statValueBase + fs.getHeight();
        int meterLabelBase = statRateBase + fs.getDescent() + 5 + fs.getAscent();
        int meterY = meterLabelBase + fs.getDescent() + 3;
        int footerBase = meterY + METER_H + 6 + fs.getAscent();
        int historyBase = footerBase + fs.getHeight();
        int h = (v.history != null ? historyBase : footerBase) + fs.getDescent() + PAD - 2;

        frame(g, w, h, v.status);

        g.setFont(bold);
        text(g, TITLE, left, titleBase, TEXT);
        g.setFont(small);
        int timeX = right - fs.stringWidth(v.time);
        int versionX = left + fb.stringWidth(TITLE) + 4;
        if (versionX + fs.stringWidth(VERSION) + 4 <= timeX) text(g, VERSION, versionX, titleBase, MUTED);
        text(g, v.time, timeX, titleBase, MUTED);
        dot(g, left, statusBase - fr.getAscent() / 2 - 2, v.status);
        text(g, fit(fr, v.activity, right - left - 12), left + 12, statusBase, TEXT);
        hairline(g, left, right, div1);

        String rate = v.perHour + " /hr";
        int sparkW = 84;
        g.setFont(heroFont);
        text(g, v.ores, left, heroBase, TEXT);
        g.setFont(bold);
        text(g, rate, right - fb.stringWidth(rate), div1 + 7 + fb.getAscent(), TEXT);
        int sparkY = div1 + 7 + fb.getHeight() + 3;
        sparkline(g, v.spark, right - sparkW, sparkY, sparkW, heroBase - sparkY + 2, v.status);
        g.setFont(small);
        text(g, "ores", left, heroLabelBase, MUTED);
        hairline(g, left, right, div2);

        int mid = left + (right - left) / 2 + 4;
        text(g, "Mining XP", left, statLabelBase, MUTED);
        text(g, "GP", mid, statLabelBase, MUTED);
        g.setFont(regular);
        text(g, v.xp, left, statValueBase, TEXT);
        text(g, v.gp, mid, statValueBase, TEXT);
        g.setFont(small);
        text(g, v.xpPerHour + " /hr", left, statRateBase, MUTED);
        text(g, v.gpPerHour + " /hr", mid, statRateBase, MUTED);

        text(g, v.sack, left, meterLabelBase, MUTED);
        g.setColor(TRACK);
        g.fillRoundRect(left, meterY, right - left, METER_H, METER_H, METER_H);
        int fill = (int) Math.round((right - left) * v.meterPct / 100.0);
        if (fill > 0) {
            g.setColor(ACTIVE);
            g.fillRoundRect(left, meterY, Math.max(METER_H, fill), METER_H, METER_H, METER_H);
        }

        text(g, fit(fs, v.footer, right - left), left, footerBase, MUTED);
        if (v.history != null) text(g, fit(fs, v.history, right - left), left, historyBase, MUTED);
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
