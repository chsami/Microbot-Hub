package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.drocolosseumprayers.prayer.Guard;
import net.runelite.client.plugins.microbot.drocolosseumprayers.prayer.MobSpec;

/**
 * Fortis Colosseum monsters with a steady, prayable attack. Speeds, ranges and max hits are from the OSRS wiki;
 * IDs are the client's own gameval names. The Manticore and Sol Heredit are handled by {@link ColosseumSpecials};
 * modifier hazards (bees, totems, scorpion, crystals) are not prayable and are left out.
 */
final class ColosseumMobs
{
	private static final Map<Integer, MobSpec> BY_ID = new HashMap<>();

	static
	{
		add(MobSpec.steady("Serpent shaman", Guard.MAGIC, 5, 10, 28)
			.attack(AnimationID.NPC_SERPENT_MAGER_CASTING), NpcID.COLOSSEUM_STANDARD_MAGER);

		add(MobSpec.steady("Jaguar warrior", Guard.MELEE, 5, 1, 47)
			.attack(AnimationID.NPC_JAGUAR_RANGER_CLAWS_ATTACK), NpcID.COLOSSEUM_JAGUAR_WARRIOR);

		// Damage is rolled one tick after the swing animation.
		add(MobSpec.steady("Minotaur", Guard.MELEE, 5, 1, 74).checkDelay(1)
			.attack(AnimationID.NPC_MINOTAUR_BOSS_ATTACK_MELEE)
			.attack(AnimationID.NPC_MINOTAUR_BOSS_ATTACK_MELEE_LOUDER), NpcID.COLOSSEUM_MINOTAUR, NpcID.COLOSSEUM_MINOTAUR_ROUTEFIND);

		// The warband only attacks from melee distance, each on its own six-tick cycle.
		add(MobSpec.steady("Warband archer", Guard.RANGE, 6, 1, 14).contactOnly()
			.attack(AnimationID.NPC_FREMENNIK_WARBANDER_ARCHER_ATT_COLOSSEUM), NpcID.COLOSSEUM_WARBANDER_RANGED_FEMALE);
		add(MobSpec.steady("Warband seer", Guard.MAGIC, 6, 1, 12).contactOnly()
			.attack(AnimationID.NPC_FREMENNIK_WARBANDER_MAGE_ZAROS_VERTICAL_CASTING_WALKMERGE), NpcID.COLOSSEUM_WARBANDER_MAGE_MALE);
		add(MobSpec.steady("Warband berserker", Guard.MELEE, 6, 1, 29)
			.attack(AnimationID.NPC_FREMENNIK_WARBANDER_MELEE_HUMAN_SWORD_STAB), NpcID.COLOSSEUM_WARBANDER_MELEE_MALE);

		// The thrown-javelin artillery is typeless and must be walked out of, but it still uses an attack slot.
		add(MobSpec.steady("Javelin Colossus", Guard.RANGE, 5, 15, 48)
			.attack(AnimationID.NPC_COLOSSI_JAVELIN_01_RANGE_ATTACK)
			.attack(AnimationID.NPC_COLOSSI_JAVELIN_01_ARTILLERY_ATTACK), NpcID.COLOSSEUM_JAVELIN_COLOSSUS);

		add(MobSpec.steady("Shockwave Colossus", Guard.MAGIC, 5, 15, 56)
			.attack(AnimationID.NPC_COLOSSI_SHOCKWAVE_01_CLAPATTACK), NpcID.COLOSSEUM_SHOCKWAVE_COLOSSUS);
	}

	private ColosseumMobs()
	{
	}

	private static void add(MobSpec spec, int... ids)
	{
		for (int id : ids)
		{
			BY_ID.put(id, spec);
		}
	}

	static MobSpec of(int npcId)
	{
		return BY_ID.get(npcId);
	}
}
