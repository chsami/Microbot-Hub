package net.runelite.client.plugins.microbot.chatbot;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** Pure input validation kept independent of the game client. */
final class ChatbotText {
    private static final Pattern TRADE_SPAM = Pattern.compile(
        "(?i)(?:\\b(?:buying|selling|wts|wtb)\\b.*(?:\\b\\d+(?:[.,]\\d+)?[km]\\b|https?://|www\\.)"
            + "|\\b(?:doubling money|double your gold|flower poker|dice game|gambling)\\b)");

    private ChatbotText() {}

    static String normalizeName(String name) {
        return name == null ? "" : name.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
    }

    static boolean nameInList(String name, String csv) {
        String normalized = normalizeName(name);
        if (csv == null || normalized.isEmpty()) return false;
        for (String candidate : csv.split(",")) {
            if (normalized.equals(normalizeName(candidate))) return true;
        }
        return false;
    }

    static boolean matchesKeyword(String message, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return true;
        boolean hasKeyword = false;
        for (String candidate : keyword.split(",")) {
            String term = candidate.trim();
            if (term.isEmpty()) continue;
            hasKeyword = true;
            if (message != null && Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(term)
                    + "(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(message).find()) return true;
        }
        return !hasKeyword;
    }

    static boolean looksLikeTradeSpam(String message) {
        return message != null && TRADE_SPAM.matcher(message).find();
    }

    static String sanitizeResponse(String response, int maxLength) {
        if (response == null) return "";
        int limit = Math.max(20, Math.min(80, maxLength));
        // The keyboard supports the ordinary printable OSRS chat alphabet. Remove
        // control characters, tags and leading channel/command prefixes from model output.
        String text = response.replaceAll("<[^>]*>", " ")
            .replaceAll("[\\r\\n\\t]+", " ")
            .replaceAll("[^\\x20-\\x7e]", "")
            .replaceAll(" +", " ").trim();
        while (text.startsWith("/") || text.startsWith("::")) {
            text = text.startsWith("::") ? text.substring(2).trim() : text.substring(1).trim();
        }
        return text.length() > limit ? text.substring(0, limit).trim() : text;
    }

    static int responseDelayMs(int minDelay, int maxDelay) {
        int min = Math.max(0, Math.min(15_000, minDelay));
        int max = Math.max(0, Math.min(15_000, maxDelay));
        int lower = Math.min(min, max);
        int upper = Math.max(min, max);
        return ThreadLocalRandom.current().nextInt(lower, upper + 1);
    }
}
