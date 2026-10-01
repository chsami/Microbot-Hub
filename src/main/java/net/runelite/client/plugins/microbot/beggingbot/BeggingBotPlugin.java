package net.runelite.client.plugins.microbot.beggingbot;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Stands at the Grand Exchange asking for gp, accepts incoming trades and hops worlds when it is not getting any.
 * It never puts anything into a trade: it only accepts what the other player offers.
 */
@PluginDescriptor(
    name = PluginConstants.DEFAULT_PREFIX + "Begging Bot",
    description = "Walks to the GE, begs for gp, accepts trades (never offers anything) and hops worlds when quiet.",
    tags = {"begging", "ge", "trade", "gp"},
    authors = {"BenMT"},
    version = BeggingBotPlugin.version,
    minClientVersion = "2.6.24",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class BeggingBotPlugin extends Plugin
{
    static final String version = "1.0.3";
    private static final String TRADE_REQUEST_SUFFIX = " wishes to trade with you.";

    @Inject private BeggingBotConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private BeggingBotOverlay overlay;

    private volatile BeggingBotScript script;

    BeggingBotScript script()
    {
        return script;
    }

    @Provides
    BeggingBotConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BeggingBotConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
        script = new BeggingBotScript(config);
        script.run();
    }

    @Override
    protected void shutDown()
    {
        BeggingBotScript running = script;
        script = null;
        overlayManager.remove(overlay);
        if (running != null) running.shutdown();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        BeggingBotScript running = script;
        if (running == null) return;
        String message = Text.removeTags(event.getMessage()).replace('\u00A0', ' ');
        if (event.getType() == ChatMessageType.TRADEREQ && message.endsWith(TRADE_REQUEST_SUFFIX))
        {
            running.onTradeRequest(message.substring(0, message.length() - TRADE_REQUEST_SUFFIX.length()));
        }
        else if (event.getType() == ChatMessageType.GAMEMESSAGE && message.equals("Accepted trade."))
        {
            running.onTradeCompleted();
        }
    }
}
