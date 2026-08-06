/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BankTagLayoutTab
{
	private final int tabIndex;
	private final String name;
	private final int iconItemId;
	private final List<Integer> orderedItemIds;
	private final Set<Integer> itemIds;

	BankTagLayoutTab(int tabIndex, String name, int iconItemId, List<Integer> orderedItemIds)
	{
		this.tabIndex = tabIndex;
		this.name = name == null || name.trim().isEmpty() ? "Layout " + tabIndex : name.trim();
		this.iconItemId = iconItemId;
		this.orderedItemIds = Collections.unmodifiableList(new ArrayList<>(orderedItemIds));
		this.itemIds = Collections.unmodifiableSet(new HashSet<>(orderedItemIds));
	}

	int tabIndex()
	{
		return tabIndex;
	}

	String name()
	{
		return name;
	}

	int iconItemId()
	{
		return iconItemId;
	}

	List<Integer> orderedItemIds()
	{
		return orderedItemIds;
	}

	Set<Integer> uniqueItemIds()
	{
		return itemIds;
	}

	boolean containsItemId(int itemId)
	{
		return itemIds.contains(itemId);
	}
}
