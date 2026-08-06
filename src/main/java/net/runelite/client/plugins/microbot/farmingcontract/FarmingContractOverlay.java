package net.runelite.client.plugins.microbot.farmingcontract;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.awt.*;

@Slf4j
public class FarmingContractOverlay extends OverlayPanel {

    @Inject
    FarmingContractOverlay(FarmingContractPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 0));
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Farming Contract")
                .color(Color.GREEN)
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(FarmingContractPlugin.getStatus())
                .build());

            String contractName = FarmingContractScript.getContractName();
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Contract:")
                .right(contractName != null ? contractName : "None")
                .build());

            if (Microbot.isLoggedIn()) {
                int level = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
                String tier = level >= 85 ? "Hard" : level >= 65 ? "Medium" : level >= 45 ? "Easy" : "N/A";
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Farming:")
                    .right(level + " (" + tier + ")")
                    .build());
            }
        } catch (Exception ex) {
            log.error("Overlay render error", ex);
        }
        return super.render(graphics);
    }
}
