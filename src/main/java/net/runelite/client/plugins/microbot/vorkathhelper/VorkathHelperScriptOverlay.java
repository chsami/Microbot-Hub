package net.runelite.client.plugins.microbot.vorkathhelper;

import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class VorkathHelperScriptOverlay extends Overlay {

    private final VorkathHelperConfig config;
    private final VorkathHelperScript script;
    private final VorkathHelperPlugin plugin;

    @Inject
    private SpriteManager spriteManager;


    @Inject
    public VorkathHelperScriptOverlay(VorkathHelperScript script, VorkathHelperConfig config, VorkathHelperPlugin plugin) {

        this.script = script;
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        if (!shouldRender())
            return null;

        if(config.vorkathSpecialOverlay())
        {
            drawVorkathSpecialCounter(graphics, script.getVorkathVars().getVorkath());
        }
        if(config.zombieSpawnOverlay())
        {
            drawZombieSpawnOverlay(graphics, script.getVorkathVars().getZombieSpawn());
        }
        if(config.acidOverlay())
        {
            drawAcidOverlay(graphics);
        }
        if(config.fireBallOverlay())
        {
            drawFireBallOverlay(graphics);
        }
        return null;
    }

    private boolean shouldRender() {
        return Microbot.getClient().getLocalPlayer() != null
                && script.getVorkathVars().getVorkath() != null;
    }

    private void drawPathToZombie(Graphics2D graphics,NPC npc) {
        Player player =
                Microbot.getClient().getLocalPlayer();

        if (player == null)
            return;

        Point playerCanvas =
                Perspective.localToCanvas(
                        Microbot.getClient(),
                        player.getLocalLocation(),
                        player.getWorldLocation().getPlane()
                );

        Point npcCanvas =
                Perspective.localToCanvas(
                        Microbot.getClient(),
                        npc.getLocalLocation(),
                        npc.getWorldLocation().getPlane()
                );

        if (playerCanvas == null || npcCanvas == null)
            return;

        graphics.setColor(config.zombieLineColor());
        graphics.setStroke(new BasicStroke(3));

        graphics.drawLine(
                playerCanvas.getX(),
                playerCanvas.getY(),
                npcCanvas.getX(),
                npcCanvas.getY()
        );
    }

    private void drawAcidOverlay(Graphics2D graphics) {
        for (WorldPoint acidTile : script.getVorkathVars().getAcidTiles()) {
            LocalPoint lp = LocalPoint.fromWorld(
                    Microbot.getClient(),
                    acidTile
            );

            if (lp == null)
                continue;

            Polygon poly = Perspective.getCanvasTilePoly(
                    Microbot.getClient(),
                    lp
            );

            if (poly == null)
                continue;

            graphics.setColor(config.acidFillColor());
            graphics.fill(poly);

            graphics.setColor(config.acidBorderColor());
            graphics.setStroke(new BasicStroke(3));
            graphics.draw(poly);
        }

    }

    private void drawFireBallOverlay(Graphics2D graphics) {
        Projectile fireball = script.getVorkathVars().getFireballProjectile();

        if (fireball != null) {
            int ticksRemaining = script.getFireballTicksRemaining();
            LocalPoint impactPoint = fireball.getTarget();

            if (impactPoint != null) {
                WorldPoint impactTile =
                        WorldPoint.fromLocal(
                                Microbot.getClient(),
                                impactPoint
                        );
                Point textPoint =
                        Perspective.getCanvasTextLocation(
                                Microbot.getClient(),
                                graphics,
                                impactPoint,
                                ticksRemaining + "s",
                                0
                        );


                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        WorldPoint tile =
                                new WorldPoint(
                                        impactTile.getX() + dx,
                                        impactTile.getY() + dy,
                                        impactTile.getPlane()
                                );

                        Polygon poly =
                                Perspective.getCanvasTilePoly(
                                        Microbot.getClient(),
                                        Objects.requireNonNull(LocalPoint.fromWorld(
                                                Microbot.getClient(),
                                                tile
                                        ))
                                );

                        if (poly == null)
                            continue;

                        graphics.setColor(config.fireballDangerColor());
                        graphics.fill(poly);

                        graphics.setColor(config.fireballDangerColor());
                        graphics.draw(poly);
                    }
                }
                if (textPoint != null) {
                    graphics.setColor(Color.WHITE);
                    Font original = graphics.getFont();
                    graphics.setFont(original.deriveFont(Font.BOLD, graphics.getFont().getSize() + 2));
                    graphics.drawString(
                            ticksRemaining + "s",
                            textPoint.getX() + 1,
                            textPoint.getY() + 1
                    );
                }
            }
        }
    }

    private void drawZombieSpawnOverlay(Graphics2D graphics, Rs2NpcModel zombieSpawn) {
        if (zombieSpawn == null)
            return;
        NPC npc = zombieSpawn.getNpc();

        if (npc == null)
            return;
        Shape hull = npc.getConvexHull();

        if (hull != null) {
            graphics.setColor(config.zombieOutlineColor());
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(hull);
        }
        drawPathToZombie(graphics, npc);
        // Highlight tile
        LocalPoint lp = npc.getLocalLocation();

        if (lp != null) {
            Polygon poly =
                    Perspective.getCanvasTilePoly(
                            Microbot.getClient(),
                            lp
                    );

            if (poly != null) {
                graphics.setColor(
                        config.zombieLineColor()
                );
                graphics.fillPolygon(poly);

                graphics.setColor(config.zombieTileColor());
                graphics.setStroke(new BasicStroke(2));
                graphics.drawPolygon(poly);
            }
        }

        Point textPoint =
                npc.getCanvasTextLocation(
                        graphics,
                        "ZOMBIE",
                        100
                );

        if (textPoint != null) {
            graphics.setColor(config.zombieOutlineColor());
            graphics.drawString(
                    "ZOMBIE",
                    textPoint.getX() + 1,
                    textPoint.getY() + 1
            );

        }


    }

    private void drawVorkathSpecialCounter(Graphics2D graphics, Rs2NpcModel vorkath) {

        Point point = vorkath.getCanvasTextLocation(
                graphics,
                "",
                200
        );
        if (point == null) return;
        int size = 30;

        int x = point.getX() - (size / 2);
        int y = point.getY() - (size / 2);

        int attacks = script.getVorkathVars().getAttackCount();

        double progress = attacks / 6.0;

        String text = String.valueOf(attacks);

        Font original = graphics.getFont();
        graphics.setFont(original.deriveFont(Font.BOLD, 14f));

        FontMetrics fm = graphics.getFontMetrics();

        int textX = x + (size - fm.stringWidth(text)) / 2;
        int textY = y + 32;

        graphics.setColor(Color.WHITE);
        graphics.drawString(text, textX + 1, textY + 4);

        graphics.setColor(Color.WHITE);
        graphics.drawString(text, textX, textY);

        BufferedImage icon;
        if (script.getVorkathVars().getNextSpecial() == VorkathHelperScript.VorkathSpecial.ACID_SPEC) {
            icon = spriteManager.getSprite(
                    SpriteID.SPELL_EARTH_SURGE,
                    0
            );
        } else {
            icon = spriteManager.getSprite(
                    SpriteID.SPELL_CRUMBLE_UNDEAD,
                    0
            );
        }


        int bgPadding = 4;

        graphics.setStroke(new BasicStroke(2));

        graphics.setColor(new Color(33, 33, 33, 220));
        graphics.fillOval(
                x - bgPadding,
                y - bgPadding,
                size + bgPadding * 2,
                size + bgPadding * 2
        );

        graphics.setColor(Color.DARK_GRAY);
        graphics.drawOval(
                x - bgPadding,
                y - bgPadding,
                size + bgPadding * 2,
                size + bgPadding * 2
        );

        Arc2D.Double arc = new Arc2D.Double(
                x - bgPadding,
                y - bgPadding,
                size + bgPadding * 2,
                size + bgPadding * 2,
                90.0,
                -360.0 * progress,
                Arc2D.OPEN
        );

        graphics.setColor(Color.YELLOW);
        graphics.draw(arc);

        int iconSize = 18;

        int iconX = x + (size - iconSize) / 2;
        int iconY = y + (size - iconSize) / 2 + 4;

        if (icon != null) {
            graphics.drawImage(
                    icon,
                    iconX,
                    iconY,
                    iconSize,
                    iconSize,
                    null
            );
        }

    }

}