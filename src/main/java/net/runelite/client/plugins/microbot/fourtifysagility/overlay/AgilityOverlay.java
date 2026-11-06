package net.runelite.client.plugins.microbot.fourtifysagility.overlay;

import net.runelite.client.plugins.microbot.fourtifysagility.util.overlay.FourtifysOverlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.util.overlay.RuntimeFormat;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public final class AgilityOverlay extends FourtifysOverlay
{
    private final FourtifysAgilityPlugin plugin;
    private final AgilityConfig config;

    @Inject
    public AgilityOverlay(FourtifysAgilityPlugin plugin, AgilityConfig config, Client client, TooltipManager tooltipManager)
    {
        super(plugin, client, tooltipManager);
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (plugin.getStartTime() == null || !config.enableUI())
        {
            return null;
        }
        String elapsed = RuntimeFormat.elapsed(plugin.getStartTime());
        AgilityCourse course = plugin.getActiveCourse();
        String context = course.getDisplayName();
        switch (course)
        {
            case GNOME:
            case APE_ATOLL:
                return render(graphics, "Fourtifys Agility", elapsed, plugin.getStatus(), context, "Laps: " + plugin.getCompletedLaps());
            case AGILITY_PYRAMID:
                return render(graphics, "Fourtifys Agility", elapsed, plugin.getStatus(), context, "Tops: " + plugin.getPyramidTops());
            case WILDERNESS:
                return render(graphics, "Fourtifys Agility", elapsed, plugin.getStatus(), context, "Tickets: " + plugin.getWildernessTickets());
            case BRIMHAVEN:
                return render(graphics, "Fourtifys Agility", elapsed, plugin.getStatus(), "Brimhaven", "",
                    "Tickets: " + plugin.getArenaTickets(), "Vouchers: " + plugin.getArenaVouchers());
            default:
                return render(graphics, "Fourtifys Agility", elapsed, plugin.getStatus(), context, "Marks: " + plugin.getMarksOfGrace());
        }
    }
}
