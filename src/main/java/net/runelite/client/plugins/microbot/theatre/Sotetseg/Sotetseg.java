package net.runelite.client.plugins.microbot.theatre.Sotetseg;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.theatre.Room;
import net.runelite.client.plugins.microbot.theatre.TheatreConfig;
import net.runelite.client.plugins.microbot.theatre.TheatrePlugin;
import net.runelite.client.plugins.microbot.theatre.prayer.TheatrePrayerUtil;
import net.runelite.client.plugins.microbot.theatre.prayer.TheatreUpcomingAttack;

import javax.inject.Inject;
import java.util.*;

@Slf4j
public class Sotetseg extends Room
{
	@Inject
	private Client client;

	@Inject
	private TheatrePlugin plugin; //DO NOT USE. - Here for Counter Constructor

	@Inject
	private SotetsegOverlay sotetsegOverlay;

	@Inject
	private SotetsegPrayerOverlay sotetsegPrayerOverlay;

	@Inject
	protected Sotetseg(TheatrePlugin plugin, TheatreConfig config)
	{
		super(plugin, config);
	}

	@Getter
	private boolean sotetsegActive;

	@Getter
	private NPC sotetsegNPC;

	@Getter
	private LinkedHashSet<Point> redTiles = new LinkedHashSet<>();

	@Getter
	private HashSet<Point> greenTiles = new HashSet<>();

	@Getter
	private static final Point swMazeSquareOverWorld = new Point(9, 22);

	@Getter
	private static final Point swMazeSquareUnderWorld = new Point(42, 31);

	@Getter
	private boolean wasInUnderWorld = false;

	@Getter
	private int sotetsegTickCount = -1;

	private boolean offTick = false;

	private boolean bigOrbPresent = false;
	private boolean sotetsegBallCounted = false;

	static final int SOTETSEG_MAGE_ORB = 1606;
	static final int SOTETSEG_RANGE_ORB = 1607;
	static final int SOTETSEG_BIG_AOE_ORB = 1604;

	private static final int GROUNDOBJECT_ID_REDMAZE = 33035;
	private int overWorldRegionID = -1;

	@Getter(AccessLevel.PACKAGE)
	private long lastTick;

	@Getter(AccessLevel.PACKAGE)
	Queue<TheatreUpcomingAttack> upcomingAttackQueue = new PriorityQueue<>();

	@Getter
	private int attacksLeft = 10;

	@Override
	public void load()
	{
		overlayManager.add(sotetsegOverlay);
		overlayManager.add(sotetsegPrayerOverlay);
	}

