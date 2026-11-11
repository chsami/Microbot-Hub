package net.runelite.client.plugins.microbot.droelf;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.ui.ClientUI;

import java.awt.*;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * MKE-style long break scheduler adapted for DroElf.
 *
 * A due break is queued until the character is naturally back at the locked elf
 * and idle.  It never redirects the bank route or inserts a second walking
 * system.  AFK breaks park the mouse once; logout breaks return to the same
 * world when a Microbot login profile is available.
 */
final class DroElfBreakManager {
    private final DroElfConfig config;

    private boolean breakActive;
    private boolean afkBreakActive;
    private boolean logoutBreakActive;
    private boolean waitingForManualLogin;

    private long nextBreakAt;
    private long breakEndsAt;
    private long resumeAfterLoginAt;
    private long nextLoginAttemptAt;

    private int preBreakWorld = -1;
    private Point originalMousePosition;
    private String originalTitle = "RuneLite";
    private String status = "";

    DroElfBreakManager(DroElfConfig config) {
        this.config = config;
        try {
            if (ClientUI.getFrame() != null) originalTitle = ClientUI.getFrame().getTitle();
        } catch (Exception ignored) {
        }
        scheduleNextBreak();
    }

    void reset() {
        breakActive = false;
        afkBreakActive = false;
        logoutBreakActive = false;
        waitingForManualLogin = false;
        breakEndsAt = 0L;
        resumeAfterLoginAt = 0L;
        nextLoginAttemptAt = 0L;
        originalMousePosition = null;
        status = "";
        restoreTitle();
        scheduleNextBreak();
    }

    /**
     * Must be called before DroElf's normal logged-in guard.  That lets an
     * intentional logout break expire and log back in without another thread.
     * Returns true while the normal thieving state machine should stay paused.
     */
    boolean update(boolean safeForBreak) {
        if (!config.enableCustomBreaks()) return false;

        long now = System.currentTimeMillis();

        if (breakActive) {
            if (afkBreakActive) return updateAfkBreak(now);
            if (logoutBreakActive) return updateLogoutBreak(now);
        }

        if (now < nextBreakAt) return false;

        // MKE principle: don't interrupt an unsafe workflow.  DroElf simply
        // keeps its normal bank/return route until it is naturally at the elf.
        if (!safeForBreak) {
            status = "Break queued";
            return false;
        }

        startRandomBreak(now);
        return true;
    }

    boolean isBreakActive() {
        return breakActive;
    }

    String getStatus() {
        return status == null || status.isBlank() ? "Break" : status;
    }

    String getTimeUntilNextBreak() {
        if (!config.enableCustomBreaks()) return "Disabled";
        if (breakActive) return getStatus();
        long remaining = Math.max(0L, nextBreakAt - System.currentTimeMillis());
        return formatDuration(remaining);
    }

    void shutdown() {
        breakActive = false;
        afkBreakActive = false;
        logoutBreakActive = false;
        waitingForManualLogin = false;
        restoreTitle();
    }

    private void startRandomBreak(long now) {
        boolean logout = randomBetween(0, 99) < clamp(config.logoutBreakChance(), 0, 100);
        if (logout) startLogoutBreak(now);
        else startAfkBreak(now);
    }

    private void startAfkBreak(long now) {
        int min = Math.max(1, config.afkBreakMinDuration());
        int max = Math.max(min, config.afkBreakMaxDuration());
        int minutes = randomBetween(min, max);

        breakActive = true;
        afkBreakActive = true;
        logoutBreakActive = false;
        breakEndsAt = now + TimeUnit.MINUTES.toMillis(minutes);
        originalMousePosition = safeMousePosition();
        status = "AFK break " + formatDuration(breakEndsAt - now);

        // One off-screen movement only; no intermediate edge click.
        try { Rs2Antiban.moveMouseOffScreen(); } catch (Exception ignored) {}
        updateTitle();
        Microbot.log("[DroElf] Started AFK break for " + minutes + " minutes");
    }

