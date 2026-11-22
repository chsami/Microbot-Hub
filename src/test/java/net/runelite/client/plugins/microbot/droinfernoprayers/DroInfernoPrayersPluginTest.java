package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.nio.file.Files;
import java.nio.file.Paths;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.droinfernoprayers.prayer.Guard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DroInfernoPrayersPluginTest
{
	private final PluginDescriptor descriptor = DroInfernoPrayersPlugin.class.getAnnotation(PluginDescriptor.class);

	@Test
	void descriptor()
	{
		assertEquals(Plugin.class, DroInfernoPrayersPlugin.class.getSuperclass());
		assertEquals("[Dro] Inferno Prayers", descriptor.name());
		assertEquals("2.0.1", descriptor.version());
		assertTrue(descriptor.isExternal());
		assertFalse(descriptor.enabledByDefault());
	}

	@Test
	void docsExist()
	{
		String docs = "src/main/resources/net/runelite/client/plugins/microbot/droinfernoprayers/docs/";
		assertTrue(Files.isRegularFile(Paths.get(docs + "README.md")));
		assertTrue(Files.isRegularFile(Paths.get(docs + "assets/icon.png")));
		assertTrue(Files.isRegularFile(Paths.get(docs + "assets/card.png")));
	}

	@Test
	void nibblersAreMeleeAndOnlyWhenTargeting()
	{
		assertEquals(Guard.MELEE, InfernoMobs.of(NpcID.INFERNO_NIBBLER).guard);
		assertTrue(InfernoMobs.of(NpcID.INFERNO_NIBBLER).targetingOnlyAttacker());
	}

	@Test
	void zukPillarsAndHealersAreNotPrayedAgainst()
	{
		assertNull(InfernoMobs.of(NpcID.INFERNO_TZKALZUK_PLACEHOLDER));
		assertNull(InfernoMobs.of(NpcID.INFERNO_ZUK_HEALER));
		assertNull(InfernoMobs.of(NpcID.INFERNO_INVISIBLE_3X3));
	}

	@Test
	void blobAndJadAreAnsweredFromTheirAnimation()
	{
		assertFalse(InfernoMobs.of(NpcID.INFERNO_CREATURE_SPLITTER).steady());
		assertFalse(InfernoMobs.of(NpcID.INFERNO_JAD).steady());
	}
}
