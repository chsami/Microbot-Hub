package net.runelite.client.plugins.microbot.sailing;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.sailing.features.salvaging.SalvagingScript;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.Comparator;

public class SailingOverlay extends OverlayPanel {

    private final Client client;
    private final SailingConfig config;
    private final SalvagingScript salvagingScript;

    @Inject
    SailingOverlay(MSailingPlugin plugin, Client client, SailingConfig config, SalvagingScript salvagingScript) {
        super(plugin);
        this.client = client;
        this.config = config;
        this.salvagingScript = salvagingScript;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(220, 300));

            addTitle();
            addSeparator();
            addStatus();

            if (config.salvaging()) {
                addSeparator();
                addSailingInfo();
                addSeparator();
                addShipwreckInfo();
                addSeparator();
                addInventoryInfo();
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    private void addTitle() {
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Sailing v" + MSailingPlugin.version)
                .color(new Color(0, 150, 200))
                .build());
    }

    private void addSeparator() {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }

    private void addStatus() {
        Color statusColor = Microbot.isLoggedIn() ? Color.GREEN : Color.RED;
        String status = config.salvaging() ? "Running" : "Stopped";

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(status)
                .rightColor(statusColor)
                .build());
    }

    private void addSailingInfo() {
        if (!Microbot.isLoggedIn()) return;

        int sailingLevel = client.getBoostedSkillLevel(Skill.SAILING);
        int baseLevel = client.getRealSkillLevel(Skill.SAILING);

        Color levelColor = sailingLevel > baseLevel ? Color.GREEN :
                          sailingLevel < baseLevel ? Color.RED : Color.WHITE;

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Sailing Level:")
                .right(String.valueOf(sailingLevel))
                .rightColor(levelColor)
                .build());
    }

    private void addShipwreckInfo() {
        if (!Microbot.isLoggedIn()) return;

        try {
            var activeWrecks = salvagingScript.getActiveWrecks();
            var inactiveWrecks = salvagingScript.getInactiveWrecks();

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Active Wrecks:")
                    .right(String.valueOf(activeWrecks.size()))
                    .rightColor(activeWrecks.isEmpty() ? Color.RED : Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Inactive Wrecks:")
                    .right(String.valueOf(inactiveWrecks.size()))
                    .rightColor(Color.GRAY)
                    .build());

            if (!activeWrecks.isEmpty()) {
                var player = new Rs2PlayerModel();
                var playerLocation = player.getWorldLocation();

                if (playerLocation != null) {
                    var nearestWreck = activeWrecks.stream()
                            .min(Comparator.comparingInt(w -> playerLocation.distanceTo(w.getWorldLocation())))
                            .orElse(null);

                    if (nearestWreck != null) {
                        int distance = playerLocation.distanceTo(nearestWreck.getWorldLocation());
                        Color distanceColor = distance <= 15 ? Color.GREEN : Color.ORANGE;

                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Nearest Wreck:")
                                .right(distance + " tiles")
                                .rightColor(distanceColor)
                                .build());
                    }
                }
            }
        } catch (Exception ex) {
            // Ignore errors during wreck query
        }
    }

    private void addInventoryInfo() {
        if (!Microbot.isLoggedIn()) return;

        int inventoryCount = Rs2Inventory.count();
        int salvageCount = Rs2Inventory.count("salvage");

        Color invColor = inventoryCount >= 24 ? Color.ORANGE :
                        inventoryCount == 28 ? Color.RED : Color.WHITE;

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Inventory:")
                .right(inventoryCount + "/28")
                .rightColor(invColor)
                .build());

        if (salvageCount > 0) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Salvage Items:")
                    .right(String.valueOf(salvageCount))
                    .rightColor(Color.CYAN)
                    .build());
        }

        if (config.enableAlching()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Alching:")
                    .right("Enabled")
                    .rightColor(Color.YELLOW)
                    .build());
        }
    }
}
