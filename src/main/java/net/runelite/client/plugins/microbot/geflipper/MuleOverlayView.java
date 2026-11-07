package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the Mule receiver overlay says, as plain rows. The in-game overlay and the login-screen window both
 * draw these same rows, so the two can never disagree about a number or a status.
 */
final class MuleOverlayView {
    enum Tone { POSITIVE, NEGATIVE, WARNING, MUTED }

    static final Color POSITIVE_COLOR = new Color(0x52D273);
    static final Color NEGATIVE_COLOR = new Color(0xFF6666);
    static final Color TITLE_COLOR = Color.CYAN;

    /** One line. A row with an empty value is a message and is drawn across the whole width. */
    static final class Row {
        final String label;
        final String value;
        final Tone tone;

        Row(String label, String value, Tone tone) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
            this.tone = tone == null ? Tone.MUTED : tone;
        }
    }

    private MuleOverlayView() { }

    static String title() {
        return "Microbot Flipper v" + FlipperPlugin.version;
    }

    static Color color(Tone tone) {
        switch (tone) {
            case POSITIVE:
                return POSITIVE_COLOR;
            case NEGATIVE:
                return NEGATIVE_COLOR;
            case WARNING:
                return Color.ORANGE;
            default:
                return Color.LIGHT_GRAY;
        }
    }

    static Tone linkTone(MuleConnectionMonitor.Snapshot connection) {
        if (connection == null) return Tone.WARNING;
        switch (connection.state) {
            case READY:
            case STANDBY:
            case SENDER_SEEN:
                return Tone.POSITIVE;
            case OFFLINE:
            case WRONG_MULE:
            case STALE:
                return Tone.NEGATIVE;
            case OFF:
                return Tone.MUTED;
            default:
                return Tone.WARNING;
        }
    }

    /** A balance without a sign: "1.20M gp". */
    static String amount(long gp) {
        String text = FlipperOverlay.formatProfit(Math.max(0L, gp));
        return text.startsWith("+") ? text.substring(1) : text;
    }

    /**
     * The receiver's rows. {@code loggedIn} says whether the coin count is live: the Mule spends most of its
     * time logged out, so a count seen earlier is labelled instead of passed off as current.
     */
    static List<Row> receiverRows(MuleConnectionMonitor.Snapshot link, MuleLedger.Snapshot ledger,
                                  boolean loggedIn, String status) {
        MuleLedger.Snapshot totals = ledger == null ? MuleLedger.Snapshot.EMPTY : ledger;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Flipper link:", link == null ? "Checking" : link.label(), linkTone(link)));
        String held = totals.heldGp < 0 ? "Unknown" : amount(totals.heldGp) + (loggedIn ? "" : " (last seen)");
        rows.add(new Row("Total held:", held, totals.heldGp > 0 ? Tone.POSITIVE : Tone.MUTED));
        rows.add(new Row("Muled (session):", FlipperOverlay.formatProfit(totals.sessionGp),
            totals.sessionGp > 0 ? Tone.POSITIVE : Tone.MUTED));
        long perHour = totals.gpPerHour();
        rows.add(new Row("Muled GP/hr:", FlipperOverlay.formatGpHr(perHour), perHour > 0 ? Tone.POSITIVE : Tone.MUTED));
        rows.add(new Row("Muled (all time):", FlipperOverlay.formatProfit(totals.lifetimeGp),
            totals.lifetimeGp > 0 ? Tone.POSITIVE : Tone.MUTED));
        if (status != null && !status.isEmpty()) rows.add(new Row(status, "", Tone.WARNING));
        return Collections.unmodifiableList(rows);
    }
}
