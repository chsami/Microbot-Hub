/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntFunction;

final class BankTagLayoutPlanner
{
	List<BankTagLayoutConflict> conflicts(List<BankTagLayoutTab> tabs)
	{
		Map<Integer, List<Integer>> tabIndexesByItemId = new TreeMap<>();
		if (tabs == null)
		{
			return Collections.emptyList();
		}

		for (BankTagLayoutTab tab : tabs)
		{
			for (int itemId : tab.uniqueItemIds())
			{
				tabIndexesByItemId.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(tab.tabIndex());
			}
		}

		List<BankTagLayoutConflict> conflicts = new ArrayList<>();
		for (Map.Entry<Integer, List<Integer>> entry : tabIndexesByItemId.entrySet())
		{
			if (entry.getValue().size() > 1)
			{
				conflicts.add(new BankTagLayoutConflict(entry.getKey(), entry.getValue()));
			}
		}
		return conflicts;
	}

	BankTagLayoutPlan plan(
		BankSnapshot snapshot,
		List<BankTagLayoutTab> tabs,
		boolean forceInsertVariants,
		IntFunction<String> itemNameLookup)
	{
		if (tabs == null || tabs.isEmpty())
		{
			throw new IllegalArgumentException("No bank tag layouts are configured.");
		}

		Map<Integer, Target> targetByItemId = new HashMap<>();
		Set<Integer> activeTabIndexes = new HashSet<>();
		for (BankTagLayoutTab tab : tabs)
		{
			activeTabIndexes.add(tab.tabIndex());
			List<Integer> ids = tab.orderedItemIds();
			for (int slot = 0; slot < ids.size(); slot++)
			{
				targetByItemId.putIfAbsent(ids.get(slot), new Target(tab, slot));
			}
		}
		if (forceInsertVariants)
		{
			addForcedVariantTargets(snapshot, tabs, targetByItemId, itemNameLookup);
		}

		List<BankTagLayoutMoveAction> actions = new ArrayList<>();
		int matched = 0;
		int unlisted = 0;
		int unlistedActiveTabbed = 0;
		for (BankSnapshot.BankStack stack : snapshot.items())
		{
			Target target = targetByItemId.get(stack.itemId());
			if (target == null)
			{
				unlisted++;
				if (activeTabIndexes.contains(stack.tab()))
				{
					unlistedActiveTabbed++;
					actions.add(new BankTagLayoutMoveAction(
						stack.itemId(),
						stack.name(),
						stack.quantity(),
						"Main",
						stack.tab(),
						0,
						-1));
				}
				continue;
			}
			matched++;
			if (stack.tab() == target.tab.tabIndex())
			{
				continue;
			}
			actions.add(new BankTagLayoutMoveAction(
				stack.itemId(),
				stack.name(),
				stack.quantity(),
				target.tab.name(),
				stack.tab(),
				target.tab.tabIndex(),
				target.slot));
		}

		return new BankTagLayoutPlan(tabs, actions, matched, unlisted, unlistedActiveTabbed);
	}

	private static void addForcedVariantTargets(
		BankSnapshot snapshot,
		List<BankTagLayoutTab> tabs,
		Map<Integer, Target> targetByItemId,
		IntFunction<String> itemNameLookup)
	{
		Map<String, Target> targetByVariantBase = new HashMap<>();
		for (BankTagLayoutTab tab : tabs)
		{
			for (int itemId : tab.orderedItemIds())
			{
				Target target = targetByItemId.get(itemId);
				if (target == null)
				{
					continue;
				}

				String name = itemNameLookup == null ? "" : itemNameLookup.apply(itemId);
				Variant variant = Variant.fromName(name);
				if (variant == null)
				{
					continue;
				}

				Target existing = targetByVariantBase.get(variant.baseName());
				if (existing == null || target.before(existing))
				{
					targetByVariantBase.put(variant.baseName(), target);
				}
			}
		}

		for (BankSnapshot.BankStack stack : snapshot.items())
		{
			Variant variant = Variant.fromName(stack.name());
			if (variant == null)
			{
				continue;
			}

			Target target = targetByVariantBase.get(variant.baseName());
			if (target != null)
			{
				targetByItemId.put(stack.itemId(), target);
			}
		}
	}

	private static final class Target
	{
		private final BankTagLayoutTab tab;
		private final int slot;

		private Target(BankTagLayoutTab tab, int slot)
		{
			this.tab = tab;
			this.slot = slot;
		}

		private boolean before(Target other)
		{
			if (tab.tabIndex() != other.tab.tabIndex())
			{
				return tab.tabIndex() < other.tab.tabIndex();
			}
			return slot < other.slot;
		}
	}

	private static final class Variant
	{
		private final String baseName;

		private Variant(String baseName)
		{
			this.baseName = baseName;
		}

		static Variant fromName(String name)
		{
			if (name == null)
			{
				return null;
			}

			String trimmed = name.trim();
			if (!trimmed.endsWith(")"))
			{
				return null;
			}

			int open = trimmed.lastIndexOf('(');
			if (open <= 0 || open + 1 >= trimmed.length() - 1)
			{
				return null;
			}

			String chargeText = trimmed.substring(open + 1, trimmed.length() - 1);
			for (int i = 0; i < chargeText.length(); i++)
			{
				if (!Character.isDigit(chargeText.charAt(i)))
				{
					return null;
				}
			}

			try
			{
				if (Integer.parseInt(chargeText) <= 0)
				{
					return null;
				}
			}
			catch (NumberFormatException ex)
			{
				return null;
			}

			String baseName = trimmed.substring(0, open).trim().toLowerCase();
			return baseName.isEmpty() ? null : new Variant(baseName);
		}

		String baseName()
		{
			return baseName;
		}
	}
}
