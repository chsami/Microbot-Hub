/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

final class BankTagLayoutParser
{
	List<BankTagLayoutTab> parse(BankOrganizerConfig config)
	{
		List<BankTagLayoutTab> tabs = new ArrayList<>();
		addIfActive(tabs, 1, config.layoutTab1Active(), config.layoutTab1());
		addIfActive(tabs, 2, config.layoutTab2Active(), config.layoutTab2());
		addIfActive(tabs, 3, config.layoutTab3Active(), config.layoutTab3());
		addIfActive(tabs, 4, config.layoutTab4Active(), config.layoutTab4());
		addIfActive(tabs, 5, config.layoutTab5Active(), config.layoutTab5());
		addIfActive(tabs, 6, config.layoutTab6Active(), config.layoutTab6());
		addIfActive(tabs, 7, config.layoutTab7Active(), config.layoutTab7());
		addIfActive(tabs, 8, config.layoutTab8Active(), config.layoutTab8());
		addIfActive(tabs, 9, config.layoutTab9Active(), config.layoutTab9());
		return tabs;
	}

	private static void addIfActive(List<BankTagLayoutTab> tabs, int tabIndex, boolean active, String csv)
	{
		if (!active)
		{
			return;
		}

		BankTagLayoutTab tab = parseOne(tabIndex, csv);
		if (tab != null)
		{
			tabs.add(tab);
		}
	}

	static BankTagLayoutTab parseOne(int tabIndex, String csv)
	{
		if (csv == null || csv.trim().isEmpty())
		{
			return null;
		}

		List<String> tokens = parseCsv(csv);
		if (tokens.size() < 5)
		{
			throw new IllegalArgumentException("Layout tab " + tabIndex + " is too short to be a bank tags CSV.");
		}

		String name = tokens.size() > 2 ? tokens.get(2) : "Layout " + tabIndex;
		int iconItemId = parseIntOrDefault(tokens.size() > 3 ? tokens.get(3) : "", -1);
		int layoutIndex = indexOf(tokens, "layout");
		List<Integer> itemIds = layoutIndex >= 0
			? parseLayoutItemIds(tabIndex, tokens, layoutIndex)
			: parsePlainBankTagItemIds(tabIndex, tokens);

		return new BankTagLayoutTab(tabIndex, name, iconItemId, itemIds);
	}

	private static List<Integer> parseLayoutItemIds(int tabIndex, List<String> tokens, int layoutIndex)
	{
		TreeMap<Integer, Integer> bySlot = new TreeMap<>();
		for (int i = layoutIndex + 1; i + 1 < tokens.size(); i += 2)
		{
			Integer slot = parseInt(tokens.get(i));
			Integer itemId = parseInt(tokens.get(i + 1));
			if (slot == null || itemId == null || itemId <= 0)
			{
				continue;
			}
			bySlot.put(slot, itemId);
		}

		List<Integer> itemIds = new ArrayList<>(bySlot.values());
		if (itemIds.isEmpty())
		{
			throw new IllegalArgumentException("Layout tab " + tabIndex + " has no item IDs in its layout section.");
		}
		return itemIds;
	}

	private static List<Integer> parsePlainBankTagItemIds(int tabIndex, List<String> tokens)
	{
		List<Integer> itemIds = new ArrayList<>();
		for (int i = 4; i < tokens.size(); i++)
		{
			Integer itemId = parseInt(tokens.get(i));
			if (itemId != null && itemId > 0)
			{
				itemIds.add(itemId);
			}
		}

		if (itemIds.isEmpty())
		{
			throw new IllegalArgumentException("Layout tab " + tabIndex + " has no item IDs.");
		}
		return itemIds;
	}

	private static int indexOf(List<String> tokens, String value)
	{
		for (int i = 0; i < tokens.size(); i++)
		{
			if (value.equalsIgnoreCase(tokens.get(i).trim()))
			{
				return i;
			}
		}
		return -1;
	}

	private static Integer parseInt(String value)
	{
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	private static int parseIntOrDefault(String value, int fallback)
	{
		Integer parsed = parseInt(value);
		return parsed == null ? fallback : parsed;
	}

	private static List<String> parseCsv(String csv)
	{
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < csv.length(); i++)
		{
			char ch = csv.charAt(i);
			if (ch == '"')
			{
				if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"')
				{
					current.append('"');
					i++;
				}
				else
				{
					quoted = !quoted;
				}
			}
			else if (ch == ',' && !quoted)
			{
				tokens.add(current.toString().trim());
				current.setLength(0);
			}
			else
			{
				current.append(ch);
			}
		}
		tokens.add(current.toString().trim());
		return tokens;
	}
}
