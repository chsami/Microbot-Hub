package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;

/** Owns the temporary Copilot sell-only mode requested by the Finish button. */
final class FinishSession {
    static final long COMPLETE_STABLE_MS = 5000;
    private static final String REQUESTED = "Finish requested. Waiting for Copilot.";
    private static final String UNAVAILABLE = "Finish paused: Copilot's finish controls are unavailable. Check Copilot or stop GE Flipper.";
    private static final String PAUSED = "Finish paused: resume Copilot to finish the offers.";
    private static final String MODE_DISABLED = "Finish paused: Copilot sell-only mode was disabled. Enable it or stop GE Flipper.";
    private static final String REFRESHING = "Finishing: waiting for a fresh Copilot suggestion.";

    private boolean requested;
    private boolean active;
    private boolean waitForAllSales;
    private boolean originalSellOnly;
    private boolean changedSellOnly;
    private boolean waitingForPreviousRequest;
    private boolean profileChanged;
    private boolean restorationFailed;
    private long generation;
    private long readySince;
    private boolean readinessStarted;
    private String status = "";
    private Instant baselineReceivedAt;
    private Object preferences;
    private Object manager;
    private Object pausedManager;
    private Method getSellOnly;
    private Method setSellOnly;
    private Method setNeeded;
    private Method getNeeded;
    private Method getInProgress;
    private Method getRefreshPending;
    private Method getError;
    private Method getReceivedAt;
    private Method getPaused;

    synchronized boolean request(boolean waitForAllSales) {
        if (requested) return false;
        this.waitForAllSales = waitForAllSales;
        requested = true;
        restorationFailed = false;
        readinessStarted = false;
        generation++;
        status = REQUESTED;
        return true;
    }

    synchronized boolean isRequested() { return requested; }
    synchronized boolean isActive() { return active; }
    synchronized long generation() { return generation; }
    synchronized String status() { return status; }

