package net.runelite.client.plugins.microbot.accountstarter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountstarter.logic.PanelModel;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class AccountStarterOverlay extends Overlay {
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
    private static final String TITLE = "Account Starter";
    private static final String VERSION = "v" + AccountStarterPlugin.version;
    private static final String TARGET = TITLE + " " + VERSION + " panel";

    private final AccountStarterConfig config;
    private final AccountStarterScript script;
    private final ConfigManager configManager;
    private Boolean menuFor;

    @Inject
    AccountStarterOverlay(AccountStarterPlugin plugin, AccountStarterConfig config,
                               AccountStarterScript script, ConfigManager configManager) {
        super(plugin);
        this.config = config;
        this.script = script;
        this.configManager = configManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showPanel() || !Microbot.isLoggedIn()) return null;
        boolean compact = config.compactPanel();
        refreshMenu(compact);
        long now = System.currentTimeMillis();
        PanelModel.Live live = PanelModel.Live.builder()
                .activity(script.getStatus())
                .settled(script.isSettled())
                .walking(script.isWalking())
                .walkingTo(script.walkingTo())
                .finished(script.isFinished())
                .stopped(script.isStopping())
                .sample(script.getXpSample())
                .nowMs(now)
                .sessionMs(now - script.getSessionStartMs())
                .build();
        PanelModel.View v = PanelModel.from(script.getLastSnapshot(), script.choices(), script.getCurrentDecision(),
                script.getParked(), live);
        return paint(graphics, v, compact, script.getHistoryLine());
    }

    private void refreshMenu(boolean compact) {
        if (menuFor != null && menuFor == compact) return;
        menuFor = compact;
        getMenuEntries().clear();
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, compact ? "Expand" : "Minimise", TARGET, e ->
                configManager.setConfiguration(AccountStarterConfig.GROUP, AccountStarterConfig.COMPACT_PANEL_KEY, !compact));
    }

    static Dimension paint(Graphics2D g, PanelModel.View v, boolean compact) {
        return paint(g, v, compact, null);
    }

    /** {@code history} is the earlier-sessions line, last in the full panel; null leaves it out. */
    static Dimension paint(Graphics2D g, PanelModel.View v, boolean compact, String history) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font regular = FontManager.getRunescapeFont();
        Font small = FontManager.getRunescapeSmallFont();
        Font bold = FontManager.getRunescapeBoldFont();
        Color status = color(v.getStatus());
        return compact ? paintCompact(g, v, status, regular, small) : paintFull(g, v, status, regular, small, bold, history);
    }

    private static Dimension paintCompact(Graphics2D g, PanelModel.View v, Color status, Font regular, Font small) {
        g.setFont(regular);
        FontMetrics fm = g.getFontMetrics();
        FontMetrics fs = g.getFontMetrics(small);
        String main = fit(fm, v.getCompact(), 240);
        int w = PAD + STRIP + 4 + 14 + fm.stringWidth(main) + 10 + fs.stringWidth(v.getTime()) + PAD;
        int h = fm.getHeight() + 8;
        frame(g, w, h, status);
        int base = (h + fm.getAscent() - fm.getDescent()) / 2;
        dot(g, PAD + STRIP, base - fm.getAscent() / 2 - 3, status);
        text(g, main, PAD + STRIP + 14, base, TEXT);
        g.setFont(small);
        text(g, v.getTime(), w - PAD - fs.stringWidth(v.getTime()), base, MUTED);
        return new Dimension(w, h);
    }

    private static Dimension paintFull(Graphics2D g, PanelModel.View v, Color status, Font regular, Font small, Font bold,
                                       String history) {
        int w = FULL_WIDTH;
        int left = PAD + STRIP + 3;
        int right = w - PAD;
        FontMetrics fr = g.getFontMetrics(regular), fs = g.getFontMetrics(small), fb = g.getFontMetrics(bold);
        Font heroFont = bold.deriveFont(bold.getSize2D() * 2);
        FontMetrics fh = g.getFontMetrics(heroFont);
        boolean hero = v.getHeroValue() != null;
        int titleBase = PAD + fb.getAscent();
        int statusBase = titleBase + fr.getHeight() + 1;
        String[] doing = wrap(fr, v.getActivity(), right - left - 12);
        int lineH = fr.getHeight() - 2;
        int bottom = statusBase + (doing.length - 1) * lineH + fr.getDescent();
        int div1 = bottom + 6;
        int heroBase = div1 + 7 + fh.getAscent() - 4;
        int heroLabelBase = heroBase + fs.getHeight();
        int meterY = heroLabelBase + fs.getDescent() + 5;
        if (hero) bottom = meterY + METER_H;
        int footerBase = bottom + 4 + fs.getAscent();
        bottom = footerBase + fs.getDescent();
        int rowH = fs.getHeight() + 1;
        int rows = v.getParked().size();
        int div2 = bottom + 5;
        int noticeBase = div2 + 5 + fs.getAscent();
        if (rows > 0) bottom = noticeBase + (rows - 1) * rowH + fs.getDescent();
        int historyBase = bottom + 4 + fs.getAscent();
        if (history != null) bottom = historyBase + fs.getDescent();
        int h = bottom + PAD - 2;

        frame(g, w, h, status);

        g.setFont(bold);
        text(g, TITLE, left, titleBase, MUTED);
        g.setFont(regular);
        text(g, v.getTime(), right - fr.stringWidth(v.getTime()), titleBase, MUTED);
        dot(g, left, statusBase - fr.getAscent() / 2 - 2, status);
        for (int i = 0; i < doing.length; i++) text(g, doing[i], left + 12, statusBase + i * lineH, TEXT);

        if (hero) {
            hairline(g, left, right, div1);
            g.setFont(heroFont);
            text(g, v.getHeroValue(), left, heroBase, TEXT);
            text(g, v.getHeroTarget(), left + fh.stringWidth(v.getHeroValue()), heroBase, SPARK);
            g.setFont(small);
            text(g, v.getHeroLabel(), left, heroLabelBase, MUTED);
            if (v.getEta() != null) {
                text(g, v.getEtaLabel(), right - fs.stringWidth(v.getEtaLabel()), heroLabelBase, MUTED);
                g.setFont(bold);
                text(g, v.getEta(), right - fb.stringWidth(v.getEta()), heroBase, TEXT);
            }
            g.setColor(TRACK);
            g.fillRoundRect(left, meterY, right - left, METER_H, METER_H, METER_H);
            int fill = (int) Math.round((right - left) * v.getMeterPct() / 100.0);
            if (fill > 0) {
                g.setColor(status);
                g.fillRoundRect(left, meterY, Math.max(METER_H, fill), METER_H, METER_H, METER_H);
            }
        }

        g.setFont(small);
        int versionW = fs.stringWidth(VERSION);
        if (v.getFooter() != null) text(g, fit(fs, v.getFooter(), right - left - versionW - 8), left, footerBase, MUTED);
        text(g, VERSION, right - versionW, footerBase, MUTED);

        if (rows > 0) {
            hairline(g, left, right, div2);
            String more = v.getMoreParked() > 0 ? "+" + v.getMoreParked() : "";
            int moreW = more.isEmpty() ? 0 : fs.stringWidth(more) + 6;
            int x = status == RECOVERING ? left : left + 12;
            if (status != RECOVERING) dot(g, left, noticeBase - fs.getAscent() + 1, RECOVERING);
            for (int i = 0; i < rows; i++) {
                boolean last = i == rows - 1;
                text(g, fit(fs, v.getParked().get(i), right - x - (last ? moreW : 0)), x, noticeBase + i * rowH, TEXT);
            }
            text(g, more, right - fs.stringWidth(more), noticeBase + (rows - 1) * rowH, MUTED);
        }
        if (history != null) text(g, fit(fs, history, right - left), left, historyBase, MUTED);
        return new Dimension(w, h);
    }

    private static String[] wrap(FontMetrics fm, String text, int width) {
        if (fm.stringWidth(text) <= width) return new String[]{text};
        int split = text.indexOf(": ");
        if (split > 0 && fm.stringWidth(text.substring(0, split)) <= width) {
            return new String[]{text.substring(0, split), fit(fm, text.substring(split + 2), width)};
        }
        split = -1;
        for (int i = text.indexOf(' '); i > 0 && fm.stringWidth(text.substring(0, i)) <= width; i = text.indexOf(' ', i + 1)) {
            if (split < 0 || Math.abs(i * 2 - text.length()) < Math.abs(split * 2 - text.length())) split = i;
        }
        if (split < 0) return new String[]{fit(fm, text, width)};
        return new String[]{text.substring(0, split), fit(fm, text.substring(split + 1), width)};
    }

    static Color color(PanelModel.Status s) {
        switch (s) {
            case ACTIVE: return ACTIVE;
            case AWAY: return AWAY;
            case RECOVERING: return RECOVERING;
            default: return IDLE;
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
