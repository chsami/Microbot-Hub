package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class ArceuusLibraryOverlay extends OverlayPanel
{
    private final ArceuusLibraryPlugin plugin;

    @Inject
    ArceuusLibraryOverlay(ArceuusLibraryPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try
        {
            panelComponent.setPreferredSize(new Dimension(220, 0));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Arceuus Library")
                    .color(Color.ORANGE)
                    .build());

            ArceuusLibraryScript script = plugin.getScript();
            if (script == null)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status").right("not started").build());
                return super.render(graphics);
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State").right(String.valueOf(script.getState())).build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Solver").right(script.getSolverState()).build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Wanted").right(script.getWantedBookLabel()).build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Customer").right(script.getCurrentCustomerLabel()).build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Candidates").right(String.valueOf(script.getCandidateCount())).build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Held").right(script.distinctBooksHeldCount() + " / 16").build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Sweeps").right(String.valueOf(script.getSweepSearchesThisTrip())).build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Delivered").right(String.valueOf(script.getDelivered())).build());
        }
        catch (Exception ex)
        {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