    /** Buy cancellation uses verified client state and need not await a new suggestion. */
    synchronized boolean readyForCancellation() {
        if (!requested || !active) return false;
        try {
            if (!bool(getSellOnly, preferences)) {
                changedSellOnly = false;
                status = MODE_DISABLED;
                readinessStarted = false;
                return false;
            }
            if (bool(getPaused, pausedManager)) {
                status = PAUSED;
                readinessStarted = false;
                return false;
            }
            if (getError.invoke(manager) != null) {
                status = "Finish paused: Copilot has a suggestion error. Wait for recovery or stop GE Flipper.";
                readinessStarted = false;
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            status = UNAVAILABLE;
            readinessStarted = false;
            return false;
        }
    }

    /** Resolves every required control before changing any runtime state. */
    synchronized boolean begin(Object copilot, Object controller, Object suggestionManager) {
        if (!requested) return false;
        if (restorationFailed) {
            status = "Finish paused: Copilot sell-only mode could not be restored. Check Copilot and restart GE Flipper.";
            return false;
        }
        if (profileChanged) {
            status = "Finish paused after a profile change. Restart GE Flipper before finishing.";
            return false;
        }
        if (active) return true;
        try {
            Object accountManager = null;
            if (controller != null) {
                try {
                    accountManager = controller.getClass().getMethod("getAccountStatusManager").invoke(controller);
                } catch (NoSuchMethodException unavailableAccessor) {
                    // Older Copilot versions expose this manager on the plugin only.
                }
            }
            if (accountManager == null && copilot != null) {
                accountManager = field(copilot, "accountStatusManager");
            }
            Object resolvedPreferences = field(accountManager, "suggestionPreferencesManager");
            Object resolvedPaused = controller.getClass().getMethod("getPausedManager").invoke(controller);
            Method resolvedGetMode = resolvedPreferences.getClass().getMethod("isSellOnlyMode");
            Method resolvedSetMode = resolvedPreferences.getClass().getMethod("setSellOnlyMode", boolean.class);
            Method resolvedSetNeeded = suggestionManager.getClass().getMethod("setSuggestionNeeded", boolean.class);
            Method resolvedGetNeeded = suggestionManager.getClass().getMethod("isSuggestionNeeded");
            Method resolvedInProgress = suggestionManager.getClass().getMethod("isSuggestionRequestInProgress");
            Method resolvedRefresh = suggestionManager.getClass().getMethod("isSuggestionRefreshPending");
            Method resolvedError = suggestionManager.getClass().getMethod("getSuggestionError");
            Method resolvedReceived = suggestionManager.getClass().getMethod("getSuggestionReceivedAt");
            Method resolvedGetPaused = resolvedPaused.getClass().getMethod("isPaused");
            boolean initialMode = bool(resolvedGetMode, resolvedPreferences);
            boolean initialRequest = bool(resolvedInProgress, suggestionManager);
            Instant initialReceivedAt = instant(resolvedReceived, suggestionManager);
            // A pause belongs to the user. Finish never resumes or resets Copilot.
            if (bool(resolvedGetPaused, resolvedPaused)) {
                status = PAUSED;
                return false;
            }
            preferences = resolvedPreferences;
            manager = suggestionManager;
            pausedManager = resolvedPaused;
            getSellOnly = resolvedGetMode;
            setSellOnly = resolvedSetMode;
            setNeeded = resolvedSetNeeded;
            getNeeded = resolvedGetNeeded;
            getInProgress = resolvedInProgress;
            getRefreshPending = resolvedRefresh;
            getError = resolvedError;
            getReceivedAt = resolvedReceived;
            getPaused = resolvedGetPaused;
            originalSellOnly = initialMode;
            baselineReceivedAt = initialReceivedAt;
            waitingForPreviousRequest = initialRequest;
            // Record ownership first so a failing setter can still be rolled back.
            changedSellOnly = !initialMode;
            if (changedSellOnly) setSellOnly.invoke(preferences, true);
            setNeeded.invoke(manager, true);
            active = true;
            readinessStarted = false;
            status = REFRESHING;
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            releaseControls();
            active = false;
            readinessStarted = false;
            status = UNAVAILABLE;
            return false;
        }
    }

    synchronized boolean canTrade(Object suggestion) {
        if (!currentSuggestionReady()) return false;
        String type = type(suggestion);
        if ("buy".equals(type) || "modify_buy".equals(type)) {
            status = "Finishing: ignoring a buy suggestion.";
            readinessStarted = false;
            return false;
        }
        if (!"sell".equals(type) && !"modify_sell".equals(type)
            && !"abort".equals(type) && !"collect".equals(type) && !"wait".equals(type)) {
            status = "Finish paused: Copilot's suggestion is unavailable. Wait for a valid suggestion or stop GE Flipper.";
            readinessStarted = false;
            return false;
        }
        status = waitForAllSales ? "Finishing: selling remaining items." : "Finishing: listing remaining items.";
        return true;
    }

    synchronized boolean healthyFreshWait(Object suggestion) {
        if (!currentSuggestionReady() || !"wait".equals(type(suggestion))) {
            readinessStarted = false;
            return false;
        }
        return true;
    }

    private boolean currentSuggestionReady() {
        if (!requested || !active) {
            readinessStarted = false;
            return false;
        }
        try {
            if (!bool(getSellOnly, preferences)) {
                // A user change ends our ownership of this transient mode.
                changedSellOnly = false;
                status = MODE_DISABLED;
                readinessStarted = false;
                return false;
            }
            if (bool(getPaused, pausedManager)) {
                status = PAUSED;
                readinessStarted = false;
                return false;
            }
            boolean inProgress = bool(getInProgress, manager);
            // A request already running when Finish began used the previous mode.
            // Let it settle, then explicitly ask once using sell-only mode.
            if (waitingForPreviousRequest) {
                if (!inProgress) {
                    baselineReceivedAt = instant(getReceivedAt, manager);
                    setNeeded.invoke(manager, true);
                    waitingForPreviousRequest = false;
                }
                status = REFRESHING;
                readinessStarted = false;
                return false;
            }
            if (getError.invoke(manager) != null) {
                status = "Finish paused: Copilot has a suggestion error. Wait for recovery or stop GE Flipper.";
                readinessStarted = false;
                return false;
            }
            Instant received = instant(getReceivedAt, manager);
            if (inProgress || bool(getNeeded, manager) || bool(getRefreshPending, manager)
                || received == null || (baselineReceivedAt != null && !received.isAfter(baselineReceivedAt))) {
                status = REFRESHING;
                readinessStarted = false;
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            status = UNAVAILABLE;
            readinessStarted = false;
            return false;
        }
    }

    synchronized boolean completionReady(CompletionSnapshot snapshot, long nowMillis) {
        boolean ready = currentSuggestionReady() && snapshot != null && snapshot.overviewReady
            && snapshot.noActiveBuys && snapshot.noCollectables && snapshot.noUnlistedItems
            && snapshot.healthyFreshWait && (!waitForAllSales || snapshot.noActiveSells);
        if (!ready) {
            readinessStarted = false;
            return false;
        }
        if (!readinessStarted || nowMillis < readySince) {
            readySince = nowMillis;
            readinessStarted = true;
        }
        return nowMillis - readySince >= COMPLETE_STABLE_MS;
    }

    /** Releases the temporary mode while preserving the explicit request over a logout. */
    synchronized void suspend() {
        releaseControls();
        active = false;
        readinessStarted = false;
        generation++;
        status = requested ? REQUESTED : "";
    }

    synchronized void pauseForProfileChange() {
        if (!requested) return;
        suspend();
        profileChanged = true;
        status = "Finish paused after a profile change. Restart GE Flipper before finishing.";
    }

    synchronized void close() {
        requested = false;
        profileChanged = false;
        active = false;
        readinessStarted = false;
        generation++;
        boolean restored = releaseControls();
        status = restored && !restorationFailed ? ""
            : "Copilot sell-only mode could not be restored. Check Copilot's sell-only setting.";
    }

    private boolean releaseControls() {
        boolean restored = true;
        try {
            // Never overwrite a user who already switched sell-only off, and never
            // write an unchanged original setting or any persisted preference.
            if (changedSellOnly && !originalSellOnly && preferences != null
                && bool(getSellOnly, preferences)) {
                setSellOnly.invoke(preferences, false);
                if (manager != null) setNeeded.invoke(manager, true);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            restored = false;
            restorationFailed = true;
        } finally {
            preferences = manager = pausedManager = null;
            getSellOnly = setSellOnly = setNeeded = getNeeded = getInProgress = null;
            getRefreshPending = getError = getReceivedAt = getPaused = null;
            baselineReceivedAt = null;
            waitingForPreviousRequest = changedSellOnly = originalSellOnly = false;
        }
        return restored;
    }

    private static boolean bool(Method method, Object target) throws ReflectiveOperationException {
        Object value = method.invoke(target);
        if (!(value instanceof Boolean)) throw new IllegalStateException("Unavailable finish state");
        return (Boolean) value;
    }

    private static Instant instant(Method method, Object target) throws ReflectiveOperationException {
        Object value = method.invoke(target);
        if (value != null && !(value instanceof Instant)) throw new IllegalStateException("Unavailable finish freshness");
        return (Instant) value;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        if (target == null) throw new IllegalStateException("Unavailable finish controls");
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        if (value == null) throw new IllegalStateException("Unavailable finish controls");
        return value;
    }

    private static String type(Object suggestion) {
        if (suggestion == null) return "";
        try {
            Object value = suggestion.getClass().getMethod("getType").invoke(suggestion);
            if (value instanceof Enum<?>) return ((Enum<?>) value).name().toLowerCase(Locale.ROOT);
            return value instanceof String ? ((String) value).toLowerCase(Locale.ROOT) : "";
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return "";
        }
    }

    static final class CompletionSnapshot {
        final boolean overviewReady;
        final boolean noActiveBuys;
        final boolean noCollectables;
        final boolean noUnlistedItems;
        final boolean noActiveSells;
        final boolean healthyFreshWait;

        CompletionSnapshot(boolean overviewReady, boolean noActiveBuys, boolean noCollectables,
                           boolean noUnlistedItems, boolean noActiveSells, boolean healthyFreshWait) {
            this.overviewReady = overviewReady;
            this.noActiveBuys = noActiveBuys;
            this.noCollectables = noCollectables;
            this.noUnlistedItems = noUnlistedItems;
            this.noActiveSells = noActiveSells;
            this.healthyFreshWait = healthyFreshWait;
        }
    }
}
