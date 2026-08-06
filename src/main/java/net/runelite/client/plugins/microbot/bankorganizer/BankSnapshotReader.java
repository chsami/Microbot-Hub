/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Varbits;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

final class BankSnapshotReader
{
	private static final int[] TAB_COUNT_VARBITS = {
		Varbits.BANK_TAB_ONE_COUNT,
		Varbits.BANK_TAB_TWO_COUNT,
		Varbits.BANK_TAB_THREE_COUNT,
		Varbits.BANK_TAB_FOUR_COUNT,
		Varbits.BANK_TAB_FIVE_COUNT,
		Varbits.BANK_TAB_SIX_COUNT,
		Varbits.BANK_TAB_SEVEN_COUNT,
		Varbits.BANK_TAB_EIGHT_COUNT,
		Varbits.BANK_TAB_NINE_COUNT
	};

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	BankSnapshotReader(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	BankSnapshot read()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(this::readOnClientThread)
			.orElseThrow(() -> new IllegalStateException("Could not read bank snapshot on the client thread."));
	}

	private BankSnapshot readOnClientThread()
	{
		if (!Rs2Bank.isOpen())
		{
			throw new IllegalStateException("Open your bank before running Bank Organizer.");
		}

		List<Rs2ItemModel> bankItems = new ArrayList<>(Rs2Bank.bankItems());
		bankItems.sort(Comparator.comparingInt(Rs2ItemModel::getSlot));

		int[] tabCounts = readTabCounts();
		List<BankSnapshot.BankStack> stacks = new ArrayList<>();
		for (int i = 0; i < bankItems.size(); i++)
		{
			Rs2ItemModel item = bankItems.get(i);
			int itemId = item.getId();
			ItemComposition composition = itemManager.getItemComposition(itemId);
			String name = composition.getName();
			if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
			{
				name = item.getName();
			}

			stacks.add(new BankSnapshot.BankStack(
				itemId,
				name,
				item.getQuantity(),
				item.getSlot(),
				i,
				tabForIndex(i, tabCounts),
				composition.isStackable(),
				composition.isTradeable(),
				composition.isGeTradeable(),
				isEquipable(composition)));
		}

		return new BankSnapshot(stacks, tabCounts, client.getVarbitValue(Varbits.CURRENT_BANK_TAB));
	}

	private int[] readTabCounts()
	{
		int[] counts = new int[TAB_COUNT_VARBITS.length];
		for (int i = 0; i < TAB_COUNT_VARBITS.length; i++)
		{
			counts[i] = client.getVarbitValue(TAB_COUNT_VARBITS[i]);
		}
		return counts;
	}

	private static int mainTabCount(int stackCount, int[] tabCounts)
	{
		int tabbed = 0;
		for (int count : tabCounts)
		{
			tabbed += count;
		}
		return Math.max(0, stackCount - tabbed);
	}

	private static int tabForIndex(int index, int[] tabCounts)
	{
		int cursor = 0;
		for (int i = 0; i < tabCounts.length; i++)
		{
			cursor += tabCounts[i];
			if (index < cursor)
			{
				return i + 1;
			}
		}
		return 0;
	}

	private static boolean isEquipable(ItemComposition composition)
	{
		String[] actions = composition.getInventoryActions();
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action == null)
			{
				continue;
			}
			String lower = action.toLowerCase();
			if (lower.contains("wear") || lower.contains("wield") || lower.contains("equip"))
			{
				return true;
			}
		}
		return false;
	}
}
