package net.runelite.client.plugins.microbot.drocolosseumprayers.prayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;

/**
 * Turns what the client shows into upcoming attacks. Animations are queued as they arrive and stamped with the
 * tick on the next GameTick, so every clock uses the same tick numbering as the planner.
 */
public final class ThreatReader
{
	private static final class Seen
	{
		final int index;
		final int id;
		final int animation;

		Seen(int index, int id, int animation)
		{
			this.index = index;
			this.id = id;
			this.animation = animation;
		}
	}

	private final IntFunction<MobSpec> specs;
	private final AttackClock clock = new AttackClock();
	private final Deque<Seen> seen = new ArrayDeque<>();
	private final List<Threat> committed = new ArrayList<>();
	private int hostiles;

	public ThreatReader(IntFunction<MobSpec> specs)
	{
		this.specs = specs;
	}

	public void clear()
	{
		clock.clear();
		seen.clear();
		committed.clear();
		hostiles = 0;
	}

	/** Client thread, from AnimationChanged. */
	public void animation(NPC npc)
	{
		if (specs.apply(npc.getId()) != null)
		{
			seen.add(new Seen(npc.getIndex(), npc.getId(), npc.getAnimation()));
		}
	}

	public void despawned(int npcIndex)
	{
		clock.forget(npcIndex);
	}

	/** Adds an attack that has already been committed, checked on {@code tick}. */
	public void commit(Threat threat)
	{
		committed.add(threat);
	}

	/** Monsters seen this tick that can attack the player at all. */
	public int hostiles()
	{
		return hostiles;
	}

	/**
	 * Every attack due from {@code tick + 1} on.
	 *
	 * @param tick the tick the GameTick just reported
	 */
	public List<Threat> read(Client client, int tick)
	{
		int nextTick = tick + 1;
		stamp(tick);
		committed.removeIf(t -> t.tick() < nextTick);
		List<Threat> threats = new ArrayList<>(committed);
		hostiles = 0;
		Player me = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (me == null || view == null)
		{
			return threats;
		}
		WorldArea mine = me.getWorldArea();
		boolean meMoving = moving(me);
		for (NPC npc : view.npcs())
		{
			MobSpec spec = npc == null ? null : specs.apply(npc.getId());
			if (spec == null || npc.isDead())
			{
				continue;
			}
			hostiles++;
			if (!spec.steady())
			{
				continue;
			}
			if (spec.targetingOnlyAttacker() && npc.getInteracting() != me)
			{
				continue;
			}
			WorldArea area = npc.getWorldArea();
			if (area == null || mine == null)
			{
				continue;
			}
			int distance = area.distanceTo(mine);
			boolean contact = distance == 1;
			boolean shifting = meMoving || moving(npc);
			Guard guard = spec.meleeInContactAttacker() && contact ? Guard.MELEE : spec.guard;
			int delay = spec.checkDelay();
			Integer last = clock.lastAttack(npc.getIndex());
			// The tick the last attack's rhythm puts the next check on; unknown until it has attacked once.
			Integer rhythm = last == null ? null : last + spec.speed + delay;
			int due = rhythm == null ? nextTick : Math.max(nextTick, rhythm);
			// An unknown or overdue clock is a guess: the monster may attack on any tick.
			boolean guessed = rhythm == null || rhythm < nextTick;
			if (delay > 0 && last != null && tick - last <= delay - 1)
			{
				// A swing seen this recently is still checked after its delay: that check is known.
				due = last + delay;
				guessed = false;
			}
			boolean ready = due == nextTick;
			boolean reaches;
			if (spec.contactOnlyAttacker() || guard == Guard.MELEE)
			{
				// One step can close a two-tile gap before the next check.
				reaches = contact || shifting && ready && distance == 2;
				guessed |= !contact;
			}
			else
			{
				boolean inRange = distance >= 1 && distance <= spec.range;
				// Out of sight now, but a step by either side can open the line before the next check.
				boolean sight = area.hasLineOfSightTo(view, mine);
				reaches = inRange && (sight || shifting && ready);
				guessed |= !sight;
			}
			if (reaches)
			{
				threats.add(new Threat(spec.name, guard, due, spec.weight, guessed));
			}
		}
		return threats;
	}

	private void stamp(int tick)
	{
		while (!seen.isEmpty())
		{
			Seen s = seen.poll();
			MobSpec spec = specs.apply(s.id);
			if (spec == null)
			{
				continue;
			}
			if (spec.isAttack(s.animation))
			{
				clock.attacked(s.index, tick);
			}
			int quiet = spec.quietTicks(s.animation);
			if (quiet >= 0)
			{
				clock.attacked(s.index, tick + quiet - spec.speed);
			}
			MobSpec.Reaction reaction = spec.reaction(s.animation);
			if (reaction != null)
			{
				for (int offset : reaction.ticks)
				{
					committed.add(new Threat(spec.name, reaction.guard, tick + offset, spec.weight));
				}
			}
		}
	}

	private static boolean moving(Actor actor)
	{
		return actor.getPoseAnimation() != actor.getIdlePoseAnimation();
	}

	/** Committed attacks, for tests. */
	Iterator<Threat> committed()
	{
		return committed.iterator();
	}
}
