package net.runelite.client.plugins.microbot.microhunter;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.microhunter.scripts.AutoChinScript;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AutoHunterOverlay extends OverlayPanel {

    private final AutoHunterPlugin plugin;

    @Inject
    AutoHunterOverlay(AutoHunterPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(240, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro Auto Hunter " + AutoHunterPlugin.version)
                    .color(Color.GREEN)
                    .build());

            AutoChinScript script = plugin.getAutoChinScript();
            addLine("State", script.getCurrentState().name());
            addLine("Next", script.getNextAction());
            addLine("Active traps", script.getActiveTrapCount() + "/" + script.getTrapLimit());
            addLine("Owned slots", script.getManagedTrapCount() + "/" + script.getTrapLimit());
            addLine("Catches / resets", script.getCatches() + " / " + script.getResets());
            addLine("Free slots", String.valueOf(Rs2Inventory.emptySlotCount()));
            addLine("Best spawn", script.getSpawnSummary());
            addLine("Layout center", String.valueOf(script.getLayoutCenter()));
            if (!script.getStopReason().isEmpty()) addLine("Stopped", script.getStopReason());


        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private void addLine(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
    }
}
