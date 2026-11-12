package net.runelite.client.plugins.microbot.droelf;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Random;

/**
 * The transferable parts of the MKE Wintertodt behavior profile, adapted for
 * rapid repetitive elf thieving.  It deliberately does not own pathing,
 * banking, target selection or the existing DroElf off-screen/inventory-glance
 * logic.
 */
final class DroElfAdaptiveBehavior {
    private static final long MIN_NUDGE_GAP_MS = 2_200L;

    private final DroElfConfig config;
    private final Random random = new Random();

    private long lastMouseNudgeAt;
    private long lastCameraTurnAt;
    private long nextCameraTurnEligibleAt;

    DroElfAdaptiveBehavior(DroElfConfig config) {
        this.config = config;
    }

    void configureNativeAntiban() {
        try {
            // Use Microbot's native adaptive/play-style machinery, but select the
            // activity-appropriate template rather than Wintertodt's firemaking one.
            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.antibanSetupTemplates.applyThievingSetup();
            Rs2Antiban.setActivity(Activity.GENERAL_THIEVING);

            // MKE uses its own long-break system, so native microbreaks are disabled.
            Rs2AntibanSettings.takeMicroBreaks = false;
            Rs2AntibanSettings.microBreakChance = 0.0;

            // Preserve the useful adaptive MKE profile components.
            Rs2AntibanSettings.antibanEnabled = true;
            Rs2AntibanSettings.usePlayStyle = true;
            Rs2AntibanSettings.randomIntervals = false;
            Rs2AntibanSettings.simulateFatigue = true;
            Rs2AntibanSettings.simulateAttentionSpan = true;
            Rs2AntibanSettings.behavioralVariability = true;
            Rs2AntibanSettings.nonLinearIntervals = true;
            Rs2AntibanSettings.profileSwitching = true;
            Rs2AntibanSettings.contextualVariability = true;
            Rs2AntibanSettings.timeOfDayAdjust = false;
            Rs2AntibanSettings.dynamicIntensity = false;
            Rs2AntibanSettings.dynamicActivity = false;
            Rs2AntibanSettings.universalAntiban = false;
            Rs2AntibanSettings.simulateMistakes = true;
            Rs2AntibanSettings.naturalMouse = true;
            // Do not use Microbot's native action cooldown here. Its play-style
            // cooldown can last long enough to materially reduce pickpocket XP/hr.
            // DroElf keeps the same occasional-hesitation concept with a short,
            // local Gaussian pause in afterSuccessfulClick().
            Rs2AntibanSettings.actionCooldownChance = 0.0;

            // DroElf already owns these two behaviors. Leaving Microbot's global
            // versions on as well causes duplicate mouse moves/jitter.
            Rs2AntibanSettings.moveMouseRandomly = false;
            Rs2AntibanSettings.moveMouseRandomlyChance = 0.0;
            Rs2AntibanSettings.moveMouseOffScreen = false;
            Rs2AntibanSettings.moveMouseOffScreenChance = 0.0;

            // User-selected native mouse speed for DroElf.
            Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);
        } catch (Exception ex) {
            Microbot.log("[DroElf] Adaptive antiban setup skipped: " + ex.getMessage());
        }
    }

    void resetSession() {
        lastMouseNudgeAt = 0L;
        lastCameraTurnAt = 0L;
        scheduleNextCameraWindow();
    }

    int reactionDelayMs(SessionPersonality personality) {
        int base = HumanBehaviorProfile.pauseDelayMs(config.humanizedTiming(), 50, 250, false);
        if (!config.humanizedTiming()) return base;

        // MKE-style Gaussian timing: most reactions stay close to the normal
        // delay, with occasional shorter/longer values instead of a flat range.
        int spread = Math.max(12, base / 4);
        int gaussian = (int) Math.round(base + random.nextGaussian() * spread);
        gaussian = Math.max(25, Math.min(450, gaussian));
        return (int) Math.max(0L, personality.reaction(gaussian));
    }

    void maybePreClickPause(SessionPersonality personality) {
        if (!config.humanizedTiming()) return;

        // A small hesitation is much less frequent than the main reaction delay.
        int chance = Math.max(3, Math.min(14, personality.earlyHoverChance() / 2 + 2));
        if (!personality.roll(chance)) return;

        int pause = gaussianClamped(145, 95, 45, 520);
        sleepQuietly((int) personality.reaction(pause));
    }

    /**
     * Optional post-click micro movement.  A strict gap and a single movement
     * are used so repetitive pickpocketing never becomes a visible two-point
     * mouse wobble/jitter loop.
     */
    void afterSuccessfulClick(SessionPersonality personality, boolean mouseAlreadyMoved) {
        if (!config.humanizedTiming()) return;

        if (!mouseAlreadyMoved && config.randomMouseMovements()) {
            long now = System.currentTimeMillis();
            if (now - lastMouseNudgeAt >= MIN_NUDGE_GAP_MS) {
                int moveChance = (int) Math.round(12 + Math.abs(random.nextGaussian()) * 7);
                moveChance = Math.max(5, Math.min(28, moveChance));
                if (personality.roll(moveChance)) {
                    nudgeMouseOnce();
                    lastMouseNudgeAt = System.currentTimeMillis();
                }
            }
        }

        if (config.adaptiveActionCooldowns()) {
            // Preserve the MKE "hesitate after an action" behavior without using
            // Rs2Antiban.actionCooldown(), whose native play-style timeout can be
            // several seconds.  This is intentionally brief: about 15% of successful
            // pickpockets get a small Gaussian pause, normally around 90-190 ms and
            // never more than 320 ms before personality scaling.
            if (personality.roll(15)) {
                int cooldown = gaussianClamped(135, 55, 45, 320);
                sleepQuietly((int) Math.max(25L, personality.reaction(cooldown)));
            }
        }
    }

    /**
     * MKE-style subtle camera yaw/pitch adjustment. Returns true when a turn was
     * actually performed so DroElf can skip the current pickpocket iteration.
     */
    boolean maybeCameraTurn(boolean safeAndIdle) {
        if (!config.cameraTurns() || !config.humanizedTiming() || !safeAndIdle) return false;

        long now = System.currentTimeMillis();
        if (now < nextCameraTurnEligibleAt) return false;

        int frequencySeconds = Math.max(1, config.cameraMovementFrequency());
        long minimumGap = frequencySeconds * 1000L;
        long sinceLast = lastCameraTurnAt == 0L ? minimumGap : now - lastCameraTurnAt;
        if (sinceLast < minimumGap) return false;

        // Same MKE concept: probability slowly increases the longer we have not
        // looked around.  Keep it conservative for high-frequency pickpocketing.
        double timeMultiplier = Math.min(12.0, sinceLast / (double) minimumGap);
        double chance = 0.006 * timeMultiplier;
        if (random.nextDouble() > chance) {
            nextCameraTurnEligibleAt = now + 750L + random.nextInt(1_750);
            return false;
        }

        performCameraTurn();
        lastCameraTurnAt = System.currentTimeMillis();
        scheduleNextCameraWindow();
        return true;
    }

    private void nudgeMouseOnce() {
        try {
            Point start = Microbot.getMouse().getMousePosition();
            if (start == null) return;

            int dx = clamp((int) Math.round(random.nextGaussian() * 18), -60, 60);
            int dy = clamp((int) Math.round(random.nextGaussian() * 18), -60, 60);
            if (Math.abs(dx) + Math.abs(dy) < 5) return;

            moveHumanized(start.x + dx, start.y + dy);
        } catch (Exception ignored) {
        }
    }

    private void performCameraTurn() {
        try {
            int yawDelta = clamp((int) Math.round(random.nextGaussian() * 18), -65, 65);
            if (Math.abs(yawDelta) < 7) {
                yawDelta = (random.nextBoolean() ? 1 : -1) * (9 + random.nextInt(13));
            }

            int targetYaw = (Rs2Camera.getAngle() + yawDelta + 360) % 360;
            int degreesToMove = Math.abs(Rs2Camera.getAngleTo(targetYaw));
            int pressTime = 70 + (int) (degreesToMove * 2.0);
            pressTime = gaussianClamped(pressTime, Math.max(10, pressTime / 7), 55, 320);

            int direction = Rs2Camera.getAngleTo(targetYaw) > 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
            Rs2Keyboard.keyHold(direction);
            sleepQuietly(pressTime);
            Rs2Keyboard.keyRelease(direction);

            // Occasional small pitch "twist" from MKE. No zoom correction is
            // imported because it adds no value to repetitive elf thieving.
            if (random.nextInt(100) < 36) {
                sleepQuietly(gaussianClamped(105, 35, 45, 190));
                boolean pitchUp = random.nextBoolean();
                int pitchKey = pitchUp ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
                int pitchTime = gaussianClamped(85, 35, 40, 190);
                Rs2Keyboard.keyHold(pitchKey);
                sleepQuietly(pitchTime);
                Rs2Keyboard.keyRelease(pitchKey);
            }
        } catch (Exception ignored) {
        } finally {
            // Defensive release in case a camera read failed after keyHold.
            try { Rs2Keyboard.keyRelease(KeyEvent.VK_LEFT); } catch (Exception ignored) {}
            try { Rs2Keyboard.keyRelease(KeyEvent.VK_RIGHT); } catch (Exception ignored) {}
            try { Rs2Keyboard.keyRelease(KeyEvent.VK_UP); } catch (Exception ignored) {}
            try { Rs2Keyboard.keyRelease(KeyEvent.VK_DOWN); } catch (Exception ignored) {}
        }
    }

    private void scheduleNextCameraWindow() {
        int seconds = Math.max(1, config.cameraMovementFrequency());
        long base = seconds * 1000L;
        nextCameraTurnEligibleAt = System.currentTimeMillis()
                + base
                + (long) (random.nextDouble() * base * 1.5);
    }

    private void moveHumanized(int x, int y) {
        if (x <= 1 || y <= 1) return;
        if (Microbot.naturalMouse != null) Microbot.naturalMouse.moveTo(x, y);
        else Microbot.getMouse().move(x, y);
    }

    private int gaussianClamped(int mean, int deviation, int min, int max) {
        int value = (int) Math.round(mean + random.nextGaussian() * Math.max(1, deviation));
        return clamp(value, min, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void sleepQuietly(int milliseconds) {
        if (milliseconds <= 0) return;
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
