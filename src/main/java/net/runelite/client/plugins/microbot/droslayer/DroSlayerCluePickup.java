package net.runelite.client.plugins.microbot.droslayer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Correlates the game's tier-limit rejection with one spaced clue pickup attempt. */
final class DroSlayerCluePickup {
    private final Set<String> blockedTiers = new HashSet<>();
    private String pendingTier;
    private long attemptedAt;

    static String tier(String name) {
        if (name == null) return null;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (!lower.startsWith("scroll box (") && !lower.startsWith("clue scroll (")) return null;
        int start = lower.indexOf('('), end = lower.indexOf(')', start);
        return end > start ? lower.substring(start + 1, end).trim() : null;
    }

    synchronized boolean mayAttempt(String name, long now) {
        String tier = tier(name);
        if (tier == null) return true;
        if (blockedTiers.contains(tier) || pendingTier != null && now - attemptedAt < 5_000L) return false;
        pendingTier = tier;
        attemptedAt = now;
        return true;
    }

    synchronized boolean onMessage(String message, long now) {
        if (message == null || pendingTier == null || now - attemptedAt > 15_000L) return false;
        String lower = message.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        if (!lower.contains("can't take any more of that tier of scroll box")
                && !lower.contains("already have a clue scroll of this tier")
                && !lower.contains("already have a clue scroll of that tier")) return false;
        return blockedTiers.add(pendingTier);
    }

    synchronized void reset() {
        blockedTiers.clear();
        pendingTier = null;
        attemptedAt = 0L;
    }
}
