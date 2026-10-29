package net.runelite.client.plugins.microbot.aiomagic;

import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

public class AIOMagicOverlay extends OverlayPanel {
    private final AIOMagicPlugin plugin;
    private final SkillIconManager skillIconManager;

    @Inject
    AIOMagicOverlay(net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin plugin, SkillIconManager skillIconManager) {
        super(plugin);
        this.plugin = plugin;
        this.skillIconManager = skillIconManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("AIO Magic V" + AIOMagicPlugin.version)
                    .color(ColorUtil.fromHex("0077B6"))
                    .build());
            
            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

            long xpPerHour = plugin.getMagicXpPerHour();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP gained:")
                    .right(String.format("%,d", plugin.getMagicXpGained()))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/hour:")
                    .right(xpPerHour > 0 ? String.format("%,d", xpPerHour) : "--")
                    .build());
            addNextLevelLines(xpPerHour);

        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    private void addNextLevelLines(long xpPerHour) {
        int level = Microbot.getClient().getRealSkillLevel(Skill.MAGIC);
        boolean maxed = level >= Experience.MAX_REAL_LEVEL;
        long xpToLevel = maxed ? 0 : Experience.getXpForLevel(level + 1) - Microbot.getClient().getSkillExperience(Skill.MAGIC);
        long secondsToLevel = xpPerHour > 0 ? xpToLevel * 3600L / xpPerHour : 0;
        LineComponent nextLevel = LineComponent.builder()
                .left(Skill.MAGIC.getName() + " \u2192 " + (maxed ? "--" : String.valueOf(level + 1)))
                .right(maxed || xpPerHour <= 0 ? "--" : String.format("%dh %02dm", secondsToLevel / 3600, secondsToLevel % 3600 / 60))
                .build();
        BufferedImage icon = skillIconManager.getSkillImage(Skill.MAGIC, true);
        panelComponent.getChildren().add(SplitComponent.builder()
                .first(new ImageComponent(icon))
                .second(nextLevel)
                .orientation(ComponentOrientation.HORIZONTAL)
                .gap(new Point(2, 0))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP to level:")
                .right(maxed ? "--" : String.format("%,d", xpToLevel))
                .build());
    }
}
