/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import net.runelite.client.plugins.microbot.PluginConstants;

import com.google.inject.Provides;
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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;

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
@Slf4j
public class BankOrganizerPlugin extends Plugin
{
	public static final String version = "1.0.0";

	@Inject
	private BankOrganizerConfig config;

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
		log.info("Starting Bank Organizer.");
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
		log.info("Bank Organizer stopped.");
	}

	private void startOrganizer()
	{
		if (task != null && !task.isDone())
		{
			log.info("Bank Organizer is already running.");
			return;
		}

		ExecutorService currentExecutor = executor;
		if (currentExecutor == null)
		{
			return;
		}

		stopRequested = false;
		log.info("Reading bank snapshot.");
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

			log.info("Parsing configured bank tag layouts.");
			List<BankTagLayoutTab> tabs = layoutParser.parse(config);
			if (tabs.isEmpty())
			{
				log.warn("No active layout tabs. Enable at least one layout tab active toggle.");
				return;
			}

			List<BankTagLayoutConflict> conflicts = layoutPlanner.conflicts(tabs);
			if (!conflicts.isEmpty())
			{
				log.warn("Resolve duplicate layout item IDs before enabling organizer: {} conflict(s).", conflicts.size());
				return;
			}
			if (stopRequested)
			{
				return;
			}

			log.info("Opening bank.");
			if (!actuator.ensureBankOpen())
			{
				throw new IllegalStateException("Could not open bank.");
			}
			if (stopRequested)
			{
				return;
			}

			log.info("Checking bank rearrange mode.");
			BankActuator.ActuatorResult insertMode = actuator.ensureBankInsertMode();
			if (!insertMode.success())
			{
				throw new IllegalStateException(insertMode.message());
			}
			if (stopRequested)
			{
				return;
			}

			log.info("Reading bank snapshot.");
			BankSnapshot snapshot = snapshotReader.read();
			if (stopRequested)
			{
				return;
			}

			runBankTagLayoutPlanner(snapshot, tabs);
		}
		catch (Throwable t)
		{
			log.warn("Bank Organizer failed: {}", t.getMessage(), t);
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

		log.info("Live layout delta organize requested: {} planned action(s).", plan.actions().size());
		runLiveLayoutOrganizer(plan);
	}

	private void runLiveLayoutOrganizer(BankTagLayoutPlan plan)
	{
		if (stopRequested)
		{
			return;
		}

		log.info("Moving {} listed stack(s) into {} configured layout tab(s).", plan.actions().size(), plan.tabs().size());
		BankActuator.FullOrganizeResult result = actuator.runBankTagLayoutDelta(plan, config.forceInsertVariants());
		if (result.success())
		{
			log.info("Bank Organizer completed: {}", result.message());
		}
		else
		{
			log.warn("Bank Organizer blocked: {}", result.message());
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

}
