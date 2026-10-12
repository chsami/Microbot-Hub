package net.runelite.client.plugins.microbot.bankseller;

/**
 * Pure login boundary guard. A run stays bound to its first valid account even
 * across logout, loading, clock resets or an attempted switch back afterward.
 */
final class BankSellerLoginReadiness {
    private String boundProfile;
    private boolean changedAccount;
    private int firstTick = -1;
    private int lastTick = -1;

    boolean observe(boolean loggedIn, boolean playerPresent, String profile, int tick) {
        if (!loggedIn || !playerPresent || !validProfile(profile) || tick < 0) {
            resetTiming();
            return false;
        }
        if (boundProfile == null) {
            boundProfile = profile;
        } else if (!boundProfile.equals(profile)) {
            changedAccount = true;
            resetTiming();
            return false;
        }
        if (changedAccount) {
            return false;
        }
        if (firstTick < 0 || tick < lastTick) {
            firstTick = tick;
            lastTick = tick;
            return false;
        }
        lastTick = tick;
        return (long) tick - firstTick >= 2;
    }

    boolean accountChanged() {
        return changedAccount;
    }

    boolean matchesProfile(String profile) {
        return !changedAccount && boundProfile != null && validProfile(profile)
                && boundProfile.equals(profile);
    }

    /** Invalidates readiness only; never releases this run's account binding. */
    void resetTiming() {
        firstTick = -1;
        lastTick = -1;
    }

    private static boolean validProfile(String profile) {
        return profile != null && !profile.trim().isEmpty();
    }
}
