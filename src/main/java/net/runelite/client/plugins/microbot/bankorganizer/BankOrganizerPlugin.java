/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import net.runelite.client.plugins.microbot.PluginConstants;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import javax.inject.Inject;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = PluginConstants.BGA + "Bank Organizer",
	description = "Organizes real bank tabs from practical item categories.",
	tags = {"bank", "organizer", "tabs", "sort"},
	authors = {"bgatfa"},
	version = BankOrganizerPlugin.version,
	minClientVersion = "2.0.61",
	iconUrl = "https://bgatfa.github.io/Microbot-Hub/BankOrganizerPlugin/assets/icon.png",
	cardUrl = "https://bgatfa.github.io/Microbot-Hub/BankOrganizerPlugin/assets/card.png",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
public class BankOrganizerPlugin extends Plugin
{
	public static final String version = "1.0.0";

	@Inject
	private BankOrganizerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankOrganizerOverlay overlay;

	@Inject
	private BankSnapshotReader snapshotReader;

	@Inject
	private BankActuator actuator;

	@Inject
	private ItemManager itemManager;

	private final BankTagLayoutParser layoutParser = new BankTagLayoutParser();
	private final BankTagLayoutPlanner layoutPlanner = new BankTagLayoutPlanner();

	private ExecutorService executor;
	private Future<?> task;
	private volatile boolean stopRequested;
	private volatile OverlayState overlayState = OverlayState.idle("Ready.");

