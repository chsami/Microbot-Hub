package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ScurriusOverlay extends OverlayPanel {
    @Inject
    ScurriusOverlay(ScurriusPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    private static String skillName(Skill skill) {
        String name = skill.getName();
        return name == null ? skill.name() : name;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(230, 0));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro Scurrius V" + ScurriusPlugin.version)
                    .color(Color.GREEN)
                    .build());

            long now = System.currentTimeMillis();
            long elapsed = ScurriusScript.XP.elapsedMs(now);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time: " + XpTracker.formatTime(elapsed))
                    .build());

            long total = 0;
            for (Skill skill : XpTracker.TRACKED) {
                long gained = ScurriusScript.XP.gained(skill, Microbot.getClient().getSkillExperience(skill));
                if (gained > 0) {
                    total += gained;
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left(skillName(skill) + ":")
                            .right(XpTracker.line(gained, elapsed))
                            .build());
                }
            }
            if (total > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Total:")
                        .right(XpTracker.line(total, elapsed))
                        .build());
            }
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kills:")
                    .right(ScurriusScript.totalKills + " (" + XpTracker.format(XpTracker.perHour(ScurriusScript.totalKills, elapsed)) + "/h)")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(ScurriusScript.state.toString())
                    .build());

            long breakEnds = ScurriusScript.breakEndsAtMs;
            if (breakEnds > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("On break: " + Breaks.formatRemaining(breakEnds - System.currentTimeMillis()) + " left")
                        .build());
            } else if (ScurriusScript.breakTripsTarget > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Next break after " + ScurriusScript.breakTripsDone + "/" + ScurriusScript.breakTripsTarget + " trips")
                        .build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Specs: " + ScurriusScript.specsUsed)
                    .build());

            int nextTrip = ScurriusScript.nextTripAt;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Since bank: " + ScurriusScript.killsSinceBank + (nextTrip > 0 ? " / next trip at " + nextTrip : ""))
                    .build());


        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
