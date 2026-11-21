package net.runelite.client.plugins.microbot.cluesolver;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = "Clue Solver",
        description = "Automates solving clue scrolls by managing interactions",
        tags = {"clue", "solver", "automation"},
        authors = { "unknown" },
        version = ClueSolverPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/ClueSolverPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/ClueSolverPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
@PluginDependency(ClueScrollPlugin.class)
public class ClueSolverPlugin extends Plugin {

    final static String version = "1.0.12";
    private static final String CONFIG_GROUP = "cluesolver";
    private static final String ENABLE_KEY = "enableClueSolver";
    @Inject
    ClueSolverScript clueSolverScript;

    @Inject
    private ClueSolverOverlay clueSolverOverlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    ClueSolverConfig clueSolverConfig;

    @Inject
    ConfigManager configManager;

    public static volatile boolean isSolverRunning = false;

    @Provides
    ClueSolverConfig provideClueSolverConfig(ConfigManager configManager) {
        return configManager.getConfig(ClueSolverConfig.class);
    }

    @Override
    protected void startUp() {
        log.info("Starting Clue Solver Plugin");
        if (null != overlayManager) {
            overlayManager.add(clueSolverOverlay);
            clueSolverOverlay.myButton.hookMouseListener();
        }
        configureSolver();
    }

    @Override
    protected void shutDown() {
        log.info("Shutting down Clue Solver Plugin");
        overlayManager.remove(clueSolverOverlay);
        clueSolverOverlay.myButton.unhookMouseListener();
        stopClueSolver();
    }

    private synchronized void startClueSolver() {
        if (!ClueSolverPlugin.isSolverRunning) {
            clueSolverScript.start();
            ClueSolverPlugin.isSolverRunning = true;
            log.info("Clue Solver Script started.");
        }
    }

    private synchronized void stopClueSolver() {
        if (ClueSolverPlugin.isSolverRunning) {
            clueSolverScript.shutdown();
            ClueSolverPlugin.isSolverRunning = false;
            log.info("Clue Solver Script stopped.");
        }
    }

    /**
     * Configures the solver based on current settings.
     * Ensures the solver state reflects the config.
     */
    public void configureSolver() {
        if (clueSolverConfig.enableClueSolver()) {
            startClueSolver();
        } else {
            stopClueSolver();
        }
    }

    public void setSolverEnabled(boolean enabled) {
        configManager.setConfiguration(CONFIG_GROUP, ENABLE_KEY, enabled);
        if (enabled) {
            startClueSolver();
        } else {
            stopClueSolver();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (CONFIG_GROUP.equals(event.getGroup()) && ENABLE_KEY.equals(event.getKey())) {
            log.info("Configuration change detected for Clue Solver Plugin");
            configureSolver();
        }
    }


}
