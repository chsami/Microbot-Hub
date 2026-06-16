/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

final class BankTagLayoutMoveAction
{
	private final int itemId;
	private final String name;
	private final int quantity;
	private final String layoutName;
	private final int sourceTab;
	private final int targetTab;
	private final int targetSlot;

	BankTagLayoutMoveAction(int itemId, String name, int quantity, String layoutName, int sourceTab, int targetTab, int targetSlot)
	{
		this.itemId = itemId;
		this.name = name;
		this.quantity = quantity;
		this.layoutName = layoutName;
		this.sourceTab = sourceTab;
		this.targetTab = targetTab;
		this.targetSlot = targetSlot;
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

	String layoutName()
	{
		return layoutName;
	}

	int sourceTab()
	{
		return sourceTab;
	}

	int targetTab()
	{
		return targetTab;
	}

	int targetSlot()
	{
		return targetSlot;
	}
}
