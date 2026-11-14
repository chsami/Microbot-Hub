package net.runelite.client.plugins.microbot.droslayer;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Observed game flags gate Wilderness work; clicks never count as confirmation. */
@Slf4j
public final class DroSlayerWildernessSettings {
    interface Environment {
        boolean loggedIn();
        boolean paused();
        boolean skullPreventionEnabled();
        boolean acceptAidEnabled();
        boolean controlsVisible();
        void revealControls();
        boolean enableSkullPrevention();
        boolean disableAcceptAid();
        void restoreTab();
    }
    private static final long ACTION_INTERVAL_MS = 1800L;
    private static final int MAX_TOGGLE_ATTEMPTS = 3;
    private static final int MAX_NAVIGATION_ATTEMPTS = 6;
    private final Environment environment;
    private final LongSupplier clock;
    private long nextActionAt;
    private int skullAttempts;
    private int aidAttempts;
    private int navigationAttempts;
    private boolean adjusting;
    private String failureReason = "";

    public DroSlayerWildernessSettings() { this(new NativeEnvironment(), () -> System.nanoTime() / 1_000_000L); }
    DroSlayerWildernessSettings(Environment environment, LongSupplier clock) { this.environment = environment; this.clock = clock; }

    public boolean ensureSafeSettings() {
        if (!environment.loggedIn() || environment.paused()) return false;
        // Read live flags even after a prior failure, login or world hop. A manual correction releases the hold.
        boolean skullEnabled = environment.skullPreventionEnabled();
        boolean aidEnabled = environment.acceptAidEnabled();
        if (skullEnabled && !aidEnabled) {
            if (adjusting) environment.restoreTab();
            adjusting = false;
            skullAttempts = aidAttempts = navigationAttempts = 0;
            failureReason = "";
            return true;
        }
        if (!failureReason.isEmpty()) return false;
        long now = clock.getAsLong();
        if (now < nextActionAt) return false;
        nextActionAt = now + ACTION_INTERVAL_MS;
        adjusting = true;
        if (!environment.controlsVisible()) {
            if (navigationAttempts >= MAX_NAVIGATION_ATTEMPTS) return hold("Cannot reach Wilderness safety controls");
            navigationAttempts++;
            environment.revealControls();
            return false;
        }
        navigationAttempts = 0;
        if (!skullEnabled) {
            if (skullAttempts >= MAX_TOGGLE_ATTEMPTS) return hold("Skull prevention did not enable after three attempts");
            skullAttempts++;
            environment.enableSkullPrevention();
            log.info("Wilderness safety: enable skull prevention attempt={}/{}; awaiting game flag", skullAttempts, MAX_TOGGLE_ATTEMPTS);
        } else {
            if (aidAttempts >= MAX_TOGGLE_ATTEMPTS) return hold("Accept Aid did not disable after three attempts");
            aidAttempts++;
            environment.disableAcceptAid();
            log.info("Wilderness safety: disable Accept Aid attempt={}/{}; awaiting game flag", aidAttempts, MAX_TOGGLE_ATTEMPTS);
        }
        return false;
    }

    /** Compatibility alias; both safety settings remain mandatory. */
    public boolean ensureSkullPrevention() { return ensureSafeSettings(); }
    public boolean isBlocked() { return !failureReason.isEmpty(); }
    public String getFailureReason() { return failureReason; }
    public void reset() {
        nextActionAt = 0L;
        skullAttempts = aidAttempts = navigationAttempts = 0;
        adjusting = false;
        failureReason = "";
    }
    private boolean hold(String reason) {
        failureReason = reason;
        Microbot.status = reason + "; Wilderness Slayer held";
        log.warn("{}; Wilderness Slayer cannot continue until the live settings are corrected", reason);
        return false;
    }

