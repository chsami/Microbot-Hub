/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BankSnapshot
{
	private final List<BankStack> items;
	private final int[] tabCounts;
	private final int currentTab;

	BankSnapshot(List<BankStack> items, int[] tabCounts, int currentTab)
	{
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
		this.tabCounts = tabCounts.clone();
		this.currentTab = currentTab;
	}

	List<BankStack> items()
	{
		return items;
	}

	int[] tabCounts()
	{
		return tabCounts.clone();
	}

	int currentTab()
	{
		return currentTab;
	}

	int stackCount()
	{
		return items.size();
	}

	int tabbedStackCount()
	{
		int total = 0;
		for (int count : tabCounts)
		{
			total += count;
		}
		return total;
	}

	int mainTabCount()
	{
		return Math.max(0, stackCount() - tabbedStackCount());
	}

	static final class BankStack
	{
		private final int itemId;
		private final String name;
		private final int quantity;
		private final int slot;
		private final int allItemsIndex;
		private final int tab;
		private final boolean stackable;
		private final boolean tradeable;
		private final boolean geTradeable;
		private final boolean equipable;

		BankStack(
			int itemId,
			String name,
			int quantity,
			int slot,
			int allItemsIndex,
			int tab,
			boolean stackable,
			boolean tradeable,
			boolean geTradeable,
			boolean equipable)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.slot = slot;
			this.allItemsIndex = allItemsIndex;
			this.tab = tab;
			this.stackable = stackable;
			this.tradeable = tradeable;
			this.geTradeable = geTradeable;
			this.equipable = equipable;
		}

		int itemId()
		{
			return itemId;
		}

		String name()
		{
			return name;
		}

		int quantity()
		{
			return quantity;
		}

		int slot()
		{
			return slot;
		}

		int allItemsIndex()
		{
			return allItemsIndex;
		}

		int tab()
		{
			return tab;
		}

		boolean stackable()
		{
			return stackable;
		}

		boolean tradeable()
		{
			return tradeable;
		}

		boolean geTradeable()
		{
			return geTradeable;
		}

		boolean equipable()
		{
			return equipable;
		}
	}
}