	@Provides
	BankOrganizerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankOrganizerConfig.class);
	}

	@Override
	protected void startUp()
	{
		stopRequested = false;
		executor = Executors.newSingleThreadExecutor();
		overlayManager.add(overlay);
		overlayState = OverlayState.running("Starting", "Starting Bank Organizer.");
		startOrganizer();
	}

	@Override
	protected void shutDown()
	{
		stopRequested = true;
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
		task = null;
		overlayManager.remove(overlay);
		overlayState = OverlayState.idle("Stopped.");
	}

	OverlayState getOverlayStateSnapshot()
	{
		return overlayState;
	}

	private void startOrganizer()
	{
		if (task != null && !task.isDone())
		{
			overlayState = overlayState.withMessage("Already running.");
			return;
		}

		ExecutorService currentExecutor = executor;
		if (currentExecutor == null)
		{
			return;
		}

		stopRequested = false;
		overlayState = OverlayState.running("Snapshot", "Reading bank snapshot.");
		task = currentExecutor.submit(this::runOrganizer);
	}

	private void runOrganizer()
	{
		try
		{
			if (stopRequested)
			{
				return;
			}

			overlayState = OverlayState.running("Layout", "Parsing configured bank tag layouts.");
			List<BankTagLayoutTab> tabs = layoutParser.parse(config);
			if (tabs.isEmpty())
			{
				overlayState = OverlayState.idle("No active layout tabs. Enable at least one Layout tab active toggle.")
					.withPhase("Blocked", "No active layout tabs.");
				return;
			}

			List<BankTagLayoutConflict> conflicts = layoutPlanner.conflicts(tabs);
			if (!conflicts.isEmpty())
			{
				overlayState = OverlayState.fromConflicts(conflicts);
				return;
			}
			if (stopRequested)
			{
				return;
			}

			overlayState = OverlayState.running("Bank", "Opening bank.");
			if (!actuator.ensureBankOpen())
			{
				throw new IllegalStateException("Could not open bank.");
			}
			if (stopRequested)
			{
				return;
			}

			overlayState = OverlayState.running("Mode", "Checking bank rearrange mode.");
			BankActuator.ActuatorResult insertMode = actuator.ensureBankInsertMode();
			if (!insertMode.success())
			{
				throw new IllegalStateException(insertMode.message());
			}
			if (stopRequested)
			{
				return;
			}

			overlayState = OverlayState.running("Snapshot", "Reading bank snapshot.");
			BankSnapshot snapshot = snapshotReader.read();
			if (stopRequested)
			{
				return;
			}

			runBankTagLayoutPlanner(snapshot, tabs);
		}
		catch (Throwable t)
		{
			overlayState = OverlayState.idle("Failed: " + t.getMessage()).withPhase("Blocked", "Failed: " + t.getMessage());
		}
	}

	private void runBankTagLayoutPlanner(BankSnapshot snapshot, List<BankTagLayoutTab> tabs)
	{
		IntFunction<String> itemNameLookup = config.forceInsertVariants()
			? itemNameLookup(tabs)
			: this::itemName;
		BankTagLayoutPlan plan = layoutPlanner.plan(snapshot, tabs, config.forceInsertVariants(), itemNameLookup);
		if (stopRequested)
		{
			return;
		}

		overlayState = OverlayState.fromSnapshot("Layout", "Live layout delta organize requested.", snapshot, plan.actions().size());
		runLiveLayoutOrganizer(plan);
	}

	private void runLiveLayoutOrganizer(BankTagLayoutPlan plan)
	{
		if (stopRequested)
		{
			return;
		}

		overlayState = OverlayState.running("Organize", "Moving " + plan.actions().size()
			+ " listed stacks into " + plan.tabs().size() + " configured layout tabs.");
		BankActuator.FullOrganizeResult result = actuator.runBankTagLayoutDelta(plan, config.forceInsertVariants());
		String phase = result.success() ? "Organize OK" : "Blocked";
		if (result.finalSnapshot() != null)
		{
			overlayState = OverlayState.fromSnapshot(phase, result.message(), result.finalSnapshot(), result.movedStacks());
		}
		else
		{
			overlayState = OverlayState.idle(result.message()).withPhase(phase, result.message());
		}

	}

	private IntFunction<String> itemNameLookup(List<BankTagLayoutTab> tabs)
	{
		Map<Integer, String> names = layoutItemNames(tabs);
		return itemId -> names.getOrDefault(itemId, "");
	}

	private Map<Integer, String> layoutItemNames(List<BankTagLayoutTab> tabs)
	{
		Set<Integer> itemIds = new HashSet<>();
		for (BankTagLayoutTab tab : tabs)
		{
			itemIds.addAll(tab.orderedItemIds());
		}

		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Map<Integer, String> names = new HashMap<>();
			for (int itemId : itemIds)
			{
				names.put(itemId, itemNameOnClientThread(itemId));
			}
			return names;
		}).orElse(Collections.emptyMap());
	}

	private String itemName(int itemId)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			return itemNameOnClientThread(itemId);
		}).orElse("");
	}

	private String itemNameOnClientThread(int itemId)
	{
		try
		{
			ItemComposition composition = itemManager.getItemComposition(itemId);
			String name = composition.getName();
			return name == null ? "" : name;
		}
		catch (Throwable ignored)
		{
			return "";
		}
	}

	static final class OverlayState
	{
		final String phase;
		final String message;
		final int stackCount;
		final int plannedStackCount;
		final int categoryTabCount;
		final int actionCount;
		final int currentTab;
		final int mainTabCount;
		final List<DetailLine> detailLines;

		private OverlayState(
			String phase,
			String message,
			int stackCount,
			int plannedStackCount,
			int categoryTabCount,
			int actionCount,
			int currentTab,
			int mainTabCount,
			List<DetailLine> detailLines)
		{
			this.phase = phase;
			this.message = message;
			this.stackCount = stackCount;
			this.plannedStackCount = plannedStackCount;
			this.categoryTabCount = categoryTabCount;
			this.actionCount = actionCount;
			this.currentTab = currentTab;
			this.mainTabCount = mainTabCount;
			this.detailLines = Collections.unmodifiableList(new ArrayList<>(detailLines));
		}

		static OverlayState idle(String message)
		{
			return new OverlayState("Idle", message, 0, 0, 0, 0, 0, 0, Collections.emptyList());
		}

		static OverlayState running(String phase, String message)
		{
			return new OverlayState(phase, message, 0, 0, 0, 0, 0, 0, Collections.emptyList());
		}

		static OverlayState fromSnapshot(String phase, String message, BankSnapshot snapshot, int actionCount)
		{
			return new OverlayState(
				phase,
				message,
				snapshot.stackCount(),
				snapshot.stackCount(),
				0,
				actionCount,
				snapshot.currentTab(),
				snapshot.mainTabCount(),
				Collections.emptyList());
		}

		static OverlayState fromConflicts(List<BankTagLayoutConflict> conflicts)
		{
			List<DetailLine> lines = new ArrayList<>();
			int shown = 0;
			for (BankTagLayoutConflict conflict : conflicts)
			{
				if (shown >= 8)
				{
					break;
				}
				lines.add(new DetailLine("Item ID " + conflict.itemId(), "tabs " + conflict.tabIndexesDisplay()));
				shown++;
			}
			if (conflicts.size() > shown)
			{
				lines.add(new DetailLine("More conflicts", Integer.toString(conflicts.size() - shown)));
			}
			return new OverlayState(
				"Blocked",
				"Resolve duplicate layout item IDs before enabling organizer.",
				0,
				0,
				0,
				0,
				0,
				0,
				lines);
		}

		OverlayState withMessage(String nextMessage)
		{
			return new OverlayState(phase, nextMessage, stackCount, plannedStackCount, categoryTabCount, actionCount,
				currentTab, mainTabCount, detailLines);
		}

		OverlayState withPhase(String nextPhase, String nextMessage)
		{
			return new OverlayState(nextPhase, nextMessage, stackCount, plannedStackCount, categoryTabCount, actionCount,
				currentTab, mainTabCount, detailLines);
		}
	}

	static final class DetailLine
	{
		final String left;
		final String right;

		DetailLine(String left, String right)
		{
			this.left = left;
			this.right = right;
		}
	}
}
