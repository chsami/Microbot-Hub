package net.runelite.client.plugins.microbot.geflipper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the Old School "System update in: MM:SS" countdown shown at the bottom right. */
final class SystemUpdate {
    static final String MARKER = "System update in";
    /** Below this many seconds Finish stops waiting for sell offers and only collects. */
    static final int STOP_WAITING_FOR_SALES_SECONDS = 60;

    private static final Pattern COUNTDOWN = Pattern.compile(
        "system\\s+update\\s+in\\s*:?\\s*(?:(\\d+)\\s*:\\s*)?(\\d{1,2})\\s*:\\s*(\\d{2})", Pattern.CASE_INSENSITIVE);

    private SystemUpdate() { }

    /** Returns the remaining seconds, or -1 when the text is not an update countdown. */
    static int parseSeconds(String widgetText) {
        if (widgetText == null) return -1;
        String plain = widgetText.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "")
            .replace('\u00a0', ' ').trim();
        Matcher matcher = COUNTDOWN.matcher(plain);
        if (!matcher.find()) return -1;
        try {
            // "H:MM:SS" has an optional leading group; "MM:SS" is the common form.
            long first = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
            long minutes = Long.parseLong(matcher.group(2));
            long seconds = Long.parseLong(matcher.group(3));
            long total = first * 3600 + minutes * 60 + seconds;
            return (int) Math.min(Integer.MAX_VALUE, total);
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }
}
