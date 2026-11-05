package net.runelite.client.plugins.microbot.cgprep.fight;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;

/**
 * Event-fed model of the Hunllef fight. Plugin @Subscribe handlers call the on*() methods (client
 * thread, cheap, no input); the fight reactor and the overlay read the getters (any thread).
 *
 * Mechanics (wiki, Corrupted Hunllef):
 *  - opens with RANGED; switches standard style after every 4th attack. Tornado summons and the
 *    prayer-disable orb count toward the 4; stomps don't.
 *  - switches its protection prayer after every 6th off-prayer hit from the player.
 *  - floor: tiles go WARNING then HIT; standing on HIT deals 10-20 per tick.
 *
 * Recon-confirmed (normal Gauntlet, 2026-10-26): attacks every 5 ticks (anim 8419 one tick before
 * the projectile); after the 4th attack of a style the boss plays the transition anim (8754 = now
 * MAGIC, 8755 = now RANGED) one tick later, i.e. before the next attack. Tornado summon (8418) and
 * the prayer-disable orb (1713, during magic) count toward the 4.
 */
public final class HunllefTracker
{
	public enum Style
	{
		RANGED, MAGIC
	}

	private volatile Style style = Style.RANGED;
	private volatile int attacksThisStyle;
	private volatile boolean prayerDisabledPending;
	private volatile int lastAttackTick = -100;
	private volatile int bossNpcId = -1;
	private volatile int playerOffPrayerHits;
	private volatile int tick;

	private final Set<WorldPoint> warningTiles = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Set<WorldPoint> hitTiles = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public void reset()
	{
		style = Style.RANGED;
		attacksThisStyle = 0;
		prayerDisabledPending = false;
		lastAttackTick = -100;
		bossNpcId = -1;
		playerOffPrayerHits = 0;
		warningTiles.clear();
		hitTiles.clear();
	}

	// ------------------------------------------------------------------ event inputs (client thread)

	public void onTick(int tick)
	{
		this.tick = tick;
	}

	public void onBossAnimation(int anim)
	{
		if (anim == CgIds.ANIM_HUNLLEF_SPECIAL)
		{
			onTornadoSummon(); // recon: tornado summon is one of the 4 attacks in a style cycle
			return;
		}
		if (anim == CgIds.ANIM_HUNLLEF_SWITCH_TO_MAGIC || anim == CgIds.ANIM_HUNLLEF_SWITCH_TO_MAGIC_LOW)
		{
			style = Style.MAGIC; // authoritative correction from the game itself
			attacksThisStyle = 0;
		}
		else if (anim == CgIds.ANIM_HUNLLEF_SWITCH_TO_RANGED || anim == CgIds.ANIM_HUNLLEF_SWITCH_TO_RANGED_LOW)
		{
			style = Style.RANGED;
			attacksThisStyle = 0;
		}
	}

	/** One boss projectile launched (call once per projectile). */
	public void onBossProjectile(int spotanimId)
	{
		if (tick == lastAttackTick)
		{
			return; // multi-part projectiles in one tick count once
		}
		lastAttackTick = tick;
		if (CgIds.PROJ_HUNLLEF_PRAYER_DISABLE.contains(spotanimId))
		{
			prayerDisabledPending = true;
		}
		Style observed = CgIds.PROJ_HUNLLEF_MAGIC.contains(spotanimId) ? Style.MAGIC
			: CgIds.PROJ_HUNLLEF_RANGED.contains(spotanimId) ? Style.RANGED : null;
		if (observed != null && observed != style)
		{
			// We were out of sync: trust what the game just showed and restart the count.
			style = observed;
			attacksThisStyle = 0;
		}
		attacksThisStyle++;
		if (attacksThisStyle >= 4)
		{
			style = style == Style.RANGED ? Style.MAGIC : Style.RANGED;
			attacksThisStyle = 0;
		}
	}

	/** Tornado summon (anim 8418) counts as an attack in the 4-cycle (recon-confirmed). */
	public void onTornadoSummon()
	{
		onBossProjectile(-1);
	}

	public void onBossIdChanged(int newId)
	{
		bossNpcId = newId;
		playerOffPrayerHits = 0;
	}

	/** Player landed an attack the boss isn't protecting against. TODO(recon): player attack anims. */
	public void onPlayerOffPrayerAttack()
	{
		playerOffPrayerHits++;
	}

	public void onFloorObject(int objectId, WorldPoint raw, boolean spawned)
	{
		Set<WorldPoint> set = CgIds.FLOOR_WARNING.contains(objectId) ? warningTiles
			: CgIds.FLOOR_HIT.contains(objectId) ? hitTiles : null;
		if (set == null || raw == null)
		{
			return;
		}
		if (spawned)
		{
			set.add(raw);
		}
		else
		{
			set.remove(raw);
		}
	}

	public void consumePrayerDisabled()
	{
		prayerDisabledPending = false;
	}

	// ------------------------------------------------------------------ reads

	/** The style the NEXT standard attack will use (what to pray against). */
	public Style nextStyle()
	{
		return style;
	}

	public int attacksThisStyle()
	{
		return attacksThisStyle;
	}

	public boolean prayerDisabledPending()
	{
		return prayerDisabledPending;
	}

	public int playerOffPrayerHits()
	{
		return playerOffPrayerHits;
	}

	public int bossNpcId()
	{
		return bossNpcId;
	}

	public Set<WorldPoint> warningTiles()
	{
		return warningTiles;
	}

	public Set<WorldPoint> hitTiles()
	{
		return hitTiles;
	}

	/** Protected style from the boss's NPC id (hypothesis, see CgIds). */
	public String bossProtects()
	{
		int id = bossNpcId;
		if (CgIds.HUNLLEF_PROTECT_MELEE.contains(id))
		{
			return "MELEE";
		}
		if (CgIds.HUNLLEF_PROTECT_RANGED.contains(id))
		{
			return "RANGED";
		}
		if (CgIds.HUNLLEF_PROTECT_MAGIC.contains(id))
		{
			return "MAGIC";
		}
		return "?";
	}
}
