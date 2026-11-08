package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.ui.MicrobotTopLevelConfigPanel;

import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Provider;
import javax.swing.SwingUtilities;

import java.awt.*;
import java.util.List;

@PluginDescriptor(
        name = PluginDescriptor.Choken + "Flipper",
        description = "Follows Flipping Copilot's suggestions at the Grand Exchange",
        tags = {"flip", "ge", "grand", "exchange", "automation"},
        authors = {"Choken", "afss0", "Joinkiee"},
        version = FlipperPlugin.version,
        minClientVersion = "2.6.53",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class FlipperPlugin extends Plugin {
    public static final String version = "1.3.0";
    /** Logger name for the few lines meant for client.log whatever this package's own log level is. */
    static final String DIAGNOSTICS_LOGGER = "GEFlipper";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FlipperPlugin.class);
    /** Outside this package's logger on purpose: that one is held at WARN to keep the game chat quiet. */
    private static final org.slf4j.Logger diagnostics = org.slf4j.LoggerFactory.getLogger(DIAGNOSTICS_LOGGER);
    @Inject
    private Client client;
    @Inject
    private FlipperScript flipperScript;

    public FlipperScript getFlipperScript() {
        return flipperScript;
    }
    @Inject
    private net.runelite.client.plugins.microbot.geflipper.FlipperConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FlipperOverlay overlay;
    @Inject
    private ConfigManager configManager;
    @Inject
    private PluginManager pluginManager;
    private volatile long lifecycleGeneration;
    @Inject
    private Provider<MicrobotTopLevelConfigPanel> settingsPanelProvider;
    private WaitingMouseSettings waitingMouseSettings;
    /** Lives from plugin load until it is unloaded, whether GE Flipper is on or off. */
    private SettingsLook settingsLook;
    @Inject
    private MouseManager mouseManager;
    private ManualMouseGuard manualMouseGuard;
    private MuleLoginOverlay muleLoginOverlay;
    @Inject
    private net.runelite.client.eventbus.EventBus eventBus;
    private final FinishBreakLock finishBreakLock = new FinishBreakLock();
    private FinishLogoutHold logoutHold;
    private javax.swing.Timer logoutHoldTimer;

    @Provides
    net.runelite.client.plugins.microbot.geflipper.FlipperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FlipperConfig.class);
    }

    private static final int LOOK_CHECK_MS = 1000;
    private static final String RUNELITE_THEME = "net.runelite.client.ui.laf.RuneLiteLAF";
    private static final String FLAT_THEME = "com.formdev.flatlaf.FlatLaf";
    private net.runelite.client.eventbus.EventBus lookEventBus;
    private LookUnloadWatch lookWaiter;
    private javax.swing.Timer lookTimer;
    private boolean lookAttempted;
    /** Whether Microbot's window is on screen with its own theme. Tests swap it. */
    java.util.function.BooleanSupplier clientUiReady = () -> clientUiReady(client);

    /**
     * Runs once when Microbot loads the plugin, on or off, so its settings panel keeps its sections folded and greys
     * settings that are not in use either way. Look only: it never starts the script or changes a setting.
     *
     * Nothing here may touch Microbot's settings panel while the client is starting. Microbot starts the game and
     * loads plugins before it applies its dark theme, and a panel built before then leaves the whole Microbot side
     * panel in Java's default white look. So the look waits until Microbot's window is on screen with its own theme:
     * checked once a second, and when the plugin is switched on, right before the panel is used.
     */
    @Inject
    void installSettingsLook(net.runelite.client.eventbus.EventBus eventBus) {
        lookEventBus = eventBus;
        LookUnloadWatch waiter = new LookUnloadWatch(this);
        lookWaiter = waiter;
        try {
            eventBus.register(waiter);
        } catch (RuntimeException | LinkageError unavailable) {
            lookWaiter = null;
        }
        javax.swing.Timer timer = new javax.swing.Timer(LOOK_CHECK_MS, event -> startSettingsLook());
        timer.setRepeats(true);
        lookTimer = timer;
        timer.start();
    }

    /**
     * True once Microbot's game window is on screen and its own theme is in use. That is after client start-up,
     * never during it, whatever state the game itself is in.
     */
    static boolean clientUiReady(Client client) {
        try {
            java.awt.Canvas canvas = client == null ? null : client.getCanvas();
            if (canvas == null || !canvas.isShowing()) return false;
            javax.swing.LookAndFeel theme = javax.swing.UIManager.getLookAndFeel();
            for (Class<?> type = theme == null ? null : theme.getClass(); type != null; type = type.getSuperclass()) {
                if (RUNELITE_THEME.equals(type.getName()) || FLAT_THEME.equals(type.getName())) return true;
            }
            return false;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /** Swing thread only. Builds the look once, and only once Microbot's window is up with its own theme. */
    void startSettingsLook() {
        if (lookAttempted || settingsLook != null) {
            stopLookWaiter();
            return;
        }
        boolean ready;
        try {
            ready = clientUiReady.getAsBoolean();
        } catch (RuntimeException | LinkageError unavailable) {
            ready = false;
        }
        if (!ready) return;
        lookAttempted = true;
        stopLookWaiter();
        try {
            if (settingsPanelProvider == null || config == null || lookEventBus == null) return;
            SettingsLook look = new SettingsLook(this, config, settingsPanelProvider.get().getWrappedPanel(),
                lookEventBus, () -> flipperScript != null && flipperScript.isRunning());
            look.setBindingsActive(waitingMouseSettings != null);
            look.start();
            settingsLook = look;
        } catch (RuntimeException | LinkageError unavailable) {
            log.warn("GE Flipper settings look unavailable on this client: {}", unavailable.toString());
        }
    }

    private void stopLookWaiter() {
        javax.swing.Timer timer = lookTimer;
        lookTimer = null;
        if (timer != null) timer.stop();
        LookUnloadWatch waiter = lookWaiter;
        lookWaiter = null;
        if (waiter == null || lookEventBus == null) return;
        try {
            lookEventBus.unregister(waiter);
        } catch (RuntimeException ignored) {
        }
    }

    /** Stops the wait if the plugin is unloaded before the look started. Touches no Swing component. */
    static final class LookUnloadWatch {
        private final FlipperPlugin plugin;

        LookUnloadWatch(FlipperPlugin plugin) {
            this.plugin = plugin;
        }

        @Subscribe
        public void onPluginChanged(net.runelite.client.events.PluginChanged event) {
            if (!event.isLoaded() && event.getPlugin() == plugin) SwingUtilities.invokeLater(plugin::stopLookWaiter);
        }
    }


    /**
     * Keeps this plugin's own log volume in hand by setting the level of THIS package's logger only.
     *
     * An earlier build detached the root logger's GameChatAppender and switched the whole client's
     * chat configuration off on every startup, and shutdown never put it back. Starting and stopping
     * GE Flipper therefore silenced in-game diagnostics for every other Microbot script. Nothing
     * outside this package is touched here.
     */
    private void applyOwnLogLevel() {
        try {
            org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger("net.runelite.client.plugins.microbot.geflipper");
            if (slf4jLogger instanceof ch.qos.logback.classic.Logger) {
                ((ch.qos.logback.classic.Logger) slf4jLogger).setLevel(verboseLoggingEnabled()
                    ? ch.qos.logback.classic.Level.INFO
                    : ch.qos.logback.classic.Level.WARN);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Whether the user asked for a full trace of this plugin's own activity. Read defensively. */
    private boolean verboseLoggingEnabled() {
        try {
            return config != null && config.verboseLogging();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    protected void startUp() throws AWTException{
        lifecycleGeneration++;
        // One line per start, so a log shows which build is running (an old jar was once mistaken for the new one).
        diagnostics.info("GE Flipper {} started.", version);
        warnIfSlotSwapOff();
        applyOwnLogLevel();
        removeOldGuideText();
        if (settingsLook == null) startSettingsLook();
        if (settingsLook != null) settingsLook.setBindingsActive(true);
        if (settingsPanelProvider != null && configManager != null) {
            waitingMouseSettings = new WaitingMouseSettings(this, config,
                settingsPanelProvider.get().getWrappedPanel(),
                this::saveWaitingMouseChance,
                this::requestFinishFromButton, () -> flipperScript != null && flipperScript.isRunning()
                    && !flipperScript.isFinishing(),
                () -> flipperScript.waitingMouseFrequency(config),
                () -> config.waitingMousePreset() != FlipperConfig.RandomizationPreset.DAY_FATIGUE
                    || !config.randomizeMouseSpeed(),
                () -> flipperScript.waitingMouseDescription(config));
            waitingMouseSettings.setMuleStatusSupplier(() -> flipperScript.getMuleSettingsStatus());
            waitingMouseSettings.start();
        }
        if (overlay != null) {
            overlay.clearStats();
            if (overlayManager != null) overlayManager.add(overlay);
        }
        flipperScript.setSettingsAvailabilityChanged(this::refreshWaitingMouseSettings);
        flipperScript.setWaitingMouseFrequencyChanged(this::refreshWaitingMouseValue);
        flipperScript.setSystemUpdateHandler(this::requestFinish);
        if (configManager != null) flipperScript.attachMuleLedgerStore(() -> new MuleLedgerConfigStore(configManager));
        flipperScript.run(config);
        startMuleLoginOverlay();
        if (mouseManager != null && client != null) {
            manualMouseGuard = new ManualMouseGuard(mouseManager, client::getCanvas,
                () -> config != null && config.blockManualMouseInput()
                    && flipperScript != null && flipperScript.isRunning()
                    && client.getGameState() == GameState.LOGGED_IN);
            manualMouseGuard.start();
        }
        refreshWaitingMouseSettings();
    }

    /**
     * The help moved to new keys so its new text shows. RuneLite had saved the old text under the old keys in
     * profiles; remove those copies once. No other setting is touched.
     */
    private void removeOldGuideText() {
        if (configManager == null) return;
        for (String key : FlipperConfig.OLD_GUIDE_KEYS) {
            try {
                if (configManager.getConfiguration(FlipperConfig.GROUP, key) != null) {
                    configManager.unsetConfiguration(FlipperConfig.GROUP, key);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Only an explicit slider edit saves the manual value; config replay never writes settings. */
    void saveWaitingMouseChance(int value) {
        if (configManager == null) return;
        synchronized (configManager) {
            configManager.setConfiguration(FlipperConfig.GROUP, "waitingMousePreset",
                FlipperConfig.RandomizationPreset.CUSTOM);
            configManager.setConfiguration(FlipperConfig.GROUP, "waitingMouseChance",
                Math.max(0, Math.min(100, value)));
        }
    }

    /** Called only by the owned native speed checkbox's explicit enable action. */
    void selectFatigueFromMouseSpeedClick() {
        if (configManager != null) configManager.setConfiguration(FlipperConfig.GROUP, "waitingMousePreset",
            FlipperConfig.RandomizationPreset.DAY_FATIGUE);
    }

    static final int STOP_RETRIES = 3;
    static final int STOP_RETRY_MS = 30_000;

    /** True when Finish started. Breaks wait (through Break Handler's own lock) until GE Flipper stops. */
    boolean requestFinish() {
        if (flipperScript == null || !flipperScript.isRunning()) return false;
        long generation = lifecycleGeneration;
        if (!flipperScript.requestFinish(() -> stopAfterFinish(generation))) {
            log.warn("Finish could not start right now. Try again in a moment.");
            return false;
        }
        finishBreakLock.acquire();
        diagnostics.info("Finish started: listing items; breaks wait until GE Flipper stops.");
        return true;
    }

    private void requestFinishFromButton() {
        WaitingMouseSettings panel = waitingMouseSettings;
        if (!requestFinish() && panel != null) panel.finishRefused();
    }

    void stopAfterFinish(long generation) {
        SwingUtilities.invokeLater(() -> holdUntilLoggedOut(generation));
    }

    /**
     * Everything is listed. GE Flipper stops all input so the game's idle timer can log the account out, keeps
     * auto-login from logging back in meanwhile, and switches itself off after the logout.
     */
    private void holdUntilLoggedOut(long generation) {
        String declined = generation != lifecycleGeneration ? "GE Flipper was restarted"
            : flipperScript == null ? "no script"
            : !flipperScript.isFinishComplete() ? "Finish is not complete yet"
            : !flipperScript.isRunning() ? "the script already stopped"
            : logoutHold != null ? "already waiting for the logout" : null;
        if (declined != null) {
            diagnostics.info("Finish: not stopping GE Flipper ({}).", declined);
            return;
        }
        flipperScript.shutdown();
        flipperScript.setAwaitingFinishLogout(true);
        log.warn("Finish: everything is listed. GE Flipper turns off once the game logs you out; "
            + "auto-login waits until you log in again.");
        FinishLogoutHold hold = new FinishLogoutHold(new FinishLogoutHold.Port() {
            @Override public boolean loggedOut() {
                return client != null && client.getGameState() == GameState.LOGIN_SCREEN;
            }
            @Override public void post(Object event) {
                if (eventBus != null) eventBus.post(event);
            }
            @Override public void finished(boolean loggedOut) {
                stopLogoutHold();
                if (!loggedOut) {
                    log.warn("Finish: the game kept you logged in for 10 minutes (another plugin may stop idle "
                        + "logout). GE Flipper is turning off; log out when you're ready.");
                }
                disableAfterFinish(generation, 1);
            }
        });
        logoutHold = hold;
        hold.start(nowMillis());
        if (hold.tick(nowMillis())) return;
        javax.swing.Timer timer = new javax.swing.Timer((int) FinishLogoutHold.RENEW_MS, e -> hold.tick(nowMillis()));
        logoutHoldTimer = timer;
        timer.start();
    }

    private void stopLogoutHold() {
        javax.swing.Timer timer = logoutHoldTimer;
        logoutHoldTimer = null;
        if (timer != null) timer.stop();
        FinishLogoutHold hold = logoutHold;
        logoutHold = null;
        if (hold != null) hold.cancel();
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private void disableAfterFinish(long generation, int attempt) {
        String declined = generation != lifecycleGeneration ? "GE Flipper was restarted"
            : pluginManager == null ? "no plugin manager"
            : !pluginManager.isPluginActive(this) ? "the plugin is already off" : null;
        if (declined != null) {
            diagnostics.info("Finish: not stopping GE Flipper ({}).", declined);
            return;
        }
        try {
            // This explicit Finish request disables only this plugin.
            pluginManager.setPluginEnabled(this, false);
            pluginManager.stopPlugin(this);
        } catch (PluginInstantiationException | RuntimeException failure) {
            if (attempt < STOP_RETRIES) {
                log.warn("Finish completed, but GE Flipper could not be disabled (try {} of {}). Retrying in {} s.",
                    attempt, STOP_RETRIES, STOP_RETRY_MS / 1000);
                javax.swing.Timer retry = new javax.swing.Timer(STOP_RETRY_MS,
                    event -> disableAfterFinish(generation, attempt + 1));
                retry.setRepeats(false);
                retry.start();
            } else {
                log.error("Finish completed, but GE Flipper could not be disabled. Turn it off manually.");
            }
            return;
        }
        diagnostics.info("Finish: GE Flipper is off.");
    }

    /** The Mule receiver's stats on the login screen, where RuneLite draws no overlays of its own. */
    private void startMuleLoginOverlay() {
        MuleLoginOverlay previous = muleLoginOverlay;
        muleLoginOverlay = null;
        if (previous != null) previous.close();
        if (client == null || config == null || flipperScript == null || GraphicsEnvironment.isHeadless()) return;
        MuleLoginOverlay created = new MuleLoginOverlay(new MuleLoginOverlay.Source() {
            @Override public boolean enabled() {
                return config.showOverlay() && config.muleMode() == FlipperConfig.MuleMode.MULE_RECEIVER
                    && flipperScript.isRunning();
            }
            @Override public GameState gameState() { return client.getGameState(); }
            @Override public Component anchor() { return client.getCanvas(); }
            @Override public Rectangle canvasOnScreen() {
                Canvas canvas = client.getCanvas();
                if (canvas == null || !canvas.isShowing()) return null;
                try {
                    return new Rectangle(canvas.getLocationOnScreen(), canvas.getSize());
                } catch (IllegalComponentStateException notShowing) {
                    return null;
                }
            }
            @Override public String title() { return MuleOverlayView.title(); }
            @Override public List<MuleOverlayView.Row> rows() {
                return MuleOverlayView.receiverRows(flipperScript.getMuleConnectionStatus(),
                    flipperScript.getMuleLedgerSnapshot(), false, flipperScript.getSlotActionStatus());
            }
        });
        muleLoginOverlay = created;
        created.start();
    }

    /**
     * Flipping Copilot's slot action swap belongs to the user. This plugin reads it and reports
     * when it is off, but never writes it - enabling it silently is what made the user's own
     * left-click setting flip back on.
     */
    private void warnIfSlotSwapOff() {
        if (configManager == null || config == null
            || config.slotAction() != FlipperConfig.SlotAction.COPILOT_LEFT_CLICK) return;
        // An unset preference uses Copilot's enabled default, so only explicit false warrants this warning.
        if (!"false".equals(configManager.getConfiguration("flippingcopilot", "slotActionSwap"))) return;
        log.warn("GE Flipper's Copilot left-click swap is On, but Flipping Copilot's slot action swap is off. "
            + "Enable it in Copilot's own settings, or set Copilot left-click swap to Off in GE Flipper. "
            + "This plugin never changes that setting for you.");
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!FlipperConfig.GROUP.equals(event.getGroup())) return;
        if (event.getKey().startsWith("mule") && flipperScript != null) flipperScript.invalidateMuleActions();
        if ("slotActionMode".equals(event.getKey())) {
            if (flipperScript != null) flipperScript.invalidateMouseMovement();
            warnIfSlotSwapOff();
        }
        if ("verboseLogging".equals(event.getKey())) applyOwnLogLevel();
        if ("randomizeMouseSpeed".equals(event.getKey())) {
            if (flipperScript != null) {
                flipperScript.invalidateMouseMovement();
                flipperScript.resetWaitingMouse();
                flipperScript.waitingMouseFrequency(config);
            }
            refreshWaitingMouseSettings();
        }
        if (event.getKey().startsWith("waitingMouse")) {
            if (flipperScript != null) {
                flipperScript.resetWaitingMouse();
                // Observe preset transitions even when its settings panel is hidden.
                flipperScript.waitingMouseFrequency(config);
            }
            refreshWaitingMouseSettings();
        }
        if ("secondScreenAfkMode".equals(event.getKey()) && flipperScript != null
            && config != null && config.secondScreenAfkMode() == FlipperConfig.SecondScreenAfkMode.OFF) {
            flipperScript.resetSecondScreenAfk();
        }
        if ("showOverlay".equals(event.getKey()) && overlay != null && !config.showOverlay()) overlay.clearStats();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.TRADEREQ || flipperScript == null
            || client == null || client.getGameState() != GameState.LOGGED_IN) return;
        flipperScript.recordMuleTradeRequest(MulePolicy.tradeRequestName(event.getName(), event.getMessage()),
            client.getAccountHash());
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event) {
        if (flipperScript != null) {
            flipperScript.invalidateMouseMovement();
            flipperScript.resetWaitingMousePresets();
            flipperScript.pauseFinishForProfileChange();
            flipperScript.pauseMuleForProfileChange();
            flipperScript.resetSecondScreenAfk();
        }
        refreshWaitingMouseSettings();
    }

    private void refreshWaitingMouseSettings() {
        WaitingMouseSettings panel = waitingMouseSettings;
        if (panel == null) return;
        Runnable refresh = () -> {
            if (waitingMouseSettings == panel) panel.refresh();
        };
        if (SwingUtilities.isEventDispatchThread()) refresh.run();
        else SwingUtilities.invokeLater(refresh);
    }

    private void refreshWaitingMouseValue() {
        WaitingMouseSettings panel = waitingMouseSettings;
        if (panel != null) SwingUtilities.invokeLater(() -> {
            if (waitingMouseSettings == panel) panel.refreshValue();
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            if (overlay != null) overlay.clearStats();
            if (flipperScript != null) flipperScript.clearCachedTradingState();
        }
    }

    @Override
    protected void shutDown() {
        lifecycleGeneration++;
        stopLogoutHold();
        finishBreakLock.release();
        if (flipperScript != null) flipperScript.setAwaitingFinishLogout(false);
        if (manualMouseGuard != null) manualMouseGuard.close();
        manualMouseGuard = null;
        flipperScript.setSettingsAvailabilityChanged(null);
        flipperScript.setWaitingMouseFrequencyChanged(null);
        flipperScript.setSystemUpdateHandler(null);
        MuleLoginOverlay loginOverlay = muleLoginOverlay;
        muleLoginOverlay = null;
        if (loginOverlay != null) loginOverlay.close();
        flipperScript.shutdown();
        flipperScript.state = State.GOING_TO_GE;
        if (waitingMouseSettings != null) waitingMouseSettings.close();
        waitingMouseSettings = null;
        if (settingsLook != null) settingsLook.setBindingsActive(false);
        if (overlay != null) {
            overlay.clearStats();
            if (overlayManager != null) overlayManager.remove(overlay);
        }
    }
}