    private void startLogoutBreak(long now) {
        int min = Math.max(1, config.logoutBreakMinDuration());
        int max = Math.max(min, config.logoutBreakMaxDuration());
        int minutes = randomBetween(min, max);

        breakActive = true;
        afkBreakActive = false;
        logoutBreakActive = true;
        waitingForManualLogin = false;
        preBreakWorld = safeCurrentWorld();
        breakEndsAt = now + TimeUnit.MINUTES.toMillis(minutes);
        nextLoginAttemptAt = breakEndsAt;
        status = "Logout break " + formatDuration(breakEndsAt - now);
        updateTitle();

        try {
            Rs2Player.logout();
        } catch (Exception ex) {
            Microbot.log("[DroElf] Logout request failed; holding break anyway: " + ex.getMessage());
        }
        Microbot.log("[DroElf] Started logout break for " + minutes + " minutes");
    }

    private boolean updateAfkBreak(long now) {
        if (now < breakEndsAt) {
            status = "AFK break " + formatDuration(breakEndsAt - now);
            updateTitle();
            return true;
        }

        afkBreakActive = false;
        breakActive = false;
        status = "Resuming";
        restoreMouseWithVariation();
        restoreTitle();
        scheduleNextBreak();
        Microbot.log("[DroElf] AFK break ended");
        return false;
    }

    private boolean updateLogoutBreak(long now) {
        if (now < breakEndsAt) {
            status = "Logout break " + formatDuration(breakEndsAt - now);
            updateTitle();
            return true;
        }

        if (Microbot.isLoggedIn()) {
            if (resumeAfterLoginAt == 0L) {
                resumeAfterLoginAt = now + randomBetween(4_000, 7_500);
                status = "Settling after login";
                return true;
            }
            if (now < resumeAfterLoginAt) {
                status = "Settling after login";
                return true;
            }

            logoutBreakActive = false;
            breakActive = false;
            waitingForManualLogin = false;
            resumeAfterLoginAt = 0L;
            status = "Resuming";
            restoreTitle();
            scheduleNextBreak();
            Microbot.log("[DroElf] Logout break ended");
            return false;
        }

        // Break time has elapsed but we are still logged out.
        if (LoginManager.getActiveProfile() == null) {
            waitingForManualLogin = true;
            status = "Break over - log in";
            updateTitle();
            return true;
        }

        if (now >= nextLoginAttemptAt) {
            nextLoginAttemptAt = now + 15_000L;
            waitingForManualLogin = false;
            status = "Logging back in";
            try {
                new Login(preBreakWorld > 300 ? preBreakWorld : 360);
            } catch (Exception ex) {
                Microbot.log("[DroElf] Break login attempt failed: " + ex.getMessage());
            }
        }
        updateTitle();
        return true;
    }

    private void scheduleNextBreak() {
        if (!config.enableCustomBreaks()) {
            nextBreakAt = Long.MAX_VALUE;
            return;
        }
        int min = Math.max(1, config.minBreakInterval());
        int max = Math.max(min, config.maxBreakInterval());
        int minutes = randomBetween(min, max);
        nextBreakAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes);
        Microbot.log("[DroElf] Next adaptive break in about " + minutes + " minutes");
    }

    private void restoreMouseWithVariation() {
        if (originalMousePosition == null) return;
        int x = originalMousePosition.x + randomBetween(-45, 45);
        int y = originalMousePosition.y + randomBetween(-45, 45);
        try {
            if (Microbot.naturalMouse != null) Microbot.naturalMouse.moveTo(x, y);
            else Microbot.getMouse().move(x, y);
        } catch (Exception ignored) {
        }
        originalMousePosition = null;
    }

    private Point safeMousePosition() {
        try { return Microbot.getMouse().getMousePosition(); }
        catch (Exception ignored) { return null; }
    }

    private int safeCurrentWorld() {
        try { return Rs2Player.getWorld(); }
        catch (Exception ignored) { return -1; }
    }

    private void updateTitle() {
        try {
            if (ClientUI.getFrame() == null) return;
            ClientUI.getFrame().setTitle("[DroElf - " + getStatus() + "] " + originalTitle);
        } catch (Exception ignored) {
        }
    }

    private void restoreTitle() {
        try {
            if (ClientUI.getFrame() != null) ClientUI.getFrame().setTitle(originalTitle);
        } catch (Exception ignored) {
        }
    }

    private static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static int randomBetween(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
