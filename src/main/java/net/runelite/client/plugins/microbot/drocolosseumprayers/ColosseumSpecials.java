package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.plugins.microbot.drocolosseumprayers.prayer.Guard;
import net.runelite.client.plugins.microbot.drocolosseumprayers.prayer.Threat;

/**
 * The two Colosseum attacks that do not follow a steady clock.
 *
 * <p><b>Manticore.</b> It charges three orbs and throws them one tick apart; each lands the tick it is thrown. The
 * first two are magic and ranged in either order, the third is melee (Mantimayhem III can shuffle them), and the
 * charged orbs show the order. The first orb's overhead is held while it is charged and can see the player; the
 * second and third are answered on the two ticks after the throw.</p>
 *
 * <p><b>Sol Heredit.</b> Only Triple Parry is prayable, with Protect from Melee. Its spears land three, six and
 * nine ticks after the animation (ten for the last below half health). Praying early makes Sol shut the prayer off,
 * so melee is only wanted on those exact ticks; every other Sol attack is typeless and gets no overhead.</p>
 */
final class ColosseumSpecials
{
	static final String MANTICORE = "Manticore";
	static final String SOL = "Sol Heredit";
	static final int MANTICORE_WEIGHT = 36;
	static final int MANTICORE_RANGE = 15;
	static final int SOL_WEIGHT = 45;
	private static final List<Guard> DEFAULT_ORDER = List.of(Guard.MAGIC, Guard.RANGE, Guard.MELEE);

	private static final class Seen
	{
		final int index;
		final int id;
		final int animation;
		final boolean belowHalf;

		Seen(int index, int id, int animation, boolean belowHalf)
		{
			this.index = index;
			this.id = id;
			this.animation = animation;
			this.belowHalf = belowHalf;
		}
	}

	private final Deque<Seen> seen = new ArrayDeque<>();
	private final Map<Integer, List<Guard>> orbOrder = new HashMap<>();
	private final Map<Integer, Integer> thrownAt = new HashMap<>();
	private final List<Threat> committed = new ArrayList<>();

	void clear()
	{
		seen.clear();
		orbOrder.clear();
		thrownAt.clear();
		committed.clear();
	}

	static boolean special(int npcId)
	{
		return npcId == NpcID.COLOSSEUM_MANTICORE || npcId == NpcID.COLOSSEUM_SOL_P1;
	}

	void despawned(int npcIndex)
	{
		orbOrder.remove(npcIndex);
		thrownAt.remove(npcIndex);
	}

	/** Client thread, from AnimationChanged. */
	void animation(NPC npc)
	{
		if (special(npc.getId()))
		{
			seen.add(new Seen(npc.getIndex(), npc.getId(), npc.getAnimation(), belowHalf(npc)));
		}
	}

	/** Special attacks due from {@code tick + 1}. */
	List<Threat> read(Client client, int tick)
	{
		int nextTick = tick + 1;
		stamp(tick);
		committed.removeIf(t -> t.tick() < nextTick);
		List<Threat> threats = new ArrayList<>(committed);
		Player me = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (me == null || view == null || me.getWorldArea() == null)
		{
			return threats;
		}
		WorldArea mine = me.getWorldArea();
		for (NPC npc : view.npcs())
		{
			if (npc == null || npc.getId() != NpcID.COLOSSEUM_MANTICORE || npc.isDead())
			{
				continue;
			}
			List<Guard> order = visibleOrder(npc);
			if (order.isEmpty())
			{
				continue;
			}
			orbOrder.put(npc.getIndex(), order);
			Integer thrown = thrownAt.get(npc.getIndex());
			if (thrown != null && tick - thrown <= 2)
			{
				continue;
			}
			WorldArea area = npc.getWorldArea();
			int distance = area == null ? Integer.MAX_VALUE : area.distanceTo(mine);
			if (distance >= 1 && distance <= MANTICORE_RANGE && area.hasLineOfSightTo(view, mine))
			{
				// The throw tick is not known in advance: holding the first orb is a guess.
				threats.add(new Threat(MANTICORE, order.get(0), nextTick, MANTICORE_WEIGHT, true));
			}
		}
		return threats;
	}

	private void stamp(int tick)
	{
		while (!seen.isEmpty())
		{
			Seen s = seen.poll();
			if (s.id == NpcID.COLOSSEUM_MANTICORE && s.animation == AnimationID.NPC_MANTICORE_01_TRIPLE_THROW)
			{
				List<Guard> order = orbOrder.getOrDefault(s.index, DEFAULT_ORDER);
				thrownAt.put(s.index, tick);
				for (int i = 1; i < order.size(); i++)
				{
					committed.add(new Threat(MANTICORE, order.get(i), tick + i, MANTICORE_WEIGHT));
				}
			}
			else if (s.id == NpcID.COLOSSEUM_SOL_P1 && (s.animation == AnimationID.NPC_COLOSSI_FINALBOSS_TRIPLEATTACK
				|| s.animation == AnimationID.NPC_COLOSSI_FINALBOSS_TRIPLEATTACK_SHORTER))
			{
				for (int offset : solParry(s.belowHalf))
				{
					committed.add(new Threat(SOL, Guard.MELEE, tick + offset, SOL_WEIGHT));
				}
			}
		}
	}

	static int[] solParry(boolean belowHalf)
	{
		return belowHalf ? new int[]{3, 6, 10} : new int[]{3, 6, 9};
	}

	private static boolean belowHalf(NPC npc)
	{
		int ratio = npc.getHealthRatio();
		int scale = npc.getHealthScale();
		return ratio >= 0 && scale > 0 && ratio * 2 < scale;
	}

	/** The charged orbs in the order they are thrown, or empty while the Manticore is not charged. */
	static List<Guard> visibleOrder(NPC npc)
	{
		List<int[]> orbs = new ArrayList<>();
		for (ActorSpotAnim spot : npc.getSpotAnims())
		{
			Guard guard = orbGuard(spot.getId());
			if (guard != Guard.NONE)
			{
				orbs.add(new int[]{guard.ordinal(), spot.getHeight(), spot.getStartCycle()});
			}
		}
		orbs.sort(Comparator.<int[]>comparingInt(o -> o[1]).thenComparingInt(o -> o[2]));
		List<Guard> order = new ArrayList<>(orbs.size());
		for (int[] orb : orbs)
		{
			order.add(Guard.values()[orb[0]]);
		}
		return orient(order);
	}

	/** Without a shuffle modifier melee comes last: a height order that starts with melee is read backwards. */
	static List<Guard> orient(List<Guard> order)
	{
		if (order.size() == 3 && order.get(0) == Guard.MELEE && order.get(2) != Guard.MELEE)
		{
			List<Guard> reversed = new ArrayList<>(order);
			Collections.reverse(reversed);
			return reversed;
		}
		return order;
	}

	static Guard orbGuard(int spotanimId)
	{
		if (spotanimId == SpotanimID.VFX_MANTICORE_01_PROJECTILE_MAGIC_01)
		{
			return Guard.MAGIC;
		}
		if (spotanimId == SpotanimID.VFX_MANTICORE_01_PROJECTILE_RANGED_01)
		{
			return Guard.RANGE;
		}
		if (spotanimId == SpotanimID.VFX_MANTICORE_01_PROJECTILE_MELEE_01)
		{
			return Guard.MELEE;
		}
		return Guard.NONE;
	}
}
