package net.runelite.client.plugins.microbot.valetotems;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.models.GameSession;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ValeTotemOverlay extends OverlayPanel {
    private final ValeTotemPlugin plugin;
    
    @Inject
    ValeTotemOverlay(ValeTotemPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(280, 400));
            
            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Vale Totems Bot v" + ValeTotemPlugin.version)
                    .color(Color.CYAN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            // Bot status
            ValeTotemScript script = plugin.getScript();
            boolean isRunning = script != null && script.isRunning();
            
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(isRunning ? "Running" : "Stopped")
                    .rightColor(isRunning ? Color.GREEN : Color.RED)
                    .build());

            // Game session info
            if (isRunning && script.getGameSession() != null) {
                GameSession session = script.getGameSession();
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("State:")
                        .right(session.getCurrentState().name())
                        .rightColor(Color.CYAN)
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Round:")
                        .right(String.valueOf(session.getCurrentRound()))
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Totem:")
                        .right(session.getCurrentTotem() != null ? 
                               session.getCurrentTotem().name() : "None")
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Completed:")
                        .right(session.getCompletedTotemsThisRound() + "/5")
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder().build());
                
                // Performance stats
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Session Time:")
                        .right(formatTime(session.getSessionDuration()))
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Total Rounds:")
                        .right(String.valueOf(session.getTotalRounds()))
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Offerings:")
                        .right(String.valueOf(session.getTotalOfferingsCollected()))
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Avg Totem Time:")
                        .right(formatTime((long) session.getAverageTotemCompletionTime()))
                        .build());
                
                // Error count
                int errorCount = session.getErrorMessages().size();
                if (errorCount > 0) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Errors:")
                            .right(String.valueOf(errorCount))
                            .rightColor(Color.RED)
                            .build());
                }
            }

            panelComponent.getChildren().add(LineComponent.builder().build());
            
            // General Microbot status
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Microbot:")
                    .right(Microbot.status)
                    .build());

        } catch(Exception ex) {
            System.err.println("Error rendering overlay: " + ex.getMessage());
        }
        return super.render(graphics);
    }
    
    /**
     * Format time in milliseconds to a readable string
     * @param timeMs time in milliseconds
     * @return formatted time string
     */
    private String formatTime(long timeMs) {
        if (timeMs <= 0) return "0s";
        
        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
