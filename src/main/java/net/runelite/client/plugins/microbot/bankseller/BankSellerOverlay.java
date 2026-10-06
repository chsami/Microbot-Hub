package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class BankSellerOverlay extends OverlayPanel {
    private final BankSellerConfig config;

    @Inject
    BankSellerOverlay(BankSellerPlugin plugin, BankSellerConfig config) {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        panelComponent.setPreferredSize(new Dimension(180, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            panelComponent.getChildren().clear();
            return null;
        }
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Bank Seller")
                .color(Color.CYAN)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Version")
                .right(BankSellerPlugin.version)
                .build());
        return super.render(graphics);
    }
}
