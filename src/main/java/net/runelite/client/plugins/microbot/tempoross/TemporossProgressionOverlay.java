package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;

public class TemporossProgressionOverlay extends OverlayPanel {

    private final TemporossPlugin plugin;

    @Inject
    public TemporossProgressionOverlay(TemporossPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_CENTER); // Adjust position as needed
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (TemporossScript.cachedInMinigame) {
            State currentState = TemporossScript.state;
            if (currentState != null) {
                // Set up the panel's visual properties
                panelComponent.setPreferredSize(new Dimension(300, 150));
                panelComponent.setBackgroundColor(new Color(60, 60, 60, 180)); // Semi-transparent background

                // Title component
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text("Tempoross Progression")
                        .color(Color.CYAN)
                        .build());

                // Runtime
                long elapsed = System.currentTimeMillis() - TemporossScript.startTime;
                long seconds = (elapsed / 1000) % 60;
                long minutes = (elapsed / (1000 * 60)) % 60;
                long hours = elapsed / (1000 * 60 * 60);
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Runtime:")
                        .right(String.format("%02d:%02d:%02d", hours, minutes, seconds))
                        .build());

                // Add current state as a line component
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current State:")
                        .right(currentState.name())
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Is completed:")
                        .right(currentState.next != null && currentState == TemporossScript.state ? "No" : "Yes")
                        .build());

                // Add fish count
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Fish count:")
                        .right(String.valueOf(TemporossScript.cachedAllFish))
                        .build());
                // Add cooked fish count
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Cooked fish count:")
                        .right(String.valueOf(TemporossScript.cachedCookedFish))
                        .build());
                // Add raw fish count
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Raw fish count:")
                        .right(String.valueOf(TemporossScript.cachedRawFish))
                        .build());
                // Add total available fish slots
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Total available fish slots:")
                        .right(String.valueOf(TemporossScript.cachedTotalSlots))
                        .build());
                // Is filling
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Is filling:")
                        .right(TemporossScript.isFilling ? "Yes" : "No")
                        .build());
                // Is Fire fighting
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Is Fire fighting:")
                        .right(TemporossScript.isFightingFire ? "Yes" : "No")
                        .build());
                // Get interacting
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Interacting with:")
                        .right(Rs2Player.getInteracting() != null && Rs2Player.getInteracting().getName() != null ? Text.removeTags(Rs2Player.getInteracting().getName()) : "None")
                        .build());

                // Add progression bar
                double progression = calculateProgression(currentState);
                final ProgressBarComponent progressBar = new ProgressBarComponent();
                progressBar.setValue((int) (progression * 100));
                progressBar.setMaximum(100);
                progressBar.setForegroundColor(new Color(37, 196, 37, 255));
                progressBar.setBackgroundColor(new Color(255, 0, 0, 255));
                progressBar.setPreferredSize(new Dimension(280, 30));
                progressBar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.PERCENTAGE);

                panelComponent.getChildren().add(progressBar);
            }
        }
        return super.render(graphics);
    }

    private double calculateProgression(State state) {
        int cooked = TemporossScript.cachedCookedFish;
        int all = TemporossScript.cachedAllFish;
        int raw = TemporossScript.cachedRawFish;
        int slots = TemporossScript.cachedTotalSlots;
        boolean solo = TemporossScript.temporossConfig != null && TemporossScript.temporossConfig.solo();

        switch (state) {
            case ATTACK_TEMPOROSS:
                return Math.min(TemporossScript.ENERGY / 94.0, 1.0);
            case SECOND_FILL:
                return 1.0 - Math.min((double) cooked / (solo ? 19 : slots), 1.0);
            case INITIAL_FILL:
                return 1.0 - Math.min((double) cooked / (solo ? 17 : slots), 1.0);
            case THIRD_COOK:
                return Math.min((double) cooked / (solo ? 19 : Math.max(all, 1)), 1.0);
            case THIRD_CATCH:
                return Math.min((double) all / (solo ? 19 : slots), 1.0);
            case EMERGENCY_FILL:
                return all == 0 ? 1.0 : 0.0;
            case SECOND_COOK:
                return Math.min((double) cooked / (solo ? 17 : Math.max(all, 1)), 1.0);
            case SECOND_CATCH:
                return Math.min((double) all / (solo ? 17 : slots), 1.0);
            case INITIAL_COOK:
                return Math.min((double) cooked / Math.max(all, 1), 1.0);
            case INITIAL_CATCH:
                return Math.max(
                        Math.min((double) raw / 7.0, 1.0),
                        Math.min((double) all / 10.0, 1.0));
            default:
                return 0.0;
        }
    }

}
