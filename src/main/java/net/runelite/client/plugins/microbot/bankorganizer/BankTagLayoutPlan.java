/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BankTagLayoutPlan
{
	private final List<BankTagLayoutTab> tabs;
	private final List<BankTagLayoutMoveAction> actions;
	private final int matchedStacks;
	private final int unlistedStacks;
	private final int unlistedActiveTabbedStacks;

	BankTagLayoutPlan(
		List<BankTagLayoutTab> tabs,
		List<BankTagLayoutMoveAction> actions,
		int matchedStacks,
		int unlistedStacks,
		int unlistedActiveTabbedStacks)
	{
		this.tabs = Collections.unmodifiableList(new ArrayList<>(tabs));
		this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
		this.matchedStacks = matchedStacks;
		this.unlistedStacks = unlistedStacks;
		this.unlistedActiveTabbedStacks = unlistedActiveTabbedStacks;
	}

	List<BankTagLayoutTab> tabs()
	{
		return tabs;
	}

	List<BankTagLayoutMoveAction> actions()
	{
		return actions;
	}

	int matchedStacks()
	{
		return matchedStacks;
	}

	int unlistedStacks()
	{
		return unlistedStacks;
	}

	int unlistedActiveTabbedStacks()
	{
		return unlistedActiveTabbedStacks;
	}
}
