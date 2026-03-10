package net.runelite.client.plugins.microbot.accountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountbuilder.task.Task;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AccountBuilderOverlay extends OverlayPanel {

    private final AccountBuilderPlugin plugin;

    @Inject
    public AccountBuilderOverlay(AccountBuilderPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 300));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("MB AccountBuilder v" + AccountBuilderPlugin.VERSION)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            AccountBuilderScript script = plugin.getScript();
            if (script == null || script.getTaskExecutor() == null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Initialising...")
                        .build());
                return super.render(graphics);
            }

            // Current task
            Task current = script.getTaskExecutor().getCurrentTask();
            String taskName = current != null ? current.getName() : "All tasks complete!";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Task:")
                    .right(taskName)
                    .build());

            // Progress
            int done = script.getTaskExecutor().getCompletedCount();
            int total = script.getTaskExecutor().getTotalCount();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Progress:")
                    .right(done + " / " + total)
                    .build());

            // Runtime
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(formatTime(script.getRuntimeMs()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            // Skill levels relevant to current task
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Attack:")
                    .right(String.valueOf(Microbot.getClient().getRealSkillLevel(Skill.ATTACK)))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Strength:")
                    .right(String.valueOf(Microbot.getClient().getRealSkillLevel(Skill.STRENGTH)))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Defence:")
                    .right(String.valueOf(Microbot.getClient().getRealSkillLevel(Skill.DEFENCE)))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Ranged:")
                    .right(String.valueOf(Microbot.getClient().getRealSkillLevel(Skill.RANGED)))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer:")
                    .right(String.valueOf(Microbot.getClient().getRealSkillLevel(Skill.PRAYER)))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Magic:")
                    .right(String.valueOf(Microbot.getClient().getRealSkillLevel(Skill.MAGIC)))
                    .build());

        } catch (Exception ex) {
            log.error("AccountBuilderOverlay render error: {}", ex.getMessage(), ex);
        }

        return super.render(graphics);
    }

    private String formatTime(long ms) {
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
