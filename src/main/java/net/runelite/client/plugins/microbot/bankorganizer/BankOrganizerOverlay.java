/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

class BankOrganizerOverlay extends OverlayPanel
{
	private static final int WIDTH = 232;
	private static final Color MUTED = new Color(190, 190, 190);
	private static final Color WARNING = new Color(255, 190, 85);
	private static final Color STOPPED = new Color(255, 115, 115);

	private final BankOrganizerPlugin plugin;
	private final BankOrganizerConfig config;

	@Inject
	BankOrganizerOverlay(BankOrganizerPlugin plugin, BankOrganizerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_RIGHT);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
		panelComponent.setBorder(new Rectangle(6, 5, 6, 5));
		panelComponent.setGap(new Point(0, 3));
	}

	@Override
	public Dimension render(java.awt.Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		BankOrganizerPlugin.OverlayState state = plugin.getOverlayStateSnapshot();
		graphics.setFont(FontManager.getRunescapeSmallFont());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Bank Organizer")
			.right(state.phase)
			.rightColor(colorFor(state.phase, state.message))
			.build());

		if (state.stackCount > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Stacks")
				.right(state.plannedStackCount + " / " + state.stackCount)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Category tabs")
				.right(Integer.toString(state.categoryTabCount))
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Planned actions")
				.right(Integer.toString(state.actionCount))
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Current tab")
				.right(Integer.toString(state.currentTab))
				.leftColor(MUTED)
				.rightColor(MUTED)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Main stacks")
				.right(Integer.toString(state.mainTabCount))
				.leftColor(MUTED)
				.rightColor(MUTED)
				.build());

		}

		for (BankOrganizerPlugin.DetailLine line : state.detailLines)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(line.left)
				.right(line.right)
				.build());
		}

		if (state.message != null && !state.message.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(state.message)
				.leftColor(colorFor(state.phase, state.message))
				.build());
		}

		return super.render(graphics);
	}

	private static Color colorFor(String phase, String message)
	{
		String lower = ((phase == null ? "" : phase) + " " + (message == null ? "" : message)).toLowerCase();
		if (lower.contains("failed") || lower.contains("blocked") || lower.contains("stopped"))
		{
			return STOPPED;
		}
		if (lower.contains("not implemented") || lower.contains("open your bank") || lower.contains("already"))
		{
			return WARNING;
		}
		return MUTED;
	}
}