    private static final class NativeEnvironment implements Environment {
        private InterfaceTab previousTab;
        public boolean loggedIn() {
            return Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getGameState() == GameState.LOGGED_IN).orElse(false);
        }
        public boolean paused() { return Microbot.pauseAllScripts.get(); }
        public boolean skullPreventionEnabled() { return Microbot.getVarbitValue(VarbitID.SKULL_PREVENT_ENABLED) == 1; }
        public boolean acceptAidEnabled() { return Microbot.getVarbitValue(VarbitID.OPTION_ACCEPTAID) != 0; }
        public boolean controlsVisible() {
            return !Rs2Bank.isOpen() && !Rs2Widget.isWidgetVisible(InterfaceID.Settings.UNIVERSE)
                    && Rs2Widget.isWidgetVisible(InterfaceID.SettingsSide.SKULL_PREVENTION);
        }
        public void revealControls() {
            if (previousTab == null) previousTab = Rs2Tab.getCurrentTab();
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, true, (java.util.function.BooleanSupplier) () -> {
                if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return true; }
                if (Rs2Widget.isWidgetVisible(InterfaceID.Settings.UNIVERSE)) return Rs2Widget.clickWidget(InterfaceID.Settings.CLOSE);
                if (!Rs2Tab.isCurrentTab(InterfaceTab.SETTINGS)) return Rs2Tab.switchTo(InterfaceTab.SETTINGS);
                Widget controls = Microbot.getClientThread().runOnClientThreadOptional(() ->
                        findControls(Rs2Widget.getWidget(InterfaceID.SettingsSide.TABS), 0)).orElse(null);
                return controls != null && Rs2Widget.clickWidget(controls);
            });
        }
        public boolean enableSkullPrevention() {
            return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true, (java.util.function.BooleanSupplier) () -> {
                if (skullPreventionEnabled()) return false; // A delayed/manual enable must never be toggled back off.
                Widget checkbox = Microbot.getClientThread().runOnClientThreadOptional(() ->
                        skullCheckbox(Rs2Widget.getWidget(InterfaceID.SettingsSide.SKULL_PREVENTION))).orElse(null);
                return checkbox != null && Rs2Widget.clickWidget(checkbox);
            });
        }
        public boolean disableAcceptAid() {
            return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true, (java.util.function.BooleanSupplier) () -> {
                if (!acceptAidEnabled()) return false;
                Widget button = Microbot.getClientThread().runOnClientThreadOptional(() ->
                        actionable(Rs2Widget.getWidget(InterfaceID.SettingsSide.ACCEPTAID), 0)).orElse(null);
                return button != null && Rs2Widget.clickWidget(button);
            });
        }
        public void restoreTab() {
            InterfaceTab tab = previousTab;
            previousTab = null;
            if (tab != null && tab != InterfaceTab.SETTINGS && tab != InterfaceTab.NOTHING_SELECTED) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, true,
                        (java.util.function.BooleanSupplier) () -> Rs2Tab.switchTo(tab));
            }
        }
    }

    static Widget skullCheckbox(Widget container) {
        if (container == null || container.isHidden()) return null;
        // Native SettingsSide 116:5 has label child 0 and checkbox child 1.
        Widget checkbox = container.getChild(1);
        if (visibleBounds(checkbox)) return checkbox;
        return actionable(container, 0);
    }
    private static Widget findControls(Widget widget, int depth) {
        if (widget == null || widget.isHidden() || depth > 4) return null;
        String[] actions = widget.getActions();
        if (visibleBounds(widget) && actions != null && Arrays.stream(actions).anyMatch(action -> action != null
                && action.replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT).contains("controls"))) return widget;
        Widget[] children = widget.getDynamicChildren();
        if (children != null) for (Widget child : children) { Widget found = findControls(child, depth + 1); if (found != null) return found; }
        return null;
    }
    private static Widget actionable(Widget widget, int depth) {
        if (widget == null || widget.isHidden() || depth > 4) return null;
        String[] actions = widget.getActions();
        if (visibleBounds(widget) && actions != null && Arrays.stream(actions).anyMatch(action -> action != null && !action.isBlank())) return widget;
        Widget[] children = widget.getDynamicChildren();
        if (children != null) for (Widget child : children) { Widget found = actionable(child, depth + 1); if (found != null) return found; }
        return null;
    }
    private static boolean visibleBounds(Widget widget) {
        if (widget == null || widget.isHidden()) return false;
        Rectangle bounds = widget.getBounds();
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }
}
