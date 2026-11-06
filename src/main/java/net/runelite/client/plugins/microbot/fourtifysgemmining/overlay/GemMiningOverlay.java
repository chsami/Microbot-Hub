package net.runelite.client.plugins.microbot.fourtifysgemmining.overlay;

import net.runelite.client.plugins.microbot.fourtifysgemmining.util.overlay.FourtifysOverlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.fourtifysgemmining.FourtifysGemMiningPlugin;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.overlay.RuntimeFormat;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public final class GemMiningOverlay extends FourtifysOverlay
{
    private final FourtifysGemMiningPlugin plugin;
    private final GemMiningConfig config;

    @Inject
    public GemMiningOverlay(FourtifysGemMiningPlugin plugin, GemMiningConfig config, Client client, TooltipManager tooltipManager)
    {
        super(plugin, client, tooltipManager);
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (plugin.getStartTime() == null || !config.showOverlay())
        {
            return null;
        }
        String elapsed = RuntimeFormat.elapsed(plugin.getStartTime());
        return render(graphics, "Fourtifys Gem Mining", elapsed, plugin.getStatus(), plugin.getActiveMineName(), "Gems: " + plugin.getGemsMined());
    }
}
