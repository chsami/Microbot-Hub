package net.runelite.client.plugins.microbot.herbihunter;

import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Status always carries its label, never colour alone. */
public class HerbiboarOverlay extends Overlay {

    static final Color SURFACE = new Color(28, 25, 21, 228);
    static final Color FRAME_OUTER = new Color(0, 0, 0, 200);
    static final Color FRAME_INNER = new Color(105, 114, 107);
    static final Color HAIRLINE = new Color(255, 255, 255, 26);
    static final Color TEXT = new Color(255, 255, 255);
    static final Color MUTED = new Color(168, 160, 147);
    static final Color HUNTING = new Color(0x62a533);
    static final Color AWAY = new Color(0x3b8ad1);
    static final Color RECOVERING = new Color(0xd9534f);
    static final Color IDLE = new Color(168, 160, 147);
    static final Color TRACK = new Color(65, 84, 84);
    static final Color SPARK = new Color(101, 100, 77);

    static final int FULL_WIDTH = 204;
    private static final int PAD = 9;
    private static final int STRIP = 3;
    /** Two hours of trend, ten minutes a point. */
    private static final int SPARK_POINTS = 12;
    private static final long SPARK_BUCKET_MS = 10 * 60_000L;
    private static final long RATE_MIN_SECONDS = 300;
    private static final int RATE_MIN_CATCHES = 3;
    private static final String VERSION = "v" + HerbiHunterPlugin.version;
    private static final String TARGET = "Herbi Hunter " + VERSION + " panel";

    private final HerbiHunterPlugin plugin;
    private final HerbiboarConfig config;
    private final Client client;
    private final ConfigManager configManager;
    private HerbiboarConfig.InfoPanel menuFor;

