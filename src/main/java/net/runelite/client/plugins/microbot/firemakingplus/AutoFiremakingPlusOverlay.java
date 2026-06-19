package net.runelite.client.plugins.microbot.firemakingplus;

import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ButtonComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.NumberFormat;
import java.time.Duration;

public class AutoFiremakingPlusOverlay extends OverlayPanel {
    private static final Color TITLE_COLOR = new Color(0, 170, 0);
    private static final Color HEADER_COLOR = new Color(140, 220, 140);
    private static final Color NORMAL_TEXT_COLOR = Color.WHITE;
    private static final Color HIGHLIGHT_COLOR = new Color(255, 235, 145);

    private final AutoFiremakingPlusPlugin plugin;
    private final Client client;
    private final AutoFiremakingPlusConfig config;

    public final ButtonComponent pauseButton;

    @Inject
    AutoFiremakingPlusOverlay(AutoFiremakingPlusPlugin plugin, Client client, AutoFiremakingPlusConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();

        pauseButton = new ButtonComponent("Pause");
        pauseButton.setPreferredSize(new Dimension(100, 25));
        pauseButton.setParentOverlay(this);
        pauseButton.setFont(FontManager.getRunescapeBoldFont());
        pauseButton.setOnClick(() -> {
            Microbot.pauseAllScripts.set(!Microbot.pauseAllScripts.get());
            if (Microbot.pauseAllScripts.get()) {
                Rs2Walker.setTarget(null);
            }
        });
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(240, 300));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("AutoFiremakingPlus v" + AutoFiremakingPlusPlugin.version)
                    .color(TITLE_COLOR)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(Microbot.status == null ? "Idle" : Microbot.status)
                    .rightColor(HIGHLIGHT_COLOR)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().left("").build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Statistics")
                    .leftColor(HEADER_COLOR)
                    .build());

            AutoFiremakingPlusScript script = plugin.getScript();
            if (script != null && script.getStartTimeMillis() > 0) {
                int currentLevel = client.getRealSkillLevel(Skill.FIREMAKING);
                int currentXp = client.getSkillExperience(Skill.FIREMAKING);
                int xpGained = currentXp - script.getStartSkillXp();
                long runtimeMillis = System.currentTimeMillis() - script.getStartTimeMillis();
                long xpPerHour = (runtimeMillis > 1000) ? (xpGained * 3600000L / runtimeMillis) : 0;

                int levelDelta = currentLevel - script.getStartSkillLevel();
                String levelStr = currentLevel + (levelDelta > 0 ? " (+" + levelDelta + ")" : "");

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Firemaking level:")
                        .right(levelStr)
                        .rightColor(NORMAL_TEXT_COLOR)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("XP gained:")
                        .right(NumberFormat.getInstance().format(xpGained))
                        .rightColor(NORMAL_TEXT_COLOR)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("XP/hr:")
                        .right(NumberFormat.getInstance().format(xpPerHour))
                        .rightColor(NORMAL_TEXT_COLOR)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Logs burnt:")
                        .right(String.valueOf(script.getActionsCompleted()))
                        .rightColor(NORMAL_TEXT_COLOR)
                        .build());

                // Firemaking spends logs (no product), so this is a cost rate, not profit. Uses the
                // configured log's GE price (approximate under Progressive, which varies the log).
                long logCostPerHour = 0;
                if (config.logType() != null && runtimeMillis > 1000) {
                    int logPrice = Microbot.getItemManager().getItemPrice(config.logType().getItemId());
                    if (logPrice > 0) {
                        logCostPerHour = (long) script.getActionsCompleted() * logPrice * 3600000L / runtimeMillis;
                    }
                }
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Log cost/hr:")
                        .right(logCostPerHour > 0 ? "-" + NumberFormat.getInstance().format(logCostPerHour) : "0")
                        .rightColor(NORMAL_TEXT_COLOR)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Runtime:")
                        .right(formatDuration(Duration.ofMillis(runtimeMillis)))
                        .rightColor(NORMAL_TEXT_COLOR)
                        .build());

                if (config.targetLevel() > 0) {
                    int toGo = Math.max(0, config.targetLevel() - currentLevel);
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Target:")
                            .right(config.targetLevel() + (toGo > 0 ? " (" + toGo + " to go)" : " (reached)"))
                            .rightColor(HIGHLIGHT_COLOR)
                            .build());
                    if (toGo > 0 && xpPerHour > 0) {
                        long xpRemaining = Math.max(0, Experience.getXpForLevel(config.targetLevel()) - currentXp);
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("ETA:")
                                .right(formatDuration(Duration.ofMillis(xpRemaining * 3600000L / xpPerHour)))
                                .rightColor(HIGHLIGHT_COLOR)
                                .build());
                    }
                }
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("(not running)")
                        .leftColor(NORMAL_TEXT_COLOR)
                        .build());
            }

            pauseButton.setText(Microbot.pauseAllScripts.get() ? "Resume" : "Pause");
            panelComponent.getChildren().add(pauseButton);

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private String formatDuration(Duration duration) {
        return String.format("%02d:%02d:%02d", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }
}
