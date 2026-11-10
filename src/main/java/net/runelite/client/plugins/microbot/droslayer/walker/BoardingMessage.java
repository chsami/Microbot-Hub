package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.Optional;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.plugins.microbot.droslayer.walker.sailing.core.RecoveryQuote;

/** Parses explicit boarding results without inferring unobserved boat or port state. */
final class BoardingMessage {
    private static final Pattern ELSEWHERE = Pattern.compile(
            "^You cannot board your boat as it is currently docked at (.+?)[.!]?$", Pattern.CASE_INSENSITIVE);
    private BoardingMessage() { }

    static boolean lostAtSea(String message) {
        String text = RecoveryQuote.clean(message).toLowerCase(Locale.ROOT);
        return text.contains("you cannot board your boat as it is lost at sea")
                || text.contains("your boat is lost at sea");
    }

    static Optional<String> dockedElsewhere(String message) {
        if (message == null) return Optional.empty();
        Matcher match = ELSEWHERE.matcher(message.trim());
        if (!match.matches()) return Optional.empty();
        String key = PortCatalog.normalize(match.group(1));
        PortCatalog.Port port = PortCatalog.byKey(key);
        return port != null && port.gangplankId > 0 ? Optional.of(key) : Optional.empty();
    }
}
