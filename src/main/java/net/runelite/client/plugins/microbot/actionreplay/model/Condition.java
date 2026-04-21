package net.runelite.client.plugins.microbot.actionreplay.model;

import lombok.Data;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Data
public class Condition
{
	public static final int NEARBY_RADIUS = 20;

	private ConditionType type;
	private StatKind stat;
	private ConditionComparator comparator;
	private Integer threshold;
	private String name;
	private Boolean present;
	private Integer minCount;

	public boolean check()
	{
		if (type == null)
		{
			return true;
		}
		switch (type)
		{
			case STAT:
				return checkStat();
			case NPC_NEARBY:
				return checkNpcNearby();
			case OBJECT_NEARBY:
				return checkObjectNearby();
			case INVENTORY:
				return checkInventory();
			default:
				return true;
		}
	}

	private boolean checkStat()
	{
		if (stat == null || comparator == null || threshold == null)
		{
			return true;
		}
		Skill skill = stat == StatKind.HEALTH ? Skill.HITPOINTS : Skill.PRAYER;
		int current = Rs2Player.getBoostedSkillLevel(skill);
		return comparator == ConditionComparator.ABOVE ? current > threshold : current < threshold;
	}

	private boolean checkNpcNearby()
	{
		if (name == null || name.isEmpty() || present == null)
		{
			return true;
		}
		boolean found = Microbot.getRs2NpcCache().query()
			.withName(name)
			.nearestOnClientThread(NEARBY_RADIUS) != null;
		return found == present;
	}

	private boolean checkObjectNearby()
	{
		if (name == null || name.isEmpty() || present == null)
		{
			return true;
		}
		boolean found = Microbot.getRs2TileObjectCache().query()
			.withName(name)
			.nearestOnClientThread(NEARBY_RADIUS) != null;
		return found == present;
	}

	private boolean checkInventory()
	{
		if (name == null || name.isEmpty() || present == null)
		{
			return true;
		}
		int need = minCount == null ? 1 : Math.max(1, minCount);
		int have = Rs2Inventory.count(name, true);
		boolean meets = have >= need;
		return meets == present;
	}

	public String describe()
	{
		if (type == null)
		{
			return "";
		}
		switch (type)
		{
			case STAT:
				if (stat == null || comparator == null || threshold == null)
				{
					return "";
				}
				String sn = stat == StatKind.HEALTH ? "HP" : "Prayer";
				String op = comparator == ConditionComparator.ABOVE ? ">" : "<";
				return "if " + sn + op + threshold;
			case NPC_NEARBY:
			case OBJECT_NEARBY:
				if (name == null || present == null)
				{
					return "";
				}
				return "if " + (present ? "" : "no ") + name + " nearby";
			case INVENTORY:
				if (name == null || present == null)
				{
					return "";
				}
				int need = minCount == null ? 1 : Math.max(1, minCount);
				if (present)
				{
					return "if inv has " + (need > 1 ? need + "x " : "") + name;
				}
				return "if inv lacks " + name;
			default:
				return "";
		}
	}
}
