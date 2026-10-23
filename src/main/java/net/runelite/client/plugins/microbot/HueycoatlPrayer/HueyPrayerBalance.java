package net.runelite.client.plugins.microbot.HueycoatlPrayer;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure decisions for Huey prayer: when a projectile has landed, and which pillar
 * protection to take from teammate overhead counts.
 */
public final class HueyPrayerBalance
{
	private HueyPrayerBalance()
	{
	}

	/**
	 * True only after the projectile's end cycle. Equality is still the impact
	 * tick, and protection has to stay up until that tick has passed.
	 */
	public static boolean hasLanded(int gameCycle, int endCycle)
	{
		return gameCycle > endCycle;
	}

	/**
	 * Pillar prayers stay up only inside the arena while a living Huey form is present.
	 * After the kill, or outside the arena, they stay off until the next attack projectile.
	 */
	public static boolean shouldHoldPrayers(boolean inArena, boolean hueyAlive)
	{
		if (!inArena)
		{
			return false;
		}
		return hueyAlive;
	}

	public static boolean isLivingForm(int npcId)
	{
		if (npcId == NpcID.HUEY_HEAD)
		{
			return true;
		}
		if (npcId == NpcID.HUEY_HEAD_INVULNERABLE)
		{
			return true;
		}
		if (npcId == NpcID.HUEY_HEAD_ENRAGED)
		{
			return true;
		}
		if (npcId == NpcID.HUEY_TAIL)
		{
			return true;
		}
		if (npcId == NpcID.HUEY_BODY_PART)
		{
			return true;
		}
		return false;
	}

	public static boolean isArenaPresence(int npcId)
	{
		if (isLivingForm(npcId))
		{
			return true;
		}
		if (npcId == NpcID.HUEY_HEAD_DEFEATED)
		{
			return true;
		}
		if (npcId == NpcID.HUEY_HEAD_RESPAWN_PLACEHOLDER)
		{
			return true;
		}
		if (npcId == NpcID.HUEY_TAIL_BROKEN)
		{
			return true;
		}
		if (npcId == NpcID.HUEY_BODY_PART_BROKEN)
		{
			return true;
		}
		if (npcId == NpcID.HUEY_SEER)
		{
			return true;
		}
		return false;
	}

	public static int sceneTileDistance(int sceneX1, int sceneY1, int worldView1, int sceneX2, int sceneY2, int worldView2)
	{
		if (worldView1 != worldView2)
		{
			return Integer.MAX_VALUE;
		}
		int dx = Math.abs(sceneX1 - sceneX2);
		int dy = Math.abs(sceneY1 - sceneY2);
		if (dx > dy)
		{
			return dx;
		}
		return dy;
	}

	/**
	 * Pick a pillar prayer from other players' overhead counts.
	 * <p>
	 * Counts are teammates only. When several styles are tied for least covered,
	 * the choice is the local player's index in {@code groupNamesSorted} so three
	 * clients do not all take melee. A player already on a least-covered style
	 * stays. If their style is crowded, only the alphabetically last player on
	 * that style switches this call, which lets a trio converge over a few ticks
	 * instead of all moving together.
	 */
	public static Rs2PrayerEnum chooseAutobalance(
		int otherMelee,
		int otherRanged,
		int otherMagic,
		Rs2PrayerEnum myCurrent,
		List<String> teammateNamesOnMyStyle,
		List<String> groupNamesSorted,
		String myName)
	{
		int groupSize = 0;
		if (groupNamesSorted != null)
		{
			groupSize = groupNamesSorted.size();
		}
		if (otherMelee == 0)
		{
			if (otherRanged == 0)
			{
				if (otherMagic == 0)
				{
					if (groupSize <= 1)
					{
						if (myCurrent != null)
						{
							return myCurrent;
						}
						return Rs2PrayerEnum.PROTECT_MELEE;
					}
					return styleForIndex(indexOf(groupNamesSorted, myName));
				}
			}
		}
		if (myCurrent == null)
		{
			return pickLeastCovered(otherMelee, otherRanged, otherMagic, groupNamesSorted, myName);
		}
		int myOthers = othersOn(myCurrent, otherMelee, otherRanged, otherMagic);
		int min = otherMelee;
		if (otherRanged < min)
		{
			min = otherRanged;
		}
		if (otherMagic < min)
		{
			min = otherMagic;
		}
		if (myOthers <= min)
		{
			return myCurrent;
		}
		if (!iShouldYield(myName, teammateNamesOnMyStyle))
		{
			return myCurrent;
		}
		return pickLeastCovered(otherMelee, otherRanged, otherMagic, groupNamesSorted, myName);
	}

	private static boolean iShouldYield(String myName, List<String> teammateNamesOnMyStyle)
	{
		if (myName == null)
		{
			return true;
		}
		String highest = myName;
		if (teammateNamesOnMyStyle != null)
		{
			for (String name : teammateNamesOnMyStyle)
			{
				if (name == null)
				{
					continue;
				}
				if (name.compareToIgnoreCase(highest) > 0)
				{
					highest = name;
				}
			}
		}
		return myName.equalsIgnoreCase(highest);
	}

	private static int othersOn(Rs2PrayerEnum style, int otherMelee, int otherRanged, int otherMagic)
	{
		if (style == Rs2PrayerEnum.PROTECT_MELEE)
		{
			return otherMelee;
		}
		if (style == Rs2PrayerEnum.PROTECT_RANGE)
		{
			return otherRanged;
		}
		if (style == Rs2PrayerEnum.PROTECT_MAGIC)
		{
			return otherMagic;
		}
		return 0;
	}

	private static Rs2PrayerEnum pickLeastCovered(
		int otherMelee,
		int otherRanged,
		int otherMagic,
		List<String> groupNamesSorted,
		String myName)
	{
		int min = otherMelee;
		if (otherRanged < min)
		{
			min = otherRanged;
		}
		if (otherMagic < min)
		{
			min = otherMagic;
		}
		List<Rs2PrayerEnum> tied = new ArrayList<Rs2PrayerEnum>();
		if (otherMelee == min)
		{
			tied.add(Rs2PrayerEnum.PROTECT_MELEE);
		}
		if (otherRanged == min)
		{
			tied.add(Rs2PrayerEnum.PROTECT_RANGE);
		}
		if (otherMagic == min)
		{
			tied.add(Rs2PrayerEnum.PROTECT_MAGIC);
		}
		int index = indexOf(groupNamesSorted, myName);
		int slot = Math.floorMod(index, tied.size());
		return tied.get(slot);
	}

	private static Rs2PrayerEnum styleForIndex(int index)
	{
		int slot = Math.floorMod(index, 3);
		if (slot == 0)
		{
			return Rs2PrayerEnum.PROTECT_MELEE;
		}
		if (slot == 1)
		{
			return Rs2PrayerEnum.PROTECT_RANGE;
		}
		return Rs2PrayerEnum.PROTECT_MAGIC;
	}

	private static int indexOf(List<String> groupNamesSorted, String myName)
	{
		if (groupNamesSorted == null)
		{
			return 0;
		}
		if (myName == null)
		{
			return 0;
		}
		for (int i = 0; i < groupNamesSorted.size(); i++)
		{
			String name = groupNamesSorted.get(i);
			if (name == null)
			{
				continue;
			}
			if (name.equalsIgnoreCase(myName))
			{
				return i;
			}
		}
		return 0;
	}
}
