package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.drocolosseumprayers.prayer.Guard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DroColosseumPrayersPluginTest
{
	private final PluginDescriptor descriptor = DroColosseumPrayersPlugin.class.getAnnotation(PluginDescriptor.class);

	@Test
	void descriptor()
	{
		assertEquals(Plugin.class, DroColosseumPrayersPlugin.class.getSuperclass());
		assertEquals("[Dro] Colosseum Prayers", descriptor.name());
		assertEquals("2.0.1", descriptor.version());
		assertTrue(descriptor.isExternal());
		assertFalse(descriptor.enabledByDefault());
	}

	@Test
	void docsExist()
	{
		String docs = "src/main/resources/net/runelite/client/plugins/microbot/drocolosseumprayers/docs/";
		assertTrue(Files.isRegularFile(Paths.get(docs + "README.md")));
		assertTrue(Files.isRegularFile(Paths.get(docs + "assets/icon.png")));
		assertTrue(Files.isRegularFile(Paths.get(docs + "assets/card.png")));
	}

	@Test
	void warbandOnlyAttacksFromMeleeDistance()
	{
		assertTrue(ColosseumMobs.of(NpcID.COLOSSEUM_WARBANDER_RANGED_FEMALE).contactOnlyAttacker());
		assertTrue(ColosseumMobs.of(NpcID.COLOSSEUM_WARBANDER_MAGE_MALE).contactOnlyAttacker());
		assertEquals(Guard.RANGE, ColosseumMobs.of(NpcID.COLOSSEUM_WARBANDER_RANGED_FEMALE).guard);
	}

	@Test
	void manticoreAndSolAreSpecials()
	{
		assertNull(ColosseumMobs.of(NpcID.COLOSSEUM_MANTICORE));
		assertNull(ColosseumMobs.of(NpcID.COLOSSEUM_SOL_P1));
		assertTrue(ColosseumSpecials.special(NpcID.COLOSSEUM_MANTICORE));
		assertTrue(ColosseumSpecials.special(NpcID.COLOSSEUM_SOL_P1));
	}

	@Test
	void manticoreOrbsMapToOverheads()
	{
		assertEquals(Guard.MAGIC, ColosseumSpecials.orbGuard(SpotanimID.VFX_MANTICORE_01_PROJECTILE_MAGIC_01));
		assertEquals(Guard.RANGE, ColosseumSpecials.orbGuard(SpotanimID.VFX_MANTICORE_01_PROJECTILE_RANGED_01));
		assertEquals(Guard.MELEE, ColosseumSpecials.orbGuard(SpotanimID.VFX_MANTICORE_01_PROJECTILE_MELEE_01));
		assertEquals(Guard.NONE, ColosseumSpecials.orbGuard(1));
	}

	@Test
	void manticoreMeleeOrbIsThrownLast()
	{
		assertEquals(List.of(Guard.RANGE, Guard.MAGIC, Guard.MELEE),
			ColosseumSpecials.orient(List.of(Guard.MELEE, Guard.MAGIC, Guard.RANGE)));
		assertEquals(List.of(Guard.MAGIC, Guard.RANGE, Guard.MELEE),
			ColosseumSpecials.orient(List.of(Guard.MAGIC, Guard.RANGE, Guard.MELEE)));
	}

	@Test
	void solTripleParryTicks()
	{
		assertArrayEquals(new int[]{3, 6, 9}, ColosseumSpecials.solParry(false));
		assertArrayEquals(new int[]{3, 6, 10}, ColosseumSpecials.solParry(true));
	}
}