	@Override
	public void unload()
	{
		overlayManager.remove(sotetsegOverlay);
		overlayManager.remove(sotetsegPrayerOverlay);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		NPC npc = npcSpawned.getNpc();
		switch (npc.getId())
		{
			case NpcID.SOTETSEG:
			case NpcID.SOTETSEG_8388:
			case 10864:
			case 10865:
			case 10867:
			case 10868:
				sotetsegActive = true;
				sotetsegNPC = npc;
				break;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		NPC npc = npcDespawned.getNpc();
		switch (npc.getId())
		{
			case NpcID.SOTETSEG:
			case NpcID.SOTETSEG_8388:
			case 10864:
			case 10865:
			case 10867:
			case 10868:
				if (client.getPlane() != 3)
				{
					sotetsegActive = false;
					sotetsegNPC = null;
					upcomingAttackQueue.clear();
					attacksLeft = 10;
				}
				break;
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor instanceof NPC)
		{
			if (actor == sotetsegNPC)
			{
				int animation = event.getActor().getAnimation();
				switch (animation)
				{
					case 8138:
					case 8139:
					{
						sotetsegTickCount = 6;
						upcomingAttackQueue.add(new TheatreUpcomingAttack(
							sotetsegTickCount,
							Prayer.PROTECT_FROM_MELEE,
							1
						));
					}
				}
			}
		}
	}

	private final Set<Projectile> handledProjectiles = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

	@Subscribe
	public void onProjectileMoved(ProjectileMoved projectileMoved)
	{
		if (!sotetsegActive)
		{
			return;
		}

		Projectile p = projectileMoved.getProjectile();
		if (p == null)
		{
			return;
		}

		if (handledProjectiles.contains(p))
		{
			if (p.getRemainingCycles() <= 0)
			{
				handledProjectiles.remove(p);
			}
			return;
		}

		handledProjectiles.add(p);

		if ((p.getInteracting() == client.getLocalPlayer()) && (p.getId() == Sotetseg.SOTETSEG_MAGE_ORB || p.getId() == Sotetseg.SOTETSEG_RANGE_ORB))
		{
			upcomingAttackQueue.add(new TheatreUpcomingAttack(
				(p.getRemainingCycles() / 30),
				(p.getId() == Sotetseg.SOTETSEG_MAGE_ORB ? Prayer.PROTECT_FROM_MAGIC : Prayer.PROTECT_FROM_MISSILES)
			));
		}

		if (p.getId() == SOTETSEG_BIG_AOE_ORB)
		{
			sotetsegTickCount = 11;
			attacksLeft = 10;
		}

		WorldPoint soteWp = WorldPoint.fromLocal(client, sotetsegNPC.getLocalLocation());
		WorldPoint projWp = WorldPoint.fromLocal(client, p.getX1(), p.getY1(), client.getPlane());
		if (p.getId() == SOTETSEG_MAGE_ORB && sotetsegNPC.getAnimation() == 8139 && projWp.equals(soteWp))
		{
			attacksLeft--;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (sotetsegActive)
		{
			lastTick = System.currentTimeMillis();
			TheatrePrayerUtil.updateNextPrayerQueue(getUpcomingAttackQueue());

			if (sotetsegNPC != null && (sotetsegNPC.getId() == NpcID.SOTETSEG_8388 || sotetsegNPC.getId() == 10865 || sotetsegNPC.getId() == 10868))
			{
				if (sotetsegTickCount >= 0)
				{
					sotetsegTickCount--;
				}

				if (!redTiles.isEmpty())
				{
					redTiles.clear();
					offTick = false;
				}

				if (!greenTiles.isEmpty())
				{
					greenTiles.clear();
				}

				if (inRoomRegion(TheatrePlugin.SOTETSEG_REGION_OVERWORLD))
				{
					wasInUnderWorld = false;
					if (client.getLocalPlayer() != null && client.getLocalPlayer().getWorldLocation() != null)
					{
						overWorldRegionID = client.getLocalPlayer().getWorldLocation().getRegionID();
					}
				}
			}

			if (config.sotetsegBigOrbTicks())
			{
				boolean foundBigOrb = false;
				for (Projectile p : client.getProjectiles())
				{
					if (p.getId() == SOTETSEG_BIG_AOE_ORB)
					{
						foundBigOrb = true;
						break;
					}
				}
				bigOrbPresent = foundBigOrb;
			}

			if (!bigOrbPresent)
			{
				sotetsegBallCounted = false;
			}

			if (bigOrbPresent && !sotetsegBallCounted)
			{
				sotetsegTickCount = 10;
				sotetsegBallCounted = true;
			}
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		if (sotetsegActive)
		{
			GroundObject o = event.getGroundObject();

			if (o.getId() == GROUNDOBJECT_ID_REDMAZE)
			{
				Tile t = event.getTile();
				WorldPoint p = WorldPoint.fromLocal(client, t.getLocalLocation());
				Point point = new Point(p.getRegionX(), p.getRegionY());
				if (inRoomRegion(TheatrePlugin.SOTETSEG_REGION_OVERWORLD))
				{
					redTiles.add(new Point(point.getX() - swMazeSquareOverWorld.getX(), point.getY() - swMazeSquareOverWorld.getY()));
				}
				if (inRoomRegion(TheatrePlugin.SOTETSEG_REGION_UNDERWORLD))
				{
					redTiles.add(new Point(point.getX() - swMazeSquareUnderWorld.getX(), point.getY() - swMazeSquareUnderWorld.getY()));
					wasInUnderWorld = true;
				}
			}
		}
	}

	WorldPoint worldPointFromMazePoint(Point mazePoint)
	{
		if (overWorldRegionID == -1 && client.getLocalPlayer() != null)
		{
			return WorldPoint.fromRegion(
				client.getLocalPlayer().getWorldLocation().getRegionID(), mazePoint.getX() + Sotetseg.getSwMazeSquareOverWorld().getX(),
				mazePoint.getY() + Sotetseg.getSwMazeSquareOverWorld().getY(), 0);
		}
		return WorldPoint.fromRegion(
			overWorldRegionID, mazePoint.getX() + Sotetseg.getSwMazeSquareOverWorld().getX(),
			mazePoint.getY() + Sotetseg.getSwMazeSquareOverWorld().getY(), 0);
	}

}
