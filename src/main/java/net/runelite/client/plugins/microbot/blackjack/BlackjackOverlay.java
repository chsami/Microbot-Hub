package net.runelite.client.plugins.microbot.blackjack;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class BlackjackOverlay extends OverlayPanel
{
    private final BlackjackPlugin plugin;

    @Inject
    BlackjackOverlay(BlackjackPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        BlackjackScript script = plugin.getScript();
        if (script == null)
        {
            return super.render(graphics);
        }
        panelComponent.setPreferredSize(new Dimension(250, 360));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Blackjack")
                .color(script.getState() == BlackjackState.ERROR ? Color.RED : Color.ORANGE)
                .build());

        addLine("State", script.getState().toString());
        addLine("State age", script.getStateAgeSeconds() + "s");
        addLine("Next", script.getNextAction());
        addLine("Observed", script.getLastOutcome());
        addLine("Target", script.getTargetDescription());
        addLine("Combat signal", script.isCombatSignal() ? "TARGETING" : "Clear");
        addLine("Thieving", Integer.toString(Rs2Player.getRealSkillLevel(Skill.THIEVING)));
        addLine("XP gained", Integer.toString(script.getXpGained()));
        addLine("Knockouts", Integer.toString(script.getSuccessfulKnockouts()));
        addLine("Failed KOs", Integer.toString(script.getFailedKnockouts()));
        addLine("Pickpockets", Integer.toString(script.getSuccessfulPickpockets()));
        addLine("Burst", script.getPicksThisKnockout() + "/2 (" + script.getPickpocketClicks() + " clicks)");
        addLine("Burst timeouts", Integer.toString(script.getBurstTimeouts()));
        addLine("KO timeouts", Integer.toString(script.getKnockoutDispatchTimeouts()));
        addLine("Reset probes", Integer.toString(script.getCombatResetRetries()));
        addLine("HP", Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS)
                + "/" + Rs2Player.getRealSkillLevel(Skill.HITPOINTS));
        BlackjackSupplies supplies = plugin.getSupplies();
        addLine("Wine", supplies == null ? "Unknown" : Integer.toString(supplies.wine));
        addLine("Noted wine", supplies == null ? "Unknown" : Integer.toString(supplies.notedWine));
        addLine("Wines to heal", Integer.toString(script.getWinesToHeal()));
        addLine("Scheduled break", script.getBreakStatus());
        addLine("Wine run", script.isWineRestockPending() ? "Pending" : "Ready");
        addLine("Humanizer", script.getHumanizerStatus());
        addLine("Human events", Integer.toString(script.getHumanizerEvents()));
        addLine("Runtime", script.getFormattedRuntime());

        if (!script.getStopReason().isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(script.getStopReason())
                    .leftColor(Color.RED)
                    .build());
        }

        addLine("Version", BlackjackPlugin.VERSION);
        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right)
                .build());
    }
}
