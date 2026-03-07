package net.runelite.client.plugins.microbot.chatbot;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

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
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD16 Chatbot V" + ChatbotPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(chatbotScript.getLastStatus())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Received:")
                    .right(String.valueOf(chatbotScript.getMessagesReceived()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Responded:")
                    .right(String.valueOf(chatbotScript.getResponsesSent()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Microbot:")
                    .right(Microbot.status)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
