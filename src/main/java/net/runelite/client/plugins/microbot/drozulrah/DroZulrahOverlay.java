package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.Locale;

public class DroZulrahOverlay extends OverlayPanel
{
    private static final Color ZULRAH_GREEN = new Color(72, 205, 132);
    private final DroZulrahScript script;

    @Inject
    DroZulrahOverlay(DroZulrahPlugin plugin, DroZulrahScript script)
    {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.setPreferredSize(new Dimension(195, 0));
        panelComponent.getChildren().add(TitleComponent.builder().text("[Dro] Zulrah").color(ZULRAH_GREEN).build());
        panelComponent.getChildren().add(LineComponent.builder().left("State").right(String.valueOf(script.getState())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Status").right(script.getStatus()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Runtime").right(duration(script.getSessionElapsedMs())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Kills").right(String.valueOf(script.getKills())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Trips").right(String.valueOf(script.getTrips())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Deaths").right(String.valueOf(script.getDeaths())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Magic XP/hr").right(number(script.getMagicXpPerHour())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Ranged XP/hr").right(number(script.getRangedXpPerHour())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Loot value").right(number(script.getTotalLootValue()) + " gp").build());
        panelComponent.getChildren().add(LineComponent.builder().left("Est. profit").right(number(script.getEstimatedProfit()) + " gp").build());
        panelComponent.getChildren().add(LineComponent.builder().left("Profit/hr").right(number(script.getEstimatedProfitPerHour()) + " gp").build());
        return super.render(graphics);
    }

    private static String number(long value)
    {
        return String.format(Locale.US, "%,d", value);
    }

    private static String duration(long milliseconds)
    {
        long seconds = milliseconds / 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600L, seconds % 3600L / 60L, seconds % 60L);
    }
}
