/*
 * Copyright (c) 2026, bgatfa
 * All rights reserved. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided the copyright notice is kept.
 */
package net.runelite.client.plugins.microbot.bankorganizer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BankOrganizerConfig.GROUP)
public interface BankOrganizerConfig extends Config
{
	String GROUP = "bankorganizer";
	String DEFAULT_LAYOUT_TAB_1 = "banktags,1,Gathering,1511,layout,0,1511,1,1521,2,6333,3,6332,4,1519,5,1517,6,1515,7,1513,8,8778,9,8780,10,960,11,8782,12,32904,13,32907,14,32910,15,19669,16,438,17,440,18,453,19,447,20,449,21,451,22,442,23,444,24,2349,25,2351,26,2353,27,2359,28,2361,29,2363,30,2355,31,2357,32,436,33,31719,34,32892,35,32889,36,31716,37,434,38,1761,39,2922,40,1623,41,1621,42,1619,43,1617,44,1631,45,1625,46,1627,47,1629,48,1607,49,1605,50,1603,51,1601,52,1615,53,1609,54,1611,55,1613,56,1656,57,1639,58,1660,59,1681,60,1683,61,21090,62,21102,63,21105,64,1597,65,1592,66,1595,67,1700,68,1702,69,1759,70,21111,71,21114,72,1757,73,1733,74,1734,75,21504,76,1781,77,1783,78,1785,79,1775";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the Bank Organizer status overlay.",
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "forceInsertVariants",
		name = "Force insert variants",
		description = "Group numeric charged variants at the first matching CSV position and sort them high-to-low.",
		position = 1
	)
	default boolean forceInsertVariants()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab1Active",
		name = "Tab 1",
		description = "Use tab 1 when organizing.",
		position = 2
	)
	default boolean layoutTab1Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab1",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 1.",
		position = 3
	)
	default String layoutTab1()
	{
		return DEFAULT_LAYOUT_TAB_1;
	}

	@ConfigItem(
		keyName = "layoutTab2Active",
		name = "Tab 2",
		description = "Use tab 2 when organizing.",
		position = 4
	)
	default boolean layoutTab2Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab2",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 2.",
		position = 5
	)
	default String layoutTab2()
	{
		return "";
	}

	@ConfigItem(
		keyName = "layoutTab3Active",
		name = "Tab 3",
		description = "Use tab 3 when organizing.",
		position = 6
	)
	default boolean layoutTab3Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab3",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 3.",
		position = 7
	)
	default String layoutTab3()
	{
		return "";
	}

	@ConfigItem(
		keyName = "layoutTab4Active",
		name = "Tab 4",
		description = "Use tab 4 when organizing.",
		position = 8
	)
	default boolean layoutTab4Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab4",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 4.",
		position = 9
	)
	default String layoutTab4()
	{
		return "";
	}

	@ConfigItem(
		keyName = "layoutTab5Active",
		name = "Tab 5",
		description = "Use tab 5 when organizing.",
		position = 10
	)
	default boolean layoutTab5Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab5",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 5.",
		position = 11
	)
	default String layoutTab5()
	{
		return "";
	}

	@ConfigItem(
		keyName = "layoutTab6Active",
		name = "Tab 6",
		description = "Use tab 6 when organizing.",
		position = 12
	)
	default boolean layoutTab6Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab6",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 6.",
		position = 13
	)
	default String layoutTab6()
	{
		return "";
	}

	@ConfigItem(
		keyName = "layoutTab7Active",
		name = "Tab 7",
		description = "Use tab 7 when organizing.",
		position = 14
	)
	default boolean layoutTab7Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab7",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 7.",
		position = 15
	)
	default String layoutTab7()
	{
		return "";
	}

	@ConfigItem(
		keyName = "layoutTab8Active",
		name = "Tab 8",
		description = "Use tab 8 when organizing.",
		position = 16
	)
	default boolean layoutTab8Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab8",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 8.",
		position = 17
	)
	default String layoutTab8()
	{
		return "";
	}

	@ConfigItem(
		keyName = "layoutTab9Active",
		name = "Tab 9",
		description = "Use tab 9 when organizing.",
		position = 18
	)
	default boolean layoutTab9Active()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutTab9",
		name = "",
		description = "RuneLite bank tag or layout CSV for real tab 9.",
		position = 19
	)
	default String layoutTab9()
	{
		return "";
	}

}
