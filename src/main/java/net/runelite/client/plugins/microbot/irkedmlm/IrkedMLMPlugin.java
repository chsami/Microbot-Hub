package net.runelite.client.plugins.microbot.irkedmlm;

import ch.qos.logback.classic.Level;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = IrkedMLMPlugin.PREFIX + "Motherlode Mine",
        description = "Mines both levels, banks the ore and fixes the water wheels. Supports the gem bag and Imcando hammer.",
        tags = {"mining", "motherload", "mlm", "irked"},
        authors = {"irkedMATT"},
        version = IrkedMLMPlugin.version,
        minClientVersion = "2.6.64",
        iconUrl = "https://chsami.github.io/Microbot-Hub/IrkedMLMPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/IrkedMLMPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class IrkedMLMPlugin extends Plugin {

    public static final String version = "1.0.0";
    static final String PREFIX = "<html>[<font color=#FF8FB1>irk</font>] ";

    private static final String CHAT_SACK_WILL_BE_FULL = "your sack will be full";
    private static final String CHAT_SACK_IS_FULL = "your sack is full";

    @Inject
    private IrkedMLMConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private EventBus eventBus;
    @Inject
    private IrkedMLMOverlay overlay;
    @Inject
    private IrkedMLMAreaOverlay areaOverlay;
    @Inject
    private IrkedMLMScript script;

    private EventBus.Subscriber chatMessageSubscriber;
    private EventBus.Subscriber varbitChangedSubscriber;
    private EventBus.Subscriber gameStateSubscriber;
    private EventBus.Subscriber configSubscriber;
    private MlmLogRepeats logRepeats;

    @Provides
    IrkedMLMConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(IrkedMLMConfig.class);
    }

    /** Package logger for this plugin (script + all sessions inherit it by package name). */
    private static final String LOG_PACKAGE = "net.runelite.client.plugins.microbot.irkedmlm";

    /**
     * Log detail is the package's logger level: Quiet lets warnings through, Normal adds info, Detailed adds
     * every {@code log.debug}. The script and every session log under this package, so one level covers them.
     */
    static Level levelFor(IrkedMLMConfig.LogDetail detail) {
        if (detail == IrkedMLMConfig.LogDetail.QUIET) return Level.WARN;
        if (detail == IrkedMLMConfig.LogDetail.DETAILED) return Level.DEBUG;
        return Level.INFO;
    }

    private static void applyLogLevel(Level level) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LOG_PACKAGE)).setLevel(level);
    }

    @Override
    protected void startUp() {
        applyLogLevel(levelFor(config.logDetail()));
        if (LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext) {
            logRepeats = new MlmLogRepeats();
            logRepeats.start();
            ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).addTurboFilter(logRepeats);
        }
        log.info("Starting Motherlode Mine v{} — sack size: {}", version, config.sackSize());
        if (overlayManager != null) {
            overlayManager.add(overlay);
            overlayManager.add(areaOverlay);
        }
        // Manual registration avoids LambdaConversionException with @Subscribe on some builds.
        chatMessageSubscriber = eventBus.register(ChatMessage.class, this::onChatMessage, 0.0f);
        varbitChangedSubscriber = eventBus.register(VarbitChanged.class, this::onVarbitChanged, 0.0f);
        gameStateSubscriber = eventBus.register(GameStateChanged.class, this::onGameStateChanged, 0.0f);
        configSubscriber = eventBus.register(ConfigChanged.class, this::onConfigChanged, 0.0f);
        script.run();
        log.info("Motherlode Mine startup complete");
    }

    @Override
    protected void shutDown() {
        log.info("Shutting down Motherlode Mine");
        script.shutdown();
        if (chatMessageSubscriber != null) {
            eventBus.unregister(chatMessageSubscriber);
            chatMessageSubscriber = null;
        }
        if (varbitChangedSubscriber != null) {
            eventBus.unregister(varbitChangedSubscriber);
            varbitChangedSubscriber = null;
        }
        if (gameStateSubscriber != null) {
            eventBus.unregister(gameStateSubscriber);
            gameStateSubscriber = null;
        }
        if (configSubscriber != null) {
            eventBus.unregister(configSubscriber);
            configSubscriber = null;
        }
        if (overlayManager != null) {
            overlayManager.remove(overlay);
            overlayManager.remove(areaOverlay);
        }
        log.info("Motherlode Mine shutdown complete");
        applyLogLevel(null); // back to the client's level, so ours does not outlive the plugin
        if (logRepeats != null) {
            logRepeats.flush();
            ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).getTurboFilterList().remove(logRepeats);
            logRepeats.stop();
            logRepeats = null;
        }
    }

    private void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();
        String lower = event.getMessage().toLowerCase();

        // Golden-nugget totals are tracked from the inventory count in the script (monotonic, no
        // reliable chat message fires on sack withdraw), so chat parsing is only used for the
        // sack-full early warning here.
        if (type == ChatMessageType.GAMEMESSAGE) {
            // Two different facts that used to be collapsed into one. "will be full" is only a
            // warning — treating it as "is full" pinned a sack at 100/108 to 108, and that lie then
            // got read back as evidence about capacity. Order matters: the hard fact is checked
            // first, since downgrading it to a warning would be silent.
            if (lower.contains(CHAT_SACK_IS_FULL)) {
                script.onSackIsFullChat();
            } else if (lower.contains(CHAT_SACK_WILL_BE_FULL)) {
                script.onNearlyFullChat();
            }
        }
    }

    /**
     * The sack keeps filling while the player is away — the wiki is explicit that ores waiting to be
     * collected are still there on return. So any break in continuity invalidates everything we
     * believed about the sack, and the script must re-observe rather than resume on stale numbers.
     */
    private void onGameStateChanged(GameStateChanged event) {
        script.onGameStateChanged(event.getGameState());
    }

    /** Log detail takes effect at once; a change is always written, so a log that goes quiet says why. */
    private void onConfigChanged(ConfigChanged event) {
        if (!IrkedMLMConfig.configGroup.equals(event.getGroup()) || !IrkedMLMConfig.logDetail.equals(event.getKey())) return;
        applyLogLevel(Level.INFO);
        log.info("[MLM] Log detail set to {}", config.logDetail());
        applyLogLevel(levelFor(config.logDetail()));
    }

    private void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == IrkedMLMScript.SACK_COUNT_VARBIT) {
            script.onSackVarbitChanged(event.getValue());
        }
    }
}
