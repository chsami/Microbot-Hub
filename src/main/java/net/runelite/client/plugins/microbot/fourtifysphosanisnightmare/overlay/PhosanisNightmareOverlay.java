package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.overlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.FourtifysPhosanisNightmarePlugin;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.overlay.FourtifysOverlay;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.overlay.RuntimeFormat;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public final class PhosanisNightmareOverlay extends FourtifysOverlay
{
    private final FourtifysPhosanisNightmarePlugin plugin;
    private final PhosanisNightmareConfig config;

    @Inject
    public PhosanisNightmareOverlay(FourtifysPhosanisNightmarePlugin plugin, PhosanisNightmareConfig config, Client client,
        TooltipManager tooltipManager)
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
        return render(graphics, "Fourtifys Phosani's", elapsed, plugin.getStatus(), phaseLine(plugin.getPhase()), "Kills: " + plugin.getKills(), "Per hour: " + plugin.getKillsPerHour(),
            "Loot: " + shortValue(plugin.getLootValue()), "Trips: " + plugin.getTrips());
    }

    /** Her phase, or a dash outside a fight. */
    static String phaseLine(int phase)
    {
        return "Phase: " + (phase > 0 ? Integer.toString(phase) : "-");
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
