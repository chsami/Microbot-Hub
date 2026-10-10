package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.api.GameState;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * Shared Dro behavior/profile layer intended to be embedded by any Microbot script.
 *
 * <p>It centralizes the pieces that otherwise end up being copied into every Dro script:
 * startup camera zoom/pitch, a stable per-login session personality, MKE-style reaction
 * timing, NaturalMouse helpers, fixed-edge off-screen parking, ACTIVE/BALANCED/AFK mouse
 * activity, native antiban setup with active cooldowns disabled, the DroZone smart break +
 * auto-login flow, and an optional live overlay.
 *
 * <p>This class deliberately does not own task-specific pathing, banking, combat, mining,
 * thieving, target selection, or safety rules. The host script tells it when a break is safe
 * and when the script has entered an idle opportunity.
 */
public final class BaseProfileDro {

    public static final String VERSION = "1.0.0";

    private static final long MIN_MOUSE_NUDGE_GAP_MS = 2_200L;
    private static final int DEFAULT_ATTENTION_PARK_MIN = 8;
    private static final int DEFAULT_ATTENTION_PARK_MAX = 12;

    private final Settings settings;
    private final Random random = new Random();
    private final SmartBreakManager breakManager;

    private SessionPersonality personality = SessionPersonality.neutral();
    private AfkParkSide resolvedParkSide = AfkParkSide.RIGHT;
    private int activeParkChanceThisLogin = 12;

    private boolean started;
    private boolean wasLoggedIn;
    private boolean idleLatched;
    private boolean mouseParked;
    private int offScreenParksSinceAttention;
    private int nextAttentionAfterParks;

    private long sessionStartedAt;
    private long lastMouseNudgeAt;
    private long nextCameraNudgeAt;
    private int actionsUntilRhythmPause;
    private String profileStatus = "Starting";

    private Runnable nativeTemplateApplier;
    private BooleanSupplier attentionAction;

    private OverlayManager overlayManager;
    private ProfileOverlay overlay;

    public BaseProfileDro() {
        this(new Settings());
    }

    public BaseProfileDro(Settings settings) {
        this.settings = settings == null ? new Settings() : settings;
        this.breakManager = new SmartBreakManager(this.settings);
        this.attentionAction = null;
        this.nextAttentionAfterParks = randomBetween(DEFAULT_ATTENTION_PARK_MIN, DEFAULT_ATTENTION_PARK_MAX);
    }

    /**
     * Optional activity-specific Microbot template, for example:
     * <pre>
     * profile.setNativeTemplateApplier(() -> Rs2Antiban.antibanSetupTemplates.applyThievingSetup());
     * </pre>
     * The base then applies its shared MKE/Dro overrides afterward.
     */
    public BaseProfileDro setNativeTemplateApplier(Runnable nativeTemplateApplier) {
        this.nativeTemplateApplier = nativeTemplateApplier;
        return this;
    }

    /**
     * Optional explicit on-screen attention action. No inventory glance is supplied by default.
     */
    public BaseProfileDro setAttentionAction(BooleanSupplier attentionAction) {
        this.attentionAction = attentionAction;
        return this;
    }

    /** Add the built-in live profile overlay to a plugin. */
    public void attachOverlay(Plugin plugin, OverlayManager overlayManager, OverlayDataProvider provider) {
        detachOverlay();
        if (plugin == null || overlayManager == null || !settings.overlayEnabled) return;
        this.overlayManager = overlayManager;
        this.overlay = new ProfileOverlay(plugin, this, provider);
        overlayManager.add(this.overlay);
    }

