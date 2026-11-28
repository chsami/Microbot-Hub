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
	minClientVersion = "2.6.24",
	iconUrl = "",
	cardUrl = "",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BankOrganizerPlugin extends Plugin
{
	public static final String version = "1.0.2";
	private static final String INPUT_YIELD_TIMEOUT_MESSAGE =
		"You kept using the mouse or keyboard. Leave them idle for a few seconds, then turn Bank Organizer off and on again.";
	private static final String BANK_OPEN_FAILED_MESSAGE =
		"Could not open the bank. Stand next to a bank booth or banker and turn Bank Organizer off and on again.";

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
	private final InputYieldRetry inputYieldRetry = new InputYieldRetry(new InputYieldRetry.Listener()
	{
		@Override
		public void waitingForIdle()
		{
			log.info("Waiting for you to stop using the mouse and keyboard before organizing the bank...");
		}

		@Override
		public void retrying(int attempt, int maxAttempts)
		{
			log.info("You used the mouse or keyboard while Bank Organizer was working. Retrying (attempt {} of {})...",
				attempt, maxAttempts);
		}
	});

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

			BankActuator.FullOrganizeResult result = inputYieldRetry.run(
				() -> organizeBank(tabs),
				attempt -> attempt == null || attempt.success(),
				() -> BankActuator.FullOrganizeResult.fail(INPUT_YIELD_TIMEOUT_MESSAGE, null, 0, 0),
				last -> BankActuator.FullOrganizeResult.fail(
					INPUT_YIELD_TIMEOUT_MESSAGE + " (last step: " + last.message() + ")",
					last.finalSnapshot(), last.createdTabs(), last.movedStacks()));
			if (result == null)
			{
				return;
			}
			if (result.success())
			{
				log.info("Bank Organizer completed: {}", result.message());
			}
			else
			{
				log.warn("Bank Organizer blocked: {}", result.message());
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		catch (Throwable t)
		{
			log.warn("Bank Organizer failed: {}", t.getMessage(), t);
		}
	}

	private BankActuator.FullOrganizeResult organizeBank(List<BankTagLayoutTab> tabs)
	{
		if (stopRequested)
		{
			return null;
		}

		log.info("Opening bank.");
		if (!actuator.ensureBankOpen())
		{
			return BankActuator.FullOrganizeResult.fail(BANK_OPEN_FAILED_MESSAGE, null, 0, 0);
		}
		if (stopRequested)
		{
			return null;
		}

		log.info("Checking bank rearrange mode.");
		BankActuator.ActuatorResult insertMode = actuator.ensureBankInsertMode();
		if (!insertMode.success())
		{
			return BankActuator.FullOrganizeResult.fail(insertMode.message(), null, 0, 0);
		}
		if (stopRequested)
		{
			return null;
		}

		log.info("Reading bank snapshot.");
		BankSnapshot snapshot = snapshotReader.read();
		if (stopRequested)
		{
			return null;
		}

		IntFunction<String> itemNameLookup = config.forceInsertVariants()
			? itemNameLookup(tabs)
			: this::itemName;
		BankTagLayoutPlan plan = layoutPlanner.plan(snapshot, tabs, config.forceInsertVariants(), itemNameLookup);
		if (stopRequested)
		{
			return null;
		}

		log.info("Live layout delta organize requested: {} planned action(s).", plan.actions().size());
		log.info("Moving {} listed stack(s) into {} configured layout tab(s).", plan.actions().size(), plan.tabs().size());
		return actuator.runBankTagLayoutDelta(plan, config.forceInsertVariants());
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
