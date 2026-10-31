package net.runelite.client.plugins.microbot.cgprep;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.cgprep.plan.Needs;
import net.runelite.client.plugins.microbot.cgprep.plan.PrepGoal;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class CgPrepOverlay extends OverlayPanel
{
	private final CgPrepPlugin plugin;
	private final CgPrepConfig config;

	@Inject
	CgPrepOverlay(CgPrepPlugin plugin, CgPrepConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		CgPrepScript s = plugin.script();
		panelComponent.setPreferredSize(new Dimension(280, 0));
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Auto Gauntlet v" + CgPrepPlugin.version + (config.dryRun() ? "  [DRY RUN]" : ""))
			.color(config.dryRun() ? Color.YELLOW : Color.GREEN)
			.build());
		if (config.dryRun())
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("DRY RUN: plans only, sends NO clicks").color(Color.RED).build());
		}

		if (s.handover.isActive())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text(s.handover.banner()).color(Color.ORANGE).build());
			for (String line : s.handover.lines())
			{
				panelComponent.getChildren().add(LineComponent.builder().left(line).leftColor(
					line.startsWith("WARNING") ? Color.RED : Color.WHITE).build());
			}
			panelComponent.getChildren().add(LineComponent.builder().left("After the run: " + config.continueKey() + " to go again").build());
			return super.render(g);
		}

		String state = s.getCurrentState() == null ? "-" : s.getCurrentState().name() + (s.paused ? " (PAUSED)" : "");
		long reenter = s.reenterAtMs;
		if (s.getCurrentState() == CgState.LOBBY_SETUP && reenter > 0)
		{
			long left = Math.max(0, reenter - System.currentTimeMillis());
			state += " (going back in: " + ((left + 999) / 1000) + "s)";
		}
		else if (s.waitingReason != null)
		{
			state += " (waiting: " + s.waitingReason + ")";
		}
		boolean boss = s.getCurrentState() == CgState.BOSS;
		net.runelite.client.plugins.microbot.cgprep.boss.BossFight bf = s.boss;
		row("State", state + (boss ? "  fight " + bf.mode + (bf.active ? "" : " (no boss seen)") : ""));
		row("Mode", String.valueOf(config.mode()));
		row("Loadout", String.valueOf(config.profile()));
		int t = s.secondsLeft;
		row("Prep timer", t < 0 ? "-" : (t / 60) + ":" + String.format("%02d", t % 60));
		row("Room", s.maze.roomX() + "," + s.maze.roomZ() + "  found " + s.maze.foundRoomCount() + "/49");
		if (boss)
		{
			net.runelite.client.plugins.microbot.cgprep.boss.FightPlan fp = bf.lastPlan;
			row("Goal", "Fight the Hunllef");
			row("Last", fp == null ? "-" : shorten(fp + " " + bf.lastClicks));
			row("Missing", "-");
		}
		else
		{
			PrepGoal goal = s.goal;
			boolean youPrep = config.autoMode() == CgPrepConfig.AutoMode.BOSS_ONLY && s.getCurrentState() != CgState.LOBBY_SETUP;
			row("Goal", youPrep ? "You prep (Boss Only)" : goal == null ? "-" : goal.headline());
			row("Last", shorten(s.lastAction));
			Needs n = s.needs;
			row("Missing", n == null ? "-" : n.toString());
		}

		panelComponent.getChildren().add(LineComponent.builder().left(" ").build());
		net.runelite.client.plugins.microbot.cgprep.stats.RunStats st = s.stats;
		row("Runs", st == null ? "-" : String.valueOf(st.runs()));
		row("Setup deaths", st == null ? "-" : String.valueOf(st.setupDeaths()));
		row("Boss deaths", st == null ? "-" : String.valueOf(st.bossDeaths()));
		row("GP", st == null ? "-" : st.gp());
		return super.render(g);
	}

	/** One line: long action notes are cut, the full text is in the decision log. */
	private static String shorten(String s)
	{
		return s == null ? "-" : s.length() > 40 ? s.substring(0, 39) + "…" : s;
	}

	private void row(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
	}
}
