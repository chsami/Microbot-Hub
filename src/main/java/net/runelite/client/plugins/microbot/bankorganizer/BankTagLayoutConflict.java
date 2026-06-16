/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BankTagLayoutConflict
{
	private final int itemId;
	private final List<Integer> tabIndexes;

	BankTagLayoutConflict(int itemId, List<Integer> tabIndexes)
	{
		this.itemId = itemId;
		this.tabIndexes = Collections.unmodifiableList(new ArrayList<>(tabIndexes));
	}

	int itemId()
	{
		return itemId;
	}

	List<Integer> tabIndexes()
	{
		return tabIndexes;
	}

	String tabIndexesDisplay()
	{
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < tabIndexes.size(); i++)
		{
			if (i > 0)
			{
				builder.append(", ");
			}
			builder.append(tabIndexes.get(i));
		}
		return builder.toString();
	}
}
