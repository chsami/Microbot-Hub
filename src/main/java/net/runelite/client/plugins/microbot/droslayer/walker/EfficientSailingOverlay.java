package net.runelite.client.plugins.microbot.droslayer.walker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Compact read-only status panel matching EfficientWalkerStatusOverlay. */
final class EfficientSailingOverlay extends OverlayPanel {
    private final EfficientSailingController navigator;
    private final EfficientSailingConfig config;
    EfficientSailingOverlay(EfficientSailingController navigator, EfficientSailingConfig config) {
        this.navigator = navigator; this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(190, 0));
    }
    @Override public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay() || !navigator.isBusy()) return null;
        EfficientSailingController.View v = navigator.getView();
        String eta = v.etaSeconds >= 0 ? "~" + duration(v.etaSeconds)
                : v.status == EfficientSailingController.Status.SAILING ? "charting..." : "calculating...";
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder().text("EfficientSailing")
                .color(new Color(205,211,218)).build());
        panelComponent.getChildren().add(LineComponent.builder().left(label(v.status))
                .right(duration(Math.max(0, MicrobotSailingFacade.now() - v.startedMillis) / 1000)).build());
        panelComponent.getChildren().add(LineComponent.builder().left("ETA").right(eta).build());
        if(v.status==EfficientSailingController.Status.SAILING) {
            panelComponent.getChildren().add(LineComponent.builder().left(v.navigationMode)
                    .right(String.format(java.util.Locale.ROOT,"%.2f tiles/t",v.speed)).build());
        }
        setDynamicFont(false);
        DroOverlayDropdown.panel(graphics,panelComponent);
        Dimension styledSize = super.render(graphics);
        if (styledSize != null) DroOverlayDropdown.border(graphics,styledSize.width,styledSize.height);
        return styledSize;
    }
    static String duration(long seconds) {
        return seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
    }
    static String label(EfficientSailingController.Status status) {
        switch (status) {
            case WALKING_TO_BANK: return "Walking to bank";
            case BANKING: return "Withdrawing supplies";
            case COMPARING_PORTS: return "Finding nearest port";
            case WALKING_TO_PORT: return "Walking to port";
            case APPROACHING_GANGPLANK: case AT_PORT: return "Approaching gangplank";
            case BOARDING: return "Boarding boat";
            case NEEDS_RECOVERY_SETTINGS: return "Recovery setup";
            case APPROACHING_SHIPWRIGHT: return "To shipwright";
            case RECOVERING_BOAT: return "Retrieving boat";
            case SUMMONING_BOAT: return "Summoning boat";
            case TAKING_HELM: return "Taking helm";
            case SAILING: return "Sailing";
            case STOPPING: return "Stopping";
            case SUSPENDED: return "Paused";
            default: return "Planning journey";
        }
    }
}
