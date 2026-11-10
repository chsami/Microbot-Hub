package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only recognizes an explicit numeric recovery payment; never a generic Yes. */
public final class RecoveryQuote {
    private static final Pattern PAY = Pattern.compile("(?i)^yes,?\\s+pay\\s+([0-9][0-9,]*)\\s+(?:coins?|gp)\\.?$");
    private RecoveryQuote() { }
    public static OptionalLong amount(String text) {
        if (text == null) return OptionalLong.empty();
        Matcher matcher = PAY.matcher(clean(text));
        if (!matcher.matches()) return OptionalLong.empty();
        try { return OptionalLong.of(Long.parseLong(matcher.group(1).replace(",", ""))); }
        catch (NumberFormatException ex) { return OptionalLong.empty(); }
    }
    public static String clean(String text) {
        return text == null ? "" : text.replaceAll("<[^>]*>", "").replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
    }
    public static boolean warnsCargoLoss(String text) {
        String s = clean(text).toLowerCase(Locale.ROOT);
        return s.contains("cargo") && (s.contains("lost") || s.contains("lose") || s.contains("destroy") || s.contains("discard"));
    }
    public static boolean destructive(String text) {
        String s = clean(text).toLowerCase(Locale.ROOT);
        return s.contains("destroy boat") || s.contains("destroy your boat") || s.contains("buy a new boat")
                || s.contains("buy-boat") || s.contains("customise-boat") || s.contains("customize-boat");
    }
}
