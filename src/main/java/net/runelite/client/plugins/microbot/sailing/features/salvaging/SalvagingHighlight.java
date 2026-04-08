package net.runelite.client.plugins.microbot.sailing.features.salvaging;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.sailing.SailingConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;

/**
 * Renders the same salvage-area outline as the RuneLite Plugin Hub &quot;Sailing&quot; plugin
 * ({@code com.duckblade.osrs.sailing.features.salvaging.SalvagingHighlight}): {@link Perspective#getCanvasTileAreaPoly}
 * with a fixed tile radius. Wreck lists come from {@link SalvagingScript} (top-level scene scan + spawn sync).
 */
@Slf4j
public class SalvagingHighlight extends Overlay {

    private static final int SIZE_SALVAGEABLE_AREA = 15;

    private final Client client;
    private final SailingConfig config;
    private final SalvagingScript salvagingScript;

    @Inject
    public SalvagingHighlight(Client client, SailingConfig config, SalvagingScript salvagingScript) {
        this.client = client;
        this.config = config;
        this.salvagingScript = salvagingScript;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.salvagingHighlight()) {
            return null;
        }

        int sailingLevel = safeBoostedSailingLevel(client);

        for (var wreck : salvagingScript.getActiveWrecks()) {
            Integer levelReq = SalvageObjectIds.SALVAGE_LEVEL_REQ.get(wreck.getId());
            if (levelReq == null) continue;

            boolean hasReq = sailingLevel >= levelReq;
            if ((hasReq && config.salvagingHighlightActiveWrecks()) ||
                    (!hasReq && config.salvagingHighlightHighLevelWrecks())) {
                Color colour = hasReq ? config.salvagingHighlightActiveWrecksColour() : config.salvagingHighLevelWrecksColour();
                renderWreck(graphics, wreck, colour);
            }
        }

        for (var wreck : salvagingScript.getInactiveWrecks()) {
            Integer levelReq = SalvageObjectIds.STUMP_LEVEL_REQ.get(wreck.getId());
            if (levelReq == null) continue;

            boolean hasReq = sailingLevel >= levelReq;
            if ((hasReq && config.salvagingHighlightInactiveWrecks()) ||
                    (!hasReq && config.salvagingHighlightHighLevelWrecks())) {
                Color colour = hasReq ? config.salvagingHighlightInactiveWrecksColour() : config.salvagingHighLevelWrecksColour();
                renderWreck(graphics, wreck, colour);
            }
        }

        return null;
    }

    /** When the client&apos;s skill level buffer is too short, {@link Client#getBoostedSkillLevel(Skill)} can throw. */
    private static int safeBoostedSailingLevel(Client client) {
        try {
            return client.getBoostedSkillLevel(Skill.SAILING);
        } catch (RuntimeException ex) {
            return 99;
        }
    }

    private void renderWreck(Graphics2D graphics, Rs2TileObjectModel wreck, Color colour) {
        Polygon poly = Perspective.getCanvasTileAreaPoly(client, wreck.getLocalLocation(), SIZE_SALVAGEABLE_AREA);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, colour);
        }
    }
}
