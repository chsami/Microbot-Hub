package net.runelite.client.plugins.microbot.autofarmer;
import java.util.Locale;
/** Rewards are retained without consuming, destroying, or dropping them. */
final class BankRetention {
    static boolean keep(String name) {
        if (name == null || name.trim().isEmpty()) return true;
        String n = name.toLowerCase(Locale.ROOT).trim();
        return n.matches(".*\\b(lamp|lamps)\\b.*") || n.contains("book of knowledge")
                || n.contains("books of knowledge") || n.contains("tome of experience")
                || n.contains("experience book") || n.contains("experience reward") || n.contains("xp reward");
    }
}
