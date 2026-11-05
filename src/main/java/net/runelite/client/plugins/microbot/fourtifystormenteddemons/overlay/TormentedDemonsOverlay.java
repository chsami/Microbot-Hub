package net.runelite.client.plugins.microbot.fourtifystormenteddemons.overlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.FourtifysTormentedDemonsPlugin;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.overlay.FourtifysOverlay;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.overlay.RuntimeFormat;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public final class TormentedDemonsOverlay extends FourtifysOverlay
{
    private final FourtifysTormentedDemonsPlugin plugin;
    private final TormentedDemonsConfig config;

    @Inject
    public TormentedDemonsOverlay(FourtifysTormentedDemonsPlugin plugin, TormentedDemonsConfig config, Client client, TooltipManager tooltipManager)
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
        return render(graphics, "Fourtifys Demons", elapsed, plugin.getStatus(),
            "Kills: " + plugin.getKills(), "Per hour: " + plugin.getKillsPerHour(),
            "Loot: " + shortValue(plugin.getLootValue()), "Uniques: " + plugin.getUniques());
    }

    /** Coins as 950, 12.5K or 3.2M. */
    public static String shortValue(long coins)
    {
        if (coins >= 1_000_000)
        {
            return String.format("%.1fM", coins / 1_000_000.0);
        }
        if (coins >= 10_000)
        {
            return String.format("%.1fK", coins / 1_000.0);
        }
        return Long.toString(coins);
    }
}
