package net.runelite.client.plugins.microbot.droinfernoprayers.prayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThreatReaderTest
{
	private static final int RANGER = 1, NIBBLER = 2, BLOB = 3, MAGER = 4, MINOTAUR = 5;
	private static final int SHOOT = 100, BITE = 101, BLOB_MAGIC = 102, SWING = 103;

	private final Map<Integer, MobSpec> specs = new HashMap<>();
	private final List<NPC> npcs = new ArrayList<>();
	private Client client;
	private Player me;
	private WorldView view;
	private WorldArea mine;
	private ThreatReader reader;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp()
	{
		specs.put(RANGER, MobSpec.steady("ranger", Guard.RANGE, 4, 15, 46).attack(SHOOT));
		specs.put(MAGER, MobSpec.steady("mager", Guard.MAGIC, 4, 15, 70).meleeInContact());
		specs.put(NIBBLER, MobSpec.steady("nibbler", Guard.MELEE, 4, 1, 4).targetingOnly().attack(BITE));
		specs.put(MINOTAUR, MobSpec.steady("minotaur", Guard.MELEE, 5, 1, 74).checkDelay(1).attack(SWING));
		specs.put(BLOB, MobSpec.reactive("blob", 29).reaction(BLOB_MAGIC, Guard.MAGIC, 1, 2));
		client = mock(Client.class);
		me = mock(Player.class);
		view = mock(WorldView.class);
		mine = mock(WorldArea.class);
		when(client.getLocalPlayer()).thenReturn(me);
		when(client.getTopLevelWorldView()).thenReturn(view);
		when(me.getWorldArea()).thenReturn(mine);
		IndexedObjectSet<NPC> set = mock(IndexedObjectSet.class);
		when(set.iterator()).thenAnswer(i -> npcs.iterator());
		doReturn(set).when(view).npcs();
		reader = new ThreatReader(specs::get);
	}

	private NPC npc(int id, int index, int distance, boolean sight)
	{
		NPC npc = mock(NPC.class);
		WorldArea area = mock(WorldArea.class);
		when(npc.getId()).thenReturn(id);
		when(npc.getIndex()).thenReturn(index);
		when(npc.getWorldArea()).thenReturn(area);
		when(area.distanceTo(mine)).thenReturn(distance);
		when(area.hasLineOfSightTo(any(WorldView.class), any(WorldArea.class))).thenReturn(sight);
		npcs.add(npc);
		return npc;
	}

	private Guard decide(int tick)
	{
		return GuardPlanner.decide(reader.read(client, tick), tick + 1, Guard.NONE).guard();
	}

	@Test
	void unseenRangerInSightIsDueNextTick()
	{
		npc(RANGER, 1, 6, true);
		assertEquals(Guard.RANGE, decide(10));
	}

	@Test
	void rangerFollowsItsClockAndIsOffBetweenAttacks()
	{
		NPC ranger = npc(RANGER, 1, 6, true);
		when(ranger.getAnimation()).thenReturn(SHOOT);
		reader.animation(ranger);
		assertEquals(Guard.NONE, decide(10), "just attacked: nothing due on tick 11");
		assertEquals(Guard.NONE, decide(11));
		assertEquals(Guard.NONE, decide(12));
		assertEquals(Guard.RANGE, decide(13), "attack 4 ticks later lands on tick 14");
	}

	@Test
	void unseenDelayedSwingerInContactIsPrayedNextTick()
	{
		npc(MINOTAUR, 1, 1, true);
		assertEquals(Guard.MELEE, decide(10));
		assertTrue(reader.read(client, 11).get(0).guessed());
	}

	@Test
	void delayedSwingIsCheckedOneTickAfterItsAnimation()
	{
		NPC minotaur = npc(MINOTAUR, 1, 1, true);
		when(minotaur.getAnimation()).thenReturn(SWING);
		reader.animation(minotaur);
		List<Threat> threats = reader.read(client, 10);
		assertEquals(1, threats.size());
		assertEquals(11, threats.get(0).tick(), "swing seen on tick 10 is checked on tick 11");
		assertFalse(threats.get(0).guessed());
		assertEquals(Guard.NONE, decide(11), "next swing on tick 15 is checked on tick 16");
		assertEquals(16, reader.read(client, 12).get(0).tick());
		assertEquals(Guard.MELEE, decide(15));
	}

	@Test
	void knownRangerBeatsAPrearmedMagerOutOfSight()
	{
		NPC ranger = npc(RANGER, 1, 6, true);
		npc(MAGER, 2, 8, false);
		when(me.getPoseAnimation()).thenReturn(819);
		when(me.getIdlePoseAnimation()).thenReturn(808);
		when(ranger.getAnimation()).thenReturn(SHOOT);
		reader.animation(ranger);
		assertEquals(Guard.MAGIC, decide(10), "only the guessed mager is due on tick 11");
		assertEquals(Guard.RANGE, decide(13), "the ranger's known shot on tick 14 wins");
	}

	@Test
	void hiddenBehindAPillarNothingIsPrayed()
	{
		npc(RANGER, 1, 6, false);
		npc(MAGER, 2, 8, false);
		assertEquals(Guard.NONE, decide(10));
	}

	@Test
	void steppingOutFromCoverPrearmsAReadyAttacker()
	{
		npc(RANGER, 1, 6, false);
		when(me.getPoseAnimation()).thenReturn(819);
		when(me.getIdlePoseAnimation()).thenReturn(808);
		assertEquals(Guard.RANGE, decide(10));
	}

	@Test
	void nibblersOnPillarsAreIgnored()
	{
		npc(NIBBLER, 1, 1, true);
		assertEquals(Guard.NONE, decide(10));
	}

	@Test
	void nibblerOnThePlayerIsMelee()
	{
		NPC nibbler = npc(NIBBLER, 1, 1, true);
		when(nibbler.getInteracting()).thenReturn((net.runelite.api.Actor) me);
		assertEquals(Guard.MELEE, decide(10));
	}

	@Test
	void magerInContactMelees()
	{
		npc(MAGER, 1, 1, true);
		assertEquals(Guard.MELEE, decide(10));
	}

	@Test
	void clearedWaveTurnsTheOverheadOff()
	{
		assertEquals(Guard.NONE, decide(10));
		assertEquals(0, reader.hostiles());
	}

	@Test
	void blobIsAnsweredOnlyAfterItShowsItsStyle()
	{
		NPC blob = npc(BLOB, 1, 6, true);
		assertEquals(Guard.NONE, decide(10));
		when(blob.getAnimation()).thenReturn(BLOB_MAGIC);
		reader.animation(blob);
		assertEquals(Guard.MAGIC, decide(11));
		assertEquals(Guard.MAGIC, decide(12));
		assertEquals(Guard.NONE, decide(13));
	}
}