    @Inject
    HerbiboarOverlay(HerbiHunterPlugin plugin, HerbiboarConfig config, Client client, ConfigManager configManager) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        this.configManager = configManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_LOW);
    }

    static final class Snapshot {
        HerbiboarState state;
        String activity;
        String runTime;
        int caught;
        String perHour;
        String xp;
        String xpPerHour;
        int level;
        double levelPct;
        String toLevel;
        int[] spark;
        String footer;
        String history;
        String best;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        HerbiboarConfig.InfoPanel style = config.infoPanel();
        if (style == HerbiboarConfig.InfoPanel.OFF || !Microbot.isLoggedIn()) return null;
        refreshMenu(style);
        return paint(graphics, snapshot(), style == HerbiboarConfig.InfoPanel.COMPACT);
    }

    private void refreshMenu(HerbiboarConfig.InfoPanel style) {
        if (style == menuFor) return;
        menuFor = style;
        getMenuEntries().clear();
        boolean compact = style == HerbiboarConfig.InfoPanel.COMPACT;
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, compact ? "Expand" : "Minimise", TARGET, e ->
                configManager.setConfiguration(HerbiboarConfig.GROUP, HerbiboarConfig.INFO_PANEL_KEY,
                        compact ? HerbiboarConfig.InfoPanel.FULL : HerbiboarConfig.InfoPanel.COMPACT));
    }

    /** Over active time, like the catch rate beside it: a break must not drag it down. Pure. */
    static int xpPerHour(long xp, long activeMs) {
        return activeMs <= 0 ? 0 : (int) (xp * 3_600_000L / activeMs);
    }

    private Snapshot snapshot() {
        long now = System.currentTimeMillis();
        HerbiboarSession session = plugin.getSession();
        Snapshot s = new Snapshot();
        s.state = HerbiboarScript.getState();
        s.activity = activityText(s.state, HerbiboarScript.getActivity(), plugin.getCurrentPath().size());
        long ms = session == null ? 0 : session.durationMs(now);
        s.runTime = TimeUtils.getFormattedDuration(Duration.ofMillis(ms));
        s.caught = session == null ? 0 : session.caught;
        boolean ready = ratesReady(ms / 1000, s.caught);
        s.perHour = ready ? String.valueOf(Math.round(session.catchesPerHour(now))) : "-";
        Integer gained = plugin.getXpGained();
        long xp = gained == null ? 0 : gained;
        s.xp = String.format(Locale.ENGLISH, "%,d", xp);
        int xpHr = xpPerHour(xp, ms);
        s.xpPerHour = ready ? compact(xpHr) : "-";
        s.level = client.getRealSkillLevel(Skill.HUNTER);
        if (s.level > 0 && s.level < Experience.MAX_REAL_LEVEL) {
            int have = client.getSkillExperience(Skill.HUNTER);
            int from = Experience.getXpForLevel(s.level), to = Experience.getXpForLevel(s.level + 1);
            s.levelPct = levelProgress(have, from, to);
            s.toLevel = ready && xpHr > 0 ? hoursMinutes((to - have) * 3_600_000L / xpHr) : null;
        }
        s.spark = session == null ? new int[0]
                : HerbiboarSession.buckets(session.catchTimes, session.startMs, now, SPARK_POINTS, SPARK_BUCKET_MS);
        int doses = staminaDoses(config.runEnergyOption());
        int banks = session == null ? 0 : session.bankTrips;
        s.footer = footerLine(config.playStyle(), doses, banks);
        s.history = historyLine(plugin.getHistory());
        s.best = bestLine(plugin.getHistory());
        return s;
    }

    static String footerLine(HerbiboarConfig.PlayStyle style, int doses, int banks) {
        return style + " · " + (doses >= 0 ? doses + (doses == 1 ? " dose" : " doses") + " left · " : "")
                + banks + (banks == 1 ? " bank" : " banks");
    }

    private static int staminaDoses(HerbiboarConfig.RunEnergyOption option) {
        int[] ids = HerbiboarScript.doseIds(option);
        if (ids.length == 0) return -1;
        int doses = 0;
        for (int i = 0; i < ids.length; i++) doses += (i + 1) * Rs2Inventory.count(ids[i]);
        return doses;
    }

    static String historyLine(List<HerbiboarSession> history) {
        if (history == null || history.isEmpty()) return null;
        HerbiboarSession last = history.get(history.size() - 1);
        return "Last " + hoursMinutes(last.durationMs(last.endMs)) + " · " + last.caught + " caught";
    }

    static String bestLine(List<HerbiboarSession> history) {
        if (history == null) return null;
        double best = 0;
        for (HerbiboarSession h : history) {
            if (h.durationMs(h.endMs) >= 15 * 60_000L) best = Math.max(best, h.catchesPerHour(h.endMs));
        }
        return best > 0 ? "Best " + Math.round(best) + "/hr" : null;
    }

    static Dimension paint(Graphics2D g, Snapshot s, boolean compact) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // The game's fonts are pixel fonts: crisp, unsmoothed, with the game's 1px shadow.
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font regular = FontManager.getRunescapeFont();
        Font small = FontManager.getRunescapeSmallFont();
        Font bold = FontManager.getRunescapeBoldFont();
        Color status = statusColor(s.state);
        return compact ? paintCompact(g, s, status, regular, small) : paintFull(g, s, status, regular, small, bold);
    }

    private static Dimension paintCompact(Graphics2D g, Snapshot s, Color status, Font regular, Font small) {
        g.setFont(regular);
        FontMetrics fm = g.getFontMetrics();
        String rate = "-".equals(s.perHour) ? "" : "  ·  " + s.perHour + "/hr";
        String main = s.state.getDescription() + "  ·  " + s.caught + " caught" + rate;
        int w = PAD + STRIP + 4 + 8 + 6 + fm.stringWidth(main) + 10 + g.getFontMetrics(small).stringWidth(s.runTime) + PAD;
        int h = fm.getHeight() + 8;
        frame(g, w, h, status);
        int base = (h + fm.getAscent() - fm.getDescent()) / 2;
        int x = PAD + STRIP;
        dot(g, x, base - fm.getAscent() / 2 - 3, status);
        x += 14;
        text(g, main, x, base, TEXT);
        g.setFont(small);
        text(g, s.runTime, w - PAD - g.getFontMetrics().stringWidth(s.runTime), base, MUTED);
        return new Dimension(w, h);
    }

    private static Dimension paintFull(Graphics2D g, Snapshot s, Color status, Font regular, Font small, Font bold) {
        int w = FULL_WIDTH;
        int left = PAD + STRIP + 3;
        int right = w - PAD;
        FontMetrics fr = g.getFontMetrics(regular), fs = g.getFontMetrics(small), fb = g.getFontMetrics(bold);
        Font hero = bold.deriveFont(bold.getSize2D() * 2);
        FontMetrics fh = g.getFontMetrics(hero);
        int y = PAD;
        int titleBase = y + fb.getAscent();
        int statusBase = titleBase + fr.getHeight() + 1;
        int div1 = statusBase + fr.getDescent() + 6;
        int heroBase = div1 + 7 + fh.getAscent() - 4;
        int heroLabelBase = heroBase + fs.getHeight();
        int div2 = heroLabelBase + fs.getDescent() + 6;
        int xpBase = div2 + 7 + fs.getAscent();
        int xpValueBase = xpBase + fr.getHeight() - 2;
        int meterLabelBase = xpValueBase + fs.getHeight() + 4;
        int meterY = meterLabelBase + 4;
        int meterH = 6;
        int footerBase = meterY + meterH + 6 + fs.getAscent();
        int historyBase = footerBase + fs.getHeight();
        boolean meter = s.level > 0 && s.level < Experience.MAX_REAL_LEVEL;
        if (!meter) {
            footerBase = xpValueBase + fs.getHeight() + 4;
            historyBase = footerBase + fs.getHeight();
        }
        int h = (s.history != null ? historyBase : footerBase) + fs.getDescent() + PAD - 2;

        frame(g, w, h, status);

        g.setFont(bold);
        text(g, "Herbi Hunter", left, titleBase, TEXT);
        g.setFont(small);
        text(g, VERSION, left + fb.stringWidth("Herbi Hunter") + 6, titleBase, MUTED);
        g.setFont(regular);
        text(g, s.runTime, right - fr.stringWidth(s.runTime), titleBase, MUTED);

        dot(g, left, statusBase - fr.getAscent() / 2 - 2, status);
        text(g, fit(fr, s.activity != null ? s.activity : s.state.getDescription(), right - left - 12), left + 12, statusBase, TEXT);
        hairline(g, left, right, div1);

        g.setFont(hero);
        text(g, String.valueOf(s.caught), left, heroBase, TEXT);
        g.setFont(small);
        text(g, "caught", left, heroLabelBase, MUTED);
        String rate = "-".equals(s.perHour) ? "- /hr" : s.perHour + " /hr";
        g.setFont(bold);
        text(g, rate, right - fb.stringWidth(rate), div1 + 7 + fb.getAscent(), TEXT);
        sparkline(g, s.spark, right - 84, div1 + 7 + fb.getHeight() + 3, 84, heroLabelBase - (div1 + 7 + fb.getHeight() + 3), status);
        hairline(g, left, right, div2);

        int mid = left + (right - left) / 2 + 4;
        g.setFont(small);
        text(g, "Hunter XP", left, xpBase, MUTED);
        text(g, "XP per hour", mid, xpBase, MUTED);
        g.setFont(regular);
        text(g, s.xp, left, xpValueBase, TEXT);
        text(g, s.xpPerHour, mid, xpValueBase, TEXT);

        if (meter) {
            g.setFont(small);
            text(g, "Level " + s.level, left, meterLabelBase, MUTED);
            String to = (s.level + 1) + (s.toLevel != null ? " in " + s.toLevel : "");
            text(g, to, right - fs.stringWidth(to), meterLabelBase, MUTED);
            g.setColor(TRACK);
            g.fillRoundRect(left, meterY, right - left, meterH, meterH, meterH);
            int fill = (int) Math.round((right - left) * s.levelPct / 100.0);
            if (fill > 0) {
                g.setColor(HUNTING);
                g.fillRoundRect(left, meterY, Math.max(meterH, fill), meterH, meterH, meterH);
            }
        }

        g.setFont(small);
        text(g, fit(fs, s.footer, right - left), left, footerBase, MUTED);
        if (s.history != null) {
            int bestW = s.best == null ? 0 : fs.stringWidth(s.best) + 8;
            text(g, fit(fs, s.history, right - left - bestW), left, historyBase, MUTED);
            if (s.best != null) text(g, s.best, right - fs.stringWidth(s.best), historyBase, MUTED);
        }
        return new Dimension(w, h);
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

    static String activityText(HerbiboarState state, String reason, int spot) {
        if (state == null) return "";
        if (reason == null) return state.getDescription();
        String r = reason.toLowerCase(Locale.ENGLISH);
        if (r.startsWith("the welcome screen")) return "Logging in";
        if (r.startsWith("taking a moment")) return "Taking a moment";
        if (r.startsWith("checking xp")) return "Checking XP";
        if (r.startsWith("looking at skills")) return "Looking at skills";
        if (r.startsWith("looking around")) return "Looking around";
        if (r.startsWith("glancing away")) return "Glancing away";
        if (r.contains("hole")) return "Taking the hole";
        if (r.startsWith("waiting for the herbiboar")) return "Waiting for the herbiboar";
        if (r.contains("herbiboar")) return "Harvesting";
        if (r.startsWith("attack") || r.startsWith("search ")) return "Attacking the tunnel";
        if (r.startsWith("inspect")) return state == HerbiboarState.START ? "Inspecting a start rock" : "Searching spot " + Math.max(1, spot);
        if (r.startsWith("closing on") || r.startsWith("walking") || r.startsWith("bringing")) {
            return state == HerbiboarState.TUNNEL ? "Running to the tunnel"
                    : state == HerbiboarState.START ? "Running to a start rock" : "Running to the next spot";
        }
        return state.getDescription();
    }

    /** Hourly rates need a few catches: a single catch a minute in reads as 2M XP/hr. Pure. */
    static boolean ratesReady(long seconds, int caught) {
        return seconds >= 60 && (seconds >= RATE_MIN_SECONDS || caught >= RATE_MIN_CATCHES);
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

    static double levelProgress(int xp, int levelXp, int nextLevelXp) {
        if (nextLevelXp <= levelXp) return 100;
        return Math.max(0, Math.min(100, (xp - levelXp) * 100.0 / (nextLevelXp - levelXp)));
    }

    static Color statusColor(HerbiboarState state) {
        if (state == null) return IDLE;
        switch (state) {
            case START:
            case TRAIL:
            case TUNNEL:
            case HARVEST:
                return HUNTING;
            case BANK:
            case RETURN_FROM_ISLAND:
            case RESTOCK:
            case TRAVEL:
                return AWAY;
            case RESET:
                return RECOVERING;
            default:
                return IDLE;
        }
    }
}
