package net.runelite.client.plugins.microbot.pitfallhunter;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class PitfallHunterOverlay extends OverlayPanel
{
    private final PitfallHunterScript script;

    @Inject
    PitfallHunterOverlay(PitfallHunterPlugin plugin, PitfallHunterScript script)
    {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(275, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Pitfall Hunter v" + PitfallHunterPlugin.version)
                    .color(Color.GREEN)
                    .build());

            addLine("State", script.getStateDisplay() + " (" + formatMillis(script.getStateAgeMillis()) + ")");
            addLine("Action", script.getNextStepDisplay());
            addLine("NPC", script.getSelectedNpcDisplay());
            addLine("Pit", script.getSelectedPitDisplay() + " / " + script.getSelectedPitStateDisplay());
            addLine("Choice", script.getLastPitQuery());
            addLine("Lure", script.getLastLureEvidence());

            String failure = script.getLastFailure();
            if (failure != null && !failure.isEmpty()) {
                addLine("Stop", failure);
            }
        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right == null ? "" : right)
                .build());
    }

    private String formatMillis(long millis)
    {
        return String.format("%.1fs", millis / 1000.0);
    }
}
