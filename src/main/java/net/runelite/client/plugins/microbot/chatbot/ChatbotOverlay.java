package net.runelite.client.plugins.microbot.chatbot;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class ChatbotOverlay extends OverlayPanel {
    private final ChatbotScript chatbotScript;

    @Inject
    ChatbotOverlay(ChatbotPlugin plugin, ChatbotScript chatbotScript) {
        super(plugin);
        this.chatbotScript = chatbotScript;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(250, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Chatbot " + ChatbotPlugin.version)
                .color(new Color(110, 200, 230))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status: " + chatbotScript.getLastStatus())
                .leftColor(new Color(220, 225, 230))
                .build());
        addLine("Model", chatbotScript.getEffectiveModel());
        addLine("Received", String.valueOf(chatbotScript.getMessagesReceived()));
        addLine("Sent", String.valueOf(chatbotScript.getResponsesSent()));
        addLine("Previews", String.valueOf(chatbotScript.getPreviewCount()));
        addLine("API errors", String.valueOf(chatbotScript.getApiErrors()));
        long seconds = chatbotScript.getCooldownRemainingSeconds();
        addLine("Next request", seconds > 0 ? formatWait(seconds) : chatbotScript.isRequestPaused() ? "Paused" : "Ready");

        return super.render(graphics);
    }

    private void addLine(String label, String value) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(label)
                .right(value == null ? "—" : value)
                .build());
    }

    private String formatWait(long seconds) {
        if (seconds >= 3600) {
            return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
        }
        return seconds >= 60 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds + "s";
    }
}
