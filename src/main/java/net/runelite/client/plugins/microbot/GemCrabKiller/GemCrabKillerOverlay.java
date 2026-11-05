package net.runelite.client.plugins.microbot.GemCrabKiller;

import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

public class GemCrabKillerOverlay extends OverlayPanel {
    private static final Color TITLE_COLOR = Color.decode("#a4ffff");
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 150);
    private static final Color NORMAL_COLOR = Color.WHITE;
    private static final Color WARNING_COLOR = Color.YELLOW;
    private static final Color DANGER_COLOR = Color.RED;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    private final GemCrabKillerPlugin plugin;
    private final ImageComponent image;
    private final SkillIconManager skillIconManager;

    @Inject
    GemCrabKillerOverlay(GemCrabKillerPlugin plugin, SkillIconManager skillIconManager) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
        image = new ImageComponent(getCrabImageFromResources());
        this.plugin = plugin;
        this.skillIconManager = skillIconManager;
    }

    private BufferedImage getCrabImageFromResources() {
        try {
            var img = ImageUtil.loadImageResource(GemCrabKillerPlugin.class, "GemStoneCrab.png");
            return ImageUtil.resizeImage(img, 24, 24, true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(190, 300));
            panelComponent.setBackgroundColor(BACKGROUND_COLOR);
            // Title
            final LineComponent title = LineComponent.builder()
                    .left(" TaF's Gemstone Crab Killer")
                    .leftColor(Color.white)
                    .build();
            final SplitComponent iconTitleSplit = SplitComponent.builder()
                    .first(image)
                    .second(title)
                    .orientation(ComponentOrientation.HORIZONTAL)
                    .gap(new Point(2, 0))
                    .build();
            panelComponent.getChildren().add(iconTitleSplit);
            // Script running time
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .rightColor(NORMAL_COLOR)
                    .build());
            // State information
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(plugin.gemCrabKillerScript.gemCrabKillerState.name())
                    .rightColor(getStateColor(plugin.gemCrabKillerScript.gemCrabKillerState))
                    .build());
            // Total kills
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total Kills:")
                    .right(String.valueOf(plugin.gemCrabKillerScript.totalCrabKills))
                    .rightColor(NORMAL_COLOR)
                    .build());

            // XP information
            var xpGained = plugin.getXpGained();
            var xpPerHour = plugin.getXpPerHour();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP Gained:")
                    .right(formatNumber(xpGained))
                    .rightColor(NORMAL_COLOR)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/Hour:")
                    .right(formatNumber(xpPerHour))
                    .rightColor(xpPerHour > 0 ? SUCCESS_COLOR : NORMAL_COLOR)
                    .build());

            addNextLevelLines();

            // Footer with version
            panelComponent.getChildren().add(LineComponent.builder()
                    .right("v" + plugin.version)
                    .rightColor(new Color(160, 160, 160))
                    .build());

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private void addNextLevelLines() {
        Skill skill = plugin.getTrainedSkill();
        int level = skill == null ? 0 : Microbot.getClient().getRealSkillLevel(skill);
        if (skill == null || level >= Experience.MAX_REAL_LEVEL) {
            String nextLevel = skill == null ? "--" : "Maxed";
            panelComponent.getChildren().add(LineComponent.builder().left(skill == null ? "Next Level:" : skill.getName() + ":").right(nextLevel).rightColor(NORMAL_COLOR).build());
            panelComponent.getChildren().add(LineComponent.builder().left("XP To Level:").right(nextLevel).rightColor(NORMAL_COLOR).build());
            return;
        }
        long xpToLevel = Experience.getXpForLevel(level + 1) - Microbot.getClient().getSkillExperience(skill);
        long xpPerHour = plugin.getSkillXpPerHour(skill);
        LineComponent nextLevel = LineComponent.builder()
                .left(skill.getName() + " " + (level + 1) + " in:")
                .leftColor(NORMAL_COLOR)
                .right(xpPerHour > 0 ? formatDuration(xpToLevel * 3600L / xpPerHour) : "--")
                .rightColor(NORMAL_COLOR)
                .build();
        BufferedImage icon = skillIconManager.getSkillImage(skill, true);
        panelComponent.getChildren().add(SplitComponent.builder()
                .first(new ImageComponent(icon))
                .second(nextLevel)
                .orientation(ComponentOrientation.HORIZONTAL)
                .gap(new Point(2, 0))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP To Level:")
                .right(formatNumber(xpToLevel))
                .rightColor(NORMAL_COLOR)
                .build());
    }

    private String formatDuration(long seconds) {
        return String.format("%dh %02dm", seconds / 3600, seconds % 3600 / 60);
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private Color getStateColor(GemCrabKillerState state) {
        if (state == null) return NORMAL_COLOR;

        switch (state) {
            case FIGHTING:
                return DANGER_COLOR;
            case WALKING:
                return WARNING_COLOR;
            case BANKING:
                return NORMAL_COLOR;
            default:
                return NORMAL_COLOR;
        }
    }
}