    public void detachOverlay() {
        if (overlayManager != null && overlay != null) {
            try {
                overlayManager.remove(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
        overlayManager = null;
    }

    /**
     * Initializes the shared profile. It does not start a second scheduler; the host script remains
     * the single owner of its loop and simply calls {@link #tick(boolean, boolean)}.
     */
    public void start() {
        if (started) return;
        started = true;
        sessionStartedAt = System.currentTimeMillis();
        wasLoggedIn = false;
        idleLatched = false;
        mouseParked = false;
        offScreenParksSinceAttention = 0;
        nextAttentionAfterParks = randomBetween(DEFAULT_ATTENTION_PARK_MIN, DEFAULT_ATTENTION_PARK_MAX);
        resetPerLoginPersonality();
        resetAdaptiveCadence();
        configureNativeAntiban();
        breakManager.reset();
        profileStatus = "Active";
    }

    /**
     * Main integration call for most scripts.
     *
     * @param safeToStartBreak true only when the host considers this exact moment safe for a long
     *                         AFK/logout break. A due break stays queued until this becomes true.
     * @param idleOpportunity  true while the task is naturally waiting/animating and it is safe for
     *                         the mouse to leave the client. One decision is made per idle stretch.
     * @return true when the host should pause its normal task work (break/login handling or logged out).
     */
    public boolean tick(boolean safeToStartBreak, boolean idleOpportunity) {
        return tick(safeToStartBreak, idleOpportunity, settings.mouseActivity);
    }

    private java.util.function.Consumer<Settings> breakSettingsUpdater;

    /** Refresh break controls on the script worker; keep humanization and active deadlines intact. */
    public BaseProfileDro setBreakSettingsUpdater(java.util.function.Consumer<Settings> updater) {
        this.breakSettingsUpdater = updater;
        return this;
    }

    public boolean tick(boolean safeToStartBreak, boolean idleOpportunity, MouseActivity activity) {
        if (breakSettingsUpdater != null) breakSettingsUpdater.accept(settings);
        if (!started) start();

        final boolean loggedIn = Microbot.isLoggedIn();
        handleLoginTransition(loggedIn);

        // Keep active/native cooldowns hard-disabled even if another template toggles them later.
        if (settings.nativeAntibanEnabled) {
            Rs2AntibanSettings.actionCooldownChance = 0.0;
            Rs2AntibanSettings.actionCooldownActive = false;
        }

        if (breakManager.update(safeToStartBreak, this::forceParkCompletelyOffScreen)) {
            profileStatus = breakManager.getStatus();
            if (settings.writeBreakStatusToMicrobot) Microbot.status = profileStatus;
            idleLatched = false;
            return true;
        }

        if (!loggedIn) {
            idleLatched = false;
            profileStatus = "Logged out";
            return true;
        }

        handleIdleMouse(idleOpportunity, activity == null ? settings.mouseActivity : activity);
        if (!idleOpportunity && !breakManager.isBreakActive()) {
            profileStatus = "Active";
        }
        return false;
    }

    public void shutdown() {
        detachOverlay();
        breakManager.shutdown();
        started = false;
        wasLoggedIn = false;
        idleLatched = false;
        mouseParked = false;
        try {
            Rs2Antiban.deactivateAntiban();
            Rs2Antiban.resetAntibanSettings();
        } catch (Exception ignored) {
        }
    }

    /** Mark the beginning of a real task input. */
    public void beforeAction(ActionPhase phase, boolean urgent) {
        if (!started) start();
        idleLatched = false;
        mouseParked = false;

        int base = HumanBehaviorProfile.baseDelayMs(phase, urgent);
        int spread = Math.max(10, base / 4);
        int max = phase == ActionPhase.ACTIVE_WORK ? 520 : 360;
        int delay = gaussianClamped(base, spread, urgent ? 20 : 30, max);
        sleepQuietly(personality.reaction(delay));

        int hesitationChance;
        switch (phase == null ? ActionPhase.MAINTENANCE : phase) {
            case ACTIVE_WORK:
                hesitationChance = personality.hesitationChance(11, 6, 17);
                break;
            case SETUP:
                hesitationChance = personality.hesitationChance(7, 3, 12);
                break;
            case MAINTENANCE:
            default:
                hesitationChance = personality.hesitationChance(3, 1, 6);
                break;
        }

        if (!urgent && personality.roll(hesitationChance)) {
            int pause = phase == ActionPhase.ACTIVE_WORK
                    ? gaussianClamped(310, 150, 120, 850)
                    : gaussianClamped(170, 80, 70, 430);
            sleepQuietly(personality.reaction(pause));
        }
    }

    /**
     * Small local settling and MKE-style cadence after a real successful action. This is deliberately
     * not Rs2Antiban.actionCooldown(); native active cooldowns remain disabled.
     */
    public void afterAction(ActionPhase phase) {
        ActionPhase actual = phase == null ? ActionPhase.MAINTENANCE : phase;
        int mean;
        int max;
        switch (actual) {
            case ACTIVE_WORK:
                mean = 105;
                max = 290;
                break;
            case SETUP:
                mean = 75;
                max = 220;
                break;
            case MAINTENANCE:
            default:
                mean = 45;
                max = 145;
                break;
        }

        int delay = gaussianClamped(mean, Math.max(12, mean / 3), 20, max);
        sleepQuietly(personality.reaction(delay));

        if (actual == ActionPhase.ACTIVE_WORK) {
            maybeRhythmPause();
            maybeSingleMouseNudge();
            maybeSubtleCameraNudge();
        }
    }

    /** Convenience wrapper around one real Boolean-returning action. */
    public boolean performAction(ActionPhase phase, boolean urgent, BooleanSupplier action) {
        if (action == null) return false;
        beforeAction(phase, urgent);
        boolean success;
        try {
            success = action.getAsBoolean();
        } catch (RuntimeException ex) {
            throw ex;
        }
        if (success) afterAction(phase);
        return success;
    }

    /** Convenience wrapper around a void action. */
    public void performAction(ActionPhase phase, boolean urgent, Runnable action) {
        if (action == null) return;
        beforeAction(phase, urgent);
        action.run();
        afterAction(phase);
    }

    /**
     * Picks a non-fixed point inside a known-safe clickbox. This is the reusable "different places"
     * part of the MKE profile; it does not invent unrelated clicks.
     */
    public net.runelite.api.Point randomPoint(Rectangle bounds) {
        return randomPoint(bounds, 3);
    }

    public net.runelite.api.Point randomPoint(Rectangle bounds, int inset) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;
        int safeInset = Math.max(0, Math.min(inset, Math.min(bounds.width, bounds.height) / 3));
        int minX = bounds.x + safeInset;
        int maxX = bounds.x + bounds.width - 1 - safeInset;
        int minY = bounds.y + safeInset;
        int maxY = bounds.y + bounds.height - 1 - safeInset;
        if (maxX < minX) maxX = minX;
        if (maxY < minY) maxY = minY;

        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double sx = Math.max(1.0, (maxX - minX + 1) / 5.0);
        double sy = Math.max(1.0, (maxY - minY + 1) / 5.0);

        int x = clamp((int) Math.round(cx + random.nextGaussian() * sx), minX, maxX);
        int y = clamp((int) Math.round(cy + random.nextGaussian() * sy), minY, maxY);
        return new net.runelite.api.Point(x, y);
    }

    public boolean moveToHumanized(Rectangle bounds) {
        net.runelite.api.Point point = randomPoint(bounds);
        if (point == null) return false;
        moveCursorHumanized(point.getX(), point.getY());
        return true;
    }

    /** Only use this for a clickbox the host already knows is safe to click. */
    public boolean clickHumanized(Rectangle bounds) {
        net.runelite.api.Point point = randomPoint(bounds);
        if (point == null) return false;
        moveCursorHumanized(point.getX(), point.getY());
        sleepQuietly(personality.reaction(gaussianClamped(58, 24, 20, 145)));
        Microbot.getMouse().click(point);
        return true;
    }

    /** Force an immediate park using the selected fixed edge. */
    public boolean forceParkCompletelyOffScreen() {
        if (settings.parkSide == AfkParkSide.NONE) return false;
        return parkCompletelyOffScreen(resolvedParkSide);
    }

    /** Explicit trip AFK only; does not enable automatic parking or alter the configured edge. */
    public boolean parkOffScreenForTrip() {
        return parkCompletelyOffScreen(randomBetween(0, 1) == 0 ? AfkParkSide.LEFT : AfkParkSide.RIGHT);
    }

    private boolean parkCompletelyOffScreen(AfkParkSide side) {
        if (!Microbot.isLoggedIn() || Microbot.getClient() == null) return false;
        if (side == null || !side.parksOffScreen()) return false;
        int[] canvas = Microbot.getClientThread().runOnClientThreadOptional(() -> new int[] {
                Math.max(1, Microbot.getClient().getCanvasWidth()),
                Math.max(1, Microbot.getClient().getCanvasHeight())}).orElse(null);
        if (canvas == null) return false;
        int width = canvas[0], height = canvas[1];

        // Deliberately overshoot the client by a meaningful distance. This does not merely touch
        // the edge: the final cursor coordinate is fully outside the RuneLite canvas.
        int minDepth = Math.max(40, settings.offScreenDepthMinPx);
        int maxDepth = Math.max(minDepth, settings.offScreenDepthMaxPx);
        int depth = randomBetween(minDepth, maxDepth);
        int edgePadding = Math.max(8, settings.offScreenAlongEdgePaddingPx);

        int x;
        int y;
        if (side.isHorizontal()) {
            x = side.dx < 0 ? -depth : width + depth;
            int low = Math.min(edgePadding, Math.max(0, height - 1));
            int high = Math.max(low, height - 1 - edgePadding);
            y = randomBetween(low, high);
        } else {
            y = side.dy < 0 ? -depth : height + depth;
            int low = Math.min(edgePadding, Math.max(0, width - 1));
            int high = Math.max(low, width - 1 - edgePadding);
            x = randomBetween(low, high);
        }

        try {
            if (Microbot.naturalMouse != null) {
                Microbot.naturalMouse.moveTo(x, y);
            } else {
                Microbot.getMouse().move(x, y);
            }

            // Verify that the resulting local canvas coordinate is actually outside. If NaturalMouse
            // was clipped by the platform, issue the same destination through the direct mouse path.
            Point actual = Microbot.getMouse().getMousePosition();
            if (isInsideCanvas(actual, width, height)) {
                Microbot.getMouse().move(x, y);
                actual = Microbot.getMouse().getMousePosition();
            }

            // Last-resort deeper overshoot on the SAME selected edge. Never randomize another edge.
            if (isInsideCanvas(actual, width, height)) {
                int deeper = maxDepth + 120;
                if (side.isHorizontal()) x = side.dx < 0 ? -deeper : width + deeper;
                else y = side.dy < 0 ? -deeper : height + deeper;
                Microbot.getMouse().move(x, y);
            }
            Point finalPosition = Microbot.getMouse().getMousePosition();
            if (finalPosition == null || isInsideCanvas(finalPosition, width, height)) return false;

            mouseParked = true;
            offScreenParksSinceAttention++;
            profileStatus = "Parked " + side;
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Re-roll the per-login personality and RANDOM edge. */
    private void resetPerLoginPersonality() {
        personality = SessionPersonality.roll();
        resolvedParkSide = settings.parkSide == AfkParkSide.RANDOM
                ? AfkParkSide.randomEdge()
                : settings.parkSide;
        activeParkChanceThisLogin = randomBetween(
                Math.min(settings.activeParkChanceMin, settings.activeParkChanceMax),
                Math.max(settings.activeParkChanceMin, settings.activeParkChanceMax));
        offScreenParksSinceAttention = 0;
        nextAttentionAfterParks = randomBetween(DEFAULT_ATTENTION_PARK_MIN, DEFAULT_ATTENTION_PARK_MAX);
        mouseParked = false;
        idleLatched = false;
    }

    private void handleLoginTransition(boolean loggedIn) {
        if (loggedIn && !wasLoggedIn) {
            resetPerLoginPersonality();
            resetAdaptiveCadence();
            if (!settings.cameraNudgesEnabled) {
                wasLoggedIn = true;
                return;
            }
            try {
                Rs2Camera.setZoom(settings.startupCameraZoom);
                int pitchMin = Math.min(settings.startupPitchMin, settings.startupPitchMaxExclusive - 1);
                int pitchMax = Math.max(pitchMin + 1, settings.startupPitchMaxExclusive);
                Rs2Camera.setPitch(ThreadLocalRandom.current().nextInt(pitchMin, pitchMax));
            } catch (Exception ex) {
                Microbot.log("[BaseProfileDro] Startup camera settings could not be applied: " + ex.getMessage());
            }
        }
        wasLoggedIn = loggedIn;
    }

    private void handleIdleMouse(boolean idleOpportunity, MouseActivity mode) {
        if (!idleOpportunity) {
            idleLatched = false;
            mouseParked = false;
            return;
        }
        if (idleLatched) return;
        idleLatched = true;

        if (settings.parkSide == AfkParkSide.NONE || !resolvedParkSide.parksOffScreen()) {
            maybeOnScreenAttention(false);
            return;
        }

        // Roughly one on-screen attention/glance for every 8-12 actual off-screen parks.
        if (offScreenParksSinceAttention >= nextAttentionAfterParks && maybeOnScreenAttention(true)) {
            offScreenParksSinceAttention = 0;
            nextAttentionAfterParks = randomBetween(DEFAULT_ATTENTION_PARK_MIN, DEFAULT_ATTENTION_PARK_MAX);
            mouseParked = false;
            return;
        }

        int chance = mode.offScreenChance(this);
        if (randomBetween(0, 99) < chance) {
            forceParkCompletelyOffScreen();
        } else {
            // A non-park decision normally means simply leaving the cursor alone. A small fraction
            // of those on-screen bouts may use the profile's safe attention action.
            if (personality.roll(mode.onScreenAttentionChance(personality))) {
                maybeOnScreenAttention(false);
            }
        }
    }

    private boolean maybeOnScreenAttention(boolean dueFromParkCount) {
        if (attentionAction == null) return false;
        try {
            boolean acted = attentionAction.getAsBoolean();
            if (acted) profileStatus = dueFromParkCount ? "On-screen check" : "Watching screen";
            return acted;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void configureNativeAntiban() {
        if (!settings.nativeAntibanEnabled) return;
        try {
            Rs2Antiban.resetAntibanSettings();
            if (nativeTemplateApplier != null) nativeTemplateApplier.run();

            Rs2Antiban.setActivity(settings.activity);
            Rs2Antiban.setActivityIntensity(settings.activityIntensity);
            Rs2Antiban.setPlayStyle(settings.playStyle);
            Rs2Antiban.activateAntiban();

            // Complete adaptive MKE/Dro profile. Long breaks and deliberate off-screen parking are
            // owned locally so they respect the host's safe point and the selected fixed edge.
            Rs2AntibanSettings.antibanEnabled = true;
            Rs2AntibanSettings.usePlayStyle = true;
            Rs2AntibanSettings.randomIntervals = false;
            Rs2AntibanSettings.simulateFatigue = true;
            Rs2AntibanSettings.simulateAttentionSpan = true;
            Rs2AntibanSettings.behavioralVariability = true;
            Rs2AntibanSettings.nonLinearIntervals = true;
            Rs2AntibanSettings.profileSwitching = false;
            Rs2AntibanSettings.contextualVariability = true;
            Rs2AntibanSettings.timeOfDayAdjust = true;
            Rs2AntibanSettings.dynamicIntensity = false;
            Rs2AntibanSettings.dynamicActivity = false;
            Rs2AntibanSettings.universalAntiban = false;
            Rs2AntibanSettings.simulateMistakes = true;
            Rs2AntibanSettings.naturalMouse = true;

            // Zulrah owns the cursor: no idle movement or off-screen exits.
            Rs2AntibanSettings.moveMouseRandomly = false;
            Rs2AntibanSettings.moveMouseRandomlyChance = 0.0;
            Rs2AntibanSettings.moveMouseOffScreen = false;
            Rs2AntibanSettings.moveMouseOffScreenChance = 0.0;

            // DroZone-style smart breaks own breaks. Do not stack native microbreaks on top.
            Rs2AntibanSettings.takeMicroBreaks = false;
            Rs2AntibanSettings.microBreakChance = 0.0;

            // Explicit project rule: no native active/action cooldowns.
            Rs2AntibanSettings.actionCooldownChance = 0.0;
            Rs2AntibanSettings.actionCooldownActive = false;
        } catch (Exception ex) {
            Microbot.log("[BaseProfileDro] Native antiban setup skipped: " + ex.getMessage());
        }
    }

    private void resetAdaptiveCadence() {
        lastMouseNudgeAt = 0L;
        actionsUntilRhythmPause = 2 + random.nextInt(5);
        scheduleNextCameraNudge();
    }

    private void maybeRhythmPause() {
        if (--actionsUntilRhythmPause > 0) return;
        actionsUntilRhythmPause = 2 + random.nextInt(5);
        int chance = personality.hesitationChance(62, 45, 78);
        if (!personality.roll(chance)) return;

        int pause = gaussianClamped(235, 120, 75, 680);
        if (random.nextInt(100) < 11) pause += gaussianClamped(310, 150, 90, 760);
        sleepQuietly(personality.reaction(pause));
    }

    private void maybeSingleMouseNudge() {
        long now = System.currentTimeMillis();
        if (now - lastMouseNudgeAt < MIN_MOUSE_NUDGE_GAP_MS) return;
        if (!personality.roll(personality.mouseNudgeChance())) return;

        try {
            Point start = Microbot.getMouse().getMousePosition();
            if (start == null || Microbot.getClient() == null) return;
            int width = Math.max(2, Microbot.getClient().getCanvasWidth());
            int height = Math.max(2, Microbot.getClient().getCanvasHeight());
            if (!isInsideCanvas(start, width, height)) return;

            int dx = clamp((int) Math.round(random.nextGaussian() * 14), -42, 42);
            int dy = clamp((int) Math.round(random.nextGaussian() * 14), -42, 42);
            if (Math.abs(dx) + Math.abs(dy) < 5) return;

            int targetX = clamp(start.x + dx, 2, width - 2);
            int targetY = clamp(start.y + dy, 2, height - 2);
            moveCursorHumanized(targetX, targetY);
            lastMouseNudgeAt = System.currentTimeMillis();
        } catch (Exception ignored) {
        }
    }

    private void maybeSubtleCameraNudge() {
        if (!settings.cameraNudgesEnabled) return;
        long now = System.currentTimeMillis();
        if (now < nextCameraNudgeAt) return;
        scheduleNextCameraNudge();
        if (!personality.roll(personality.cameraNudgeChance())) return;

        int direction = random.nextBoolean() ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
        try {
            Rs2Keyboard.keyHold(direction);
            sleepQuietly(gaussianClamped(85, 25, 45, 145));
            Rs2Keyboard.keyRelease(direction);
        } catch (Exception ignored) {
        } finally {
            try { Rs2Keyboard.keyRelease(KeyEvent.VK_LEFT); } catch (Exception ignored) { }
            try { Rs2Keyboard.keyRelease(KeyEvent.VK_RIGHT); } catch (Exception ignored) { }
        }
    }

    private void scheduleNextCameraNudge() {
        nextCameraNudgeAt = System.currentTimeMillis()
                + gaussianClamped(11_000, 4_000, 7_000, 22_000);
    }

    private void moveCursorHumanized(int x, int y) {
        if (Microbot.naturalMouse != null) Microbot.naturalMouse.moveTo(x, y);
        else Microbot.getMouse().move(x, y);
    }

    private static boolean isInsideCanvas(Point point, int width, int height) {
        return point != null
                && point.x >= 0 && point.x < width
                && point.y >= 0 && point.y < height;
    }

    private int gaussianClamped(int mean, int deviation, int min, int max) {
        int value = (int) Math.round(mean + random.nextGaussian() * Math.max(1, deviation));
        return clamp(value, min, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int randomBetween(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static void sleepQuietly(long milliseconds) {
        if (milliseconds <= 0L) return;
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public Settings getSettings() {
        return settings;
    }

    public SessionPersonality getPersonality() {
        return personality;
    }

    public AfkParkSide getResolvedParkSide() {
        return resolvedParkSide;
    }

    public int getActiveParkChanceThisLogin() {
        return activeParkChanceThisLogin;
    }

    public boolean isMouseParked() {
        return mouseParked;
    }

    public boolean isBreakActive() {
        return breakManager.isBreakActive();
    }

    public String getBreakDisplay() {
        return breakManager.getTimeUntilNextBreak();
    }

    public String getProfileStatus() {
        return profileStatus;
    }

    public long getSessionElapsedMs() {
        return sessionStartedAt <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - sessionStartedAt);
    }

    public enum ActionPhase {
        ACTIVE_WORK,
        SETUP,
        MAINTENANCE
    }

    /**
     * Default parking rates requested for the shared profile:
     * AFK ~= 75%, BALANCED ~= 40%, ACTIVE = one stable 10-15% roll per login.
     */
    public enum MouseActivity {
        AFK,
        BALANCED,
        ACTIVE;

        public int offScreenChance(BaseProfileDro profile) {
            if (profile == null) return 0;
            switch (this) {
                case AFK:
                    return clamp(profile.settings.afkParkChance, 0, 100);
                case BALANCED:
                    return clamp(profile.settings.balancedParkChance, 0, 100);
                case ACTIVE:
                default:
                    return clamp(profile.activeParkChanceThisLogin, 0, 100);
            }
        }

        int onScreenAttentionChance(SessionPersonality personality) {
            int base;
            switch (this) {
                case AFK:
                    base = 2;
                    break;
                case BALANCED:
                    base = 7;
                    break;
                case ACTIVE:
                default:
                    base = 12;
                    break;
            }
            return personality.scaleCheckChance(base, 1, 18);
        }

        @Override
        public String toString() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Which edge the mouse leaves through. RANDOM is resolved once on each fresh login and kept
     * stable until the next login. NONE keeps the cursor inside the client.
     */
    public enum AfkParkSide {
        NONE(0, 0),
        LEFT(-1, 0),
        RIGHT(1, 0),
        TOP(0, -1),
        BOTTOM(0, 1),
        RANDOM(0, 0);

        private final int dx;
        private final int dy;
        private static final AfkParkSide[] REAL_EDGES = {LEFT, RIGHT, TOP, BOTTOM};

        AfkParkSide(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        public int getDx() {
            return dx;
        }

        public int getDy() {
            return dy;
        }

        public boolean parksOffScreen() {
            return this != NONE;
        }

        public boolean isHorizontal() {
            return dx != 0;
        }

        public static AfkParkSide randomEdge() {
            return REAL_EDGES[Rs2Random.between(0, REAL_EDGES.length)];
        }

        @Override
        public String toString() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }
    }

    /** Stable behavioral traits rolled once per fresh login. */
    public static final class SessionPersonality {
        private final double speed;
        private final double mouseConfidence;
        private final double afkTendency;
        private final double hesitation;
        private final double checkHabit;

        private SessionPersonality(double speed, double mouseConfidence, double afkTendency,
                                   double hesitation, double checkHabit) {
            this.speed = speed;
            this.mouseConfidence = mouseConfidence;
            this.afkTendency = afkTendency;
            this.hesitation = hesitation;
            this.checkHabit = checkHabit;
        }

        public static SessionPersonality roll() {
            Random random = new Random(System.nanoTime());
            return new SessionPersonality(
                    0.84 + random.nextDouble() * 0.34,
                    triangular(random),
                    triangular(random),
                    triangular(random),
                    triangular(random));
        }

        public static SessionPersonality neutral() {
            return new SessionPersonality(1.0, 0.5, 0.5, 0.5, 0.5);
        }

        private static double triangular(Random random) {
            return (random.nextDouble() + random.nextDouble()) / 2.0;
        }

        public long reaction(long baseMs) {
            return Math.max(0L, Math.round(baseMs * speed));
        }

        public int hesitationChance(int base, int min, int max) {
            int value = (int) Math.round(base * (0.65 + hesitation * 0.7));
            return clamp(value, min, max);
        }

        public int mouseNudgeChance() {
            int value = (int) Math.round(6 * (0.70 + (1.0 - mouseConfidence) * 0.70));
            return clamp(value, 4, 10);
        }

        public int cameraNudgeChance() {
            int value = (int) Math.round(6 * (0.75 + hesitation * 0.50));
            return clamp(value, 3, 9);
        }

        public int scaleCheckChance(int base, int min, int max) {
            int value = (int) Math.round(base * (0.6 + 0.8 * checkHabit));
            return clamp(value, min, max);
        }

        public boolean roll(int chance) {
            return Rs2Random.between(0, 100) < clamp(chance, 0, 100);
        }

        public double getSpeed() {
            return speed;
        }

        public double getMouseConfidence() {
            return mouseConfidence;
        }

        public double getAfkTendency() {
            return afkTendency;
        }

        public double getHesitation() {
            return hesitation;
        }

        public double getCheckHabit() {
            return checkHabit;
        }
    }

    /** Generic timing helpers retained from the Dro/MKE behavior classes. */
    public static final class HumanBehaviorProfile {
        private HumanBehaviorProfile() {
        }

        public static int pauseDelayMs(boolean humanLike, int minMs, int maxMs, boolean urgent) {
            if (!humanLike) return 0;
            int quickChance = urgent ? 65 : 32;
            if (Rs2Random.between(0, 100) < quickChance) {
                int qMin = Math.max(30, minMs / 2);
                int qMax = Math.min(220, (minMs + maxMs) / 2);
                if (qMax < qMin) qMax = qMin + 30;
                return Rs2Random.between(qMin, qMax);
            }
            return Rs2Random.between(minMs, maxMs);
        }

        static int baseDelayMs(ActionPhase phase, boolean urgent) {
            if (urgent) return Rs2Random.between(35, 110);
            switch (phase == null ? ActionPhase.MAINTENANCE : phase) {
                case ACTIVE_WORK:
                    return Rs2Random.between(110, 330);
                case SETUP:
                    return Rs2Random.between(70, 230);
                case MAINTENANCE:
                default:
                    return Rs2Random.between(35, 135);
            }
        }
    }

    /**
     * Shared defaults. These mirror the currently working DroZone break/login values and the
     * requested shared mouse defaults. Scripts can change only the values that make sense for them.
     */
    public static final class Settings {
        private boolean nativeAntibanEnabled = true;
        private Activity activity = Activity.GENERAL_COMBAT;
        private ActivityIntensity activityIntensity = ActivityIntensity.MODERATE;
        private PlayStyle playStyle = PlayStyle.MODERATE;
        private double nativeRandomMouseChance = 0.36;

        private int startupCameraZoom = 100;
        private int startupPitchMin = 2200;
        private int startupPitchMaxExclusive = 2850;

        private MouseActivity mouseActivity = MouseActivity.BALANCED;
        private AfkParkSide parkSide = AfkParkSide.RANDOM;
        private int afkParkChance = 75;
        private int balancedParkChance = 40;
        private int activeParkChanceMin = 10;
        private int activeParkChanceMax = 15;
        private int offScreenDepthMinPx = 80;
        private int offScreenDepthMaxPx = 180;
        private int offScreenAlongEdgePaddingPx = 24;

        private boolean customBreaksEnabled = true;
        private int minBreakIntervalMinutes = 20;
        private int maxBreakIntervalMinutes = 140;
        private int logoutBreakChance = 100;
        private int afkBreakMinMinutes = 2;
        private int afkBreakMaxMinutes = 6;
        private int logoutBreakMinMinutes = 5;
        private int logoutBreakMaxMinutes = 40;
        private int postLoginSettleSeconds = 20;

        private boolean overlayEnabled = true;
        private boolean writeBreakStatusToMicrobot = true;
        private boolean cameraNudgesEnabled = true;

        public Settings nativeAntibanEnabled(boolean value) {
            this.nativeAntibanEnabled = value;
            return this;
        }

        public Settings activity(Activity value) {
            if (value != null) this.activity = value;
            return this;
        }

        public Settings activityIntensity(ActivityIntensity value) {
            if (value != null) this.activityIntensity = value;
            return this;
        }

        public Settings playStyle(PlayStyle value) {
            if (value != null) this.playStyle = value;
            return this;
        }

        public Settings nativeRandomMouseChance(double value) {
            this.nativeRandomMouseChance = Math.max(0.0, Math.min(1.0, value));
            return this;
        }

        public Settings startupCamera(int zoom, int minPitch, int maxPitchExclusive) {
            this.startupCameraZoom = zoom;
            this.startupPitchMin = minPitch;
            this.startupPitchMaxExclusive = Math.max(minPitch + 1, maxPitchExclusive);
            return this;
        }

        public Settings mouseActivity(MouseActivity value) {
            if (value != null) this.mouseActivity = value;
            return this;
        }

        public Settings parkSide(AfkParkSide value) {
            if (value != null) this.parkSide = value;
            return this;
        }

        public Settings parkChances(int afkPercent, int balancedPercent, int activeMinPercent, int activeMaxPercent) {
            this.afkParkChance = clamp(afkPercent, 0, 100);
            this.balancedParkChance = clamp(balancedPercent, 0, 100);
            this.activeParkChanceMin = clamp(activeMinPercent, 0, 100);
            this.activeParkChanceMax = clamp(activeMaxPercent, 0, 100);
            return this;
        }

        public Settings offScreenDepth(int minPixels, int maxPixels) {
            this.offScreenDepthMinPx = Math.max(40, minPixels);
            this.offScreenDepthMaxPx = Math.max(this.offScreenDepthMinPx, maxPixels);
            return this;
        }

        public Settings customBreaksEnabled(boolean value) {
            this.customBreaksEnabled = value;
            return this;
        }

        public Settings breakIntervals(int minMinutes, int maxMinutes) {
            this.minBreakIntervalMinutes = Math.max(1, minMinutes);
            this.maxBreakIntervalMinutes = Math.max(this.minBreakIntervalMinutes, maxMinutes);
            return this;
        }

        public Settings logoutBreakChance(int percent) {
            this.logoutBreakChance = clamp(percent, 0, 100);
            return this;
        }

        public Settings afkBreakDuration(int minMinutes, int maxMinutes) {
            this.afkBreakMinMinutes = Math.max(1, minMinutes);
            this.afkBreakMaxMinutes = Math.max(this.afkBreakMinMinutes, maxMinutes);
            return this;
        }

        public Settings logoutBreakDuration(int minMinutes, int maxMinutes) {
            this.logoutBreakMinMinutes = Math.max(1, minMinutes);
            this.logoutBreakMaxMinutes = Math.max(this.logoutBreakMinMinutes, maxMinutes);
            return this;
        }

        public Settings postLoginSettleSeconds(int seconds) {
            this.postLoginSettleSeconds = Math.max(0, seconds);
            return this;
        }

        public Settings overlayEnabled(boolean value) {
            this.overlayEnabled = value;
            return this;
        }

        public Settings writeBreakStatusToMicrobot(boolean value) {
            this.writeBreakStatusToMicrobot = value;
            return this;
        }

        /** Allow a host script to keep BaseProfileDro fully active while owning camera policy itself. */
        public Settings cameraNudgesEnabled(boolean value) {
            this.cameraNudgesEnabled = value;
            return this;
        }

        public MouseActivity getMouseActivity() {
            return mouseActivity;
        }

        public AfkParkSide getParkSide() {
            return parkSide;
        }
    }

    /** Extra rows/status supplied by a host plugin to the reusable overlay. */
    public interface OverlayDataProvider {
        default String title() {
            return "DRO PROFILE";
        }

        default String status() {
            return Microbot.status;
        }

        default List<OverlayRow> rows() {
            return Collections.emptyList();
        }
    }

    public static final class OverlayRow {
        private final String label;
        private final String value;

        public OverlayRow(String label, String value) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }
    }

    /** Compact DroZone-style live overlay that can be attached to any host plugin. */
    private static final class ProfileOverlay extends Overlay {
        private static final int WIDTH = 238;
        private static final int PADDING = 10;
        private static final int ROW_HEIGHT = 17;
        private static final Color BACKGROUND = new Color(13, 10, 25, 242);
        private static final Color BORDER = new Color(112, 82, 205, 190);
        private static final Color PURPLE = new Color(185, 153, 255);
        private static final Color TEXT = new Color(246, 242, 252);
        private static final Color MUTED = new Color(178, 168, 198);
        private static final Color LIVE = new Color(92, 220, 137);
        private static final Color SHADOW = new Color(0, 0, 0, 190);
        private static final Font TITLE_FONT = FontManager.getRunescapeBoldFont();
        private static final Font LABEL_FONT = FontManager.getRunescapeSmallFont();
        private static final Font VALUE_FONT = FontManager.getRunescapeBoldFont();

        private final BaseProfileDro profile;
        private final OverlayDataProvider provider;

        private ProfileOverlay(Plugin plugin, BaseProfileDro profile, OverlayDataProvider provider) {
            super(plugin);
            this.profile = profile;
            this.provider = provider;
            setPosition(OverlayPosition.BOTTOM_LEFT);
            setLayer(OverlayLayer.ABOVE_WIDGETS);
            setPriority(OverlayPriority.HIGH);
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            List<OverlayRow> rows = new ArrayList<>();
            rows.add(new OverlayRow("Runtime", formatDuration(profile.getSessionElapsedMs())));
            rows.add(new OverlayRow("Next break", profile.getBreakDisplay()));
            rows.add(new OverlayRow("Mouse", profile.settings.mouseActivity.toString()));
            rows.add(new OverlayRow("Park side", profile.getResolvedParkSide().toString()));
            if (provider != null && provider.rows() != null) rows.addAll(provider.rows());

            int height = 52 + rows.size() * ROW_HEIGHT + 8;
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(BACKGROUND);
            graphics.fillRoundRect(0, 0, WIDTH, height, 10, 10);
            graphics.setStroke(new BasicStroke(1.2f));
            graphics.setColor(BORDER);
            graphics.drawRoundRect(0, 0, WIDTH - 1, height - 1, 10, 10);

            String title = provider == null ? "DRO PROFILE" : safe(provider.title(), "DRO PROFILE");
            String status = provider == null ? profile.getProfileStatus() : safe(provider.status(), profile.getProfileStatus());
            drawText(graphics, TITLE_FONT, PURPLE, title, PADDING, 18);
            graphics.setColor(LIVE);
            graphics.fillOval(PADDING, 28, 7, 7);
            drawText(graphics, LABEL_FONT, MUTED, fitText(graphics, status, PADDING + 12), PADDING + 12, 35);
            graphics.setColor(new Color(112, 82, 205, 75));
            graphics.drawLine(PADDING, 43, WIDTH - PADDING, 43);

            int y = 48;
            for (OverlayRow row : rows) {
                renderRow(graphics, row.getLabel(), row.getValue(), y);
                y += ROW_HEIGHT;
            }
            return new Dimension(WIDTH, height);
        }

        private void renderRow(Graphics2D graphics, String label, String value, int y) {
            drawText(graphics, LABEL_FONT, MUTED, label, PADDING, y + 12);
            FontMetrics metrics = graphics.getFontMetrics(VALUE_FONT);
            String fitted = fitValue(graphics, value, WIDTH / 2);
            drawText(graphics, VALUE_FONT, TEXT, fitted,
                    WIDTH - PADDING - metrics.stringWidth(fitted), y + 12);
        }

        private String fitValue(Graphics2D graphics, String text, int maxWidth) {
            String value = safe(text, "");
            FontMetrics metrics = graphics.getFontMetrics(VALUE_FONT);
            while (value.length() > 3 && metrics.stringWidth(value) > maxWidth) {
                value = value.substring(0, value.length() - 4) + "...";
            }
            return value;
        }

        private String fitText(Graphics2D graphics, String text, int x) {
            String value = safe(text, "Starting...");
            FontMetrics metrics = graphics.getFontMetrics(LABEL_FONT);
            int maxWidth = WIDTH - PADDING - x;
            while (value.length() > 3 && metrics.stringWidth(value) > maxWidth) {
                value = value.substring(0, value.length() - 4) + "...";
            }
            return value;
        }

        private static void drawText(Graphics2D graphics, Font font, Color color,
                                     String text, int x, int y) {
            graphics.setFont(font);
            graphics.setColor(SHADOW);
            graphics.drawString(text, x + 1, y + 1);
            graphics.setColor(color);
            graphics.drawString(text, x, y);
        }

        private static String safe(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    /** DroZone-identical smart break and logout-return logic, made host-agnostic. */
    private static final class SmartBreakManager {
        private final Settings settings;

        private boolean previouslyEnabled;
        private int activePostLoginSettleSeconds;
        private boolean breakActive;
        private boolean afkBreakActive;
        private boolean logoutBreakActive;
        private boolean loginPending;

        private int breakTimeRemaining;
        private int nextBreakIn;
        private long nextBreakCheck;
        private long nextLoginAttemptAt;
        private long settleAfterLoginUntil;
        private int loginRetryCount;
        private String status = "";

        private SmartBreakManager(Settings settings) {
            this.settings = settings;
            initializeBreakTimer();
        }

        private void reset() {
            breakActive = false;
            afkBreakActive = false;
            logoutBreakActive = false;
            loginPending = false;
            breakTimeRemaining = 0;
            nextBreakCheck = 0L;
            nextLoginAttemptAt = 0L;
            settleAfterLoginUntil = 0L;
            loginRetryCount = 0;
            status = "";
            initializeBreakTimer();
        }

        private boolean update(boolean safeToStartBreak, Runnable parkMouse) {
            long now = System.currentTimeMillis();
            if (settings.customBreaksEnabled && !previouslyEnabled && !breakActive) initializeBreakTimer();
            previouslyEnabled = settings.customBreaksEnabled;

            // Turning off cancels queued breaks. An active break finishes its original
            // AFK/logout and return cycle, so disabling never grants an early login.
            if (!settings.customBreaksEnabled && !breakActive) {
                nextBreakIn = 0;
                status = "Breaks off";
                return false;
            }

            if (breakActive && logoutBreakActive && loginPending) {
                return updateLogoutReturn(now);
            }

            if (now < nextBreakCheck) {
                return breakActive;
            }
            nextBreakCheck = now + 1_000L;

            if (!breakActive && nextBreakIn > 0) nextBreakIn--;
            if (breakActive && breakTimeRemaining > 0) breakTimeRemaining--;

            if (!breakActive && nextBreakIn <= 0) {
                if (safeToStartBreak) {
                    startRandomBreak(parkMouse);
                    return true;
                }
                status = "Break queued for safe point";
                return false;
            }

            if (breakActive) {
                if (breakTimeRemaining > 0) {
                    status = (logoutBreakActive ? "Logout break " : "AFK break ")
                            + formatSeconds(breakTimeRemaining);
                    return true;
                }

                if (logoutBreakActive) {
                    loginPending = true;
                    nextLoginAttemptAt = now;
                    status = "Break over - auto login";
                    return updateLogoutReturn(now);
                }

                endAfkBreak();
                scheduleNextBreak();
                return false;
            }

            return false;
        }

        private boolean isBreakActive() {
            return breakActive;
        }

        private String getStatus() {
            return status == null || status.isBlank() ? "Break" : status;
        }

        private String getTimeUntilNextBreak() {
            if (breakActive) return getStatus();
            if (!settings.customBreaksEnabled) return "Off";
            if (nextBreakIn <= 0) return "Queued";
            return formatSeconds(nextBreakIn);
        }

        private void shutdown() {
            breakActive = false;
            afkBreakActive = false;
            logoutBreakActive = false;
            loginPending = false;
            loginRetryCount = 0;
            breakTimeRemaining = 0;
            nextBreakIn = 0;
        }

        private void initializeBreakTimer() {
            previouslyEnabled = settings.customBreaksEnabled;
            if (!settings.customBreaksEnabled) {
                nextBreakIn = 0;
                return;
            }

            int min = Math.max(1, Math.min(settings.minBreakIntervalMinutes, settings.maxBreakIntervalMinutes));
            int max = Math.max(min, Math.max(settings.minBreakIntervalMinutes, settings.maxBreakIntervalMinutes));
            nextBreakIn = randomBetween(min * 60, max * 60);
            nextBreakCheck = 0L;
            Microbot.log("[BaseProfileDro] Next smart break scheduled in about "
                    + (nextBreakIn / 60) + " minute(s)");
        }

        private void scheduleNextBreak() {
            breakActive = false;
            afkBreakActive = false;
            logoutBreakActive = false;
            loginPending = false;
            breakTimeRemaining = 0;
            nextLoginAttemptAt = 0L;
            settleAfterLoginUntil = 0L;
            loginRetryCount = 0;
            status = "Resuming";
            initializeBreakTimer();
        }

        private void startRandomBreak(Runnable parkMouse) {
            activePostLoginSettleSeconds = settings.postLoginSettleSeconds;
            int chance = clamp(settings.logoutBreakChance, 0, 100);
            if (randomBetween(0, 99) < chance) startLogoutBreak();
            else startAfkBreak(parkMouse);
        }

        private void startAfkBreak(Runnable parkMouse) {
            int min = Math.max(1, Math.min(settings.afkBreakMinMinutes, settings.afkBreakMaxMinutes));
            int max = Math.max(min, Math.max(settings.afkBreakMinMinutes, settings.afkBreakMaxMinutes));
            int minutes = randomBetween(min, max);

            breakActive = true;
            afkBreakActive = true;
            logoutBreakActive = false;
            loginPending = false;
            breakTimeRemaining = minutes * 60;
            status = "AFK break " + formatSeconds(breakTimeRemaining);
            safePark(parkMouse);
            Microbot.log("[BaseProfileDro] Started smart AFK break for " + minutes + " minute(s)");
        }

        private void startLogoutBreak() {
            int min = Math.max(1, Math.min(settings.logoutBreakMinMinutes, settings.logoutBreakMaxMinutes));
            int max = Math.max(min, Math.max(settings.logoutBreakMinMinutes, settings.logoutBreakMaxMinutes));
            int minutes = randomBetween(min, max);

            breakActive = true;
            afkBreakActive = false;
            logoutBreakActive = true;
            loginPending = false;
            breakTimeRemaining = minutes * 60;
            status = "Logout break " + formatSeconds(breakTimeRemaining);

            boolean loggedOut = attemptLogoutWithRetries(3);
            if (!loggedOut) {
                Microbot.log("[BaseProfileDro] Logout did not confirm after 3 attempts; "
                        + "break timer continues and return handling will retry later.");
            }
            Microbot.log("[BaseProfileDro] Started smart logout break for " + minutes + " minute(s)");
        }

        private boolean attemptLogoutWithRetries(int maxAttempts) {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    if (!Microbot.isLoggedIn()) return true;
                    Microbot.log("[BaseProfileDro] Logout attempt " + attempt + "/" + maxAttempts);
                    Rs2Player.logout();

                    long deadline = System.currentTimeMillis() + 2_000L;
                    while (Microbot.isLoggedIn() && System.currentTimeMillis() < deadline) {
                        sleepQuietly(100);
                    }
                    if (!Microbot.isLoggedIn()) return true;
                    if (attempt < maxAttempts) sleepQuietly(3_000);
                } catch (Exception ex) {
                    Microbot.log("[BaseProfileDro] Logout attempt failed: " + ex.getMessage());
                    if (attempt < maxAttempts) sleepQuietly(3_000);
                }
            }
            return !Microbot.isLoggedIn();
        }

        private void endAfkBreak() {
            breakActive = false;
            afkBreakActive = false;
            breakTimeRemaining = 0;
            status = "Resuming";
            Microbot.log("[BaseProfileDro] AFK break ended");
        }

        /** Exact DroZone/AutoLogin 5/15/60/300s backoff with +/-30% jitter + post-login settle. */
        private boolean updateLogoutReturn(long now) {
            if (Microbot.isLoggedIn()) {
                if (settleAfterLoginUntil == 0L) {
                    settleAfterLoginUntil = now + activePostLoginSettleSeconds * 1_000L;
                    status = "Logged in - settling";
                    return true;
                }
                if (now < settleAfterLoginUntil) {
                    status = "Logged in - settling " + formatMillis(settleAfterLoginUntil - now);
                    return true;
                }

                Microbot.log("[BaseProfileDro] Auto login succeeded; logout break ended");
                scheduleNextBreak();
                return false;
            }

            if (Microbot.getClient() == null) {
                status = "Break over - waiting for client";
                return true;
            }

            GameState state = Microbot.getClient().getGameState();
            if (state == GameState.LOGGING_IN) {
                status = "Auto login - logging in";
                return true;
            }
            if (state != GameState.LOGIN_SCREEN) {
                status = "Break over - waiting for login screen";
                return true;
            }

            if (now < nextLoginAttemptAt) {
                status = "Break over - auto login waiting";
                return true;
            }

            loginRetryCount++;
            int backoffSeconds = computeRetryBackoffSeconds(loginRetryCount, 5);
            nextLoginAttemptAt = now + backoffSeconds * 1_000L;
            try {
                boolean initiated = LoginManager.login();
                status = initiated
                        ? "Auto login attempt " + loginRetryCount
                        : "Auto login retry pending";
                Microbot.log("[BaseProfileDro] Auto login attempt " + loginRetryCount
                        + " initiated=" + initiated
                        + "; next retry in about " + backoffSeconds + "s");
            } catch (Exception ex) {
                status = "Auto login retry";
                Microbot.log("[BaseProfileDro] Auto login attempt failed: " + ex.getMessage());
            }
            return true;
        }

        private static int computeRetryBackoffSeconds(int attemptsSoFar, int configuredBaseSeconds) {
            int[] schedule = {5, 15, 60, 300};
            int idx = Math.min(Math.max(attemptsSoFar - 1, 0), schedule.length - 1);
            int floor = Math.max(1, configuredBaseSeconds);
            int step = Math.max(schedule[idx], floor);
            double jitter = 0.7 + ThreadLocalRandom.current().nextDouble() * 0.6;
            long jittered = Math.round(step * jitter);
            return (int) Math.max(floor, jittered);
        }

        private static void safePark(Runnable parkMouse) {
            if (parkMouse == null) return;
            try {
                parkMouse.run();
            } catch (Exception ignored) {
            }
        }
    }

    private static String formatSeconds(int seconds) {
        return formatMillis(Math.max(0L, seconds) * 1_000L);
    }

    private static String formatMillis(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatDuration(long millis) {
        return formatMillis(millis);
    }
}
