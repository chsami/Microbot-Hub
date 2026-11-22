package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.droinfernoprayers.prayer.Guard;
import net.runelite.client.plugins.microbot.droinfernoprayers.prayer.MobSpec;

/**
 * Inferno monsters and how their attacks are prayed against. Speeds, ranges and max hits are from the OSRS wiki;
 * IDs are the client's own gameval names. TzKal-Zuk, his healers and the pillars are typeless or harmless and are
 * left out on purpose.
 */
final class InfernoMobs
{
	private static final Map<Integer, MobSpec> BY_ID = new HashMap<>();

	static
	{
		// Only bothers the player once the pillars are gone and it targets them.
		add(MobSpec.steady("Jal-Nib", Guard.MELEE, 4, 1, 4).targetingOnly()
			.attack(AnimationID.JALNIB_ATTACK), NpcID.INFERNO_NIBBLER);

		add(MobSpec.steady("Jal-MejRah", Guard.RANGE, 3, 4, 19)
			.attack(AnimationID.JALMEJRAH_ATTACK), NpcID.INFERNO_CREATURE_HARPIE);

		// The blob reads the overhead three ticks ahead and then attacks with a style it is not protected from.
		// Its animation shows the style before the projectile leaves, so it is answered from the animation.
		add(MobSpec.reactive("Jal-Ak", 29)
			.reaction(AnimationID.JALAK_ATTACK_MAGIC, Guard.MAGIC, 1, 2)
			.reaction(AnimationID.JALAK_ATTACK_RANGED, Guard.RANGE, 1, 2)
			.reaction(AnimationID.JALAK_ATTACK_MELEE, Guard.MELEE, 1), NpcID.INFERNO_CREATURE_SPLITTER);

		add(MobSpec.steady("Jal-AkRek-Mej", Guard.MAGIC, 4, 15, 18)
			.attack(AnimationID.JALAK_ATTACK_MAGIC), NpcID.INFERNO_CREATURE_SPLITTER_MAGE);
		add(MobSpec.steady("Jal-AkRek-Xil", Guard.RANGE, 4, 15, 18)
			.attack(AnimationID.JALAK_ATTACK_RANGED), NpcID.INFERNO_CREATURE_SPLITTER_RANGE);
		add(MobSpec.steady("Jal-AkRek-Ket", Guard.MELEE, 4, 1, 18)
			.attack(AnimationID.JALAK_ATTACK_MELEE), NpcID.INFERNO_CREATURE_SPLITTER_MELEE);

		// Surfaces next to the player and waits six ticks before its first swing.
		add(MobSpec.steady("Jal-ImKot", Guard.MELEE, 4, 1, 49)
			.attack(AnimationID.JALIMKOT_ATTACK)
			.quiet(AnimationID.JALIMKOT_DIGUP, 6), NpcID.INFERNO_CREATURE_MELEE, NpcID.INFERNO_CREATURE_MELEE_SMALL);

		add(MobSpec.steady("Jal-Xil", Guard.RANGE, 4, 15, 46).meleeInContact()
			.attack(AnimationID.JALXIL_ATTACK_RANGED)
			.attack(AnimationID.JALXIL_ATTACK_MELEE), NpcID.INFERNO_CREATURE_RANGER, NpcID.INFERNO_RANGER_FINALWAVE);

		// A revive replaces an attack; it attacks again eight ticks later.
		add(MobSpec.steady("Jal-Zek", Guard.MAGIC, 4, 15, 70).meleeInContact()
			.attack(AnimationID.JALAKXIL_ATTACK_MAGIC)
			.attack(AnimationID.JALAKXIL_ATTACK_MELEE)
			.quiet(AnimationID.JALAKXIL_RESURRECT, 8), NpcID.INFERNO_CREATURE_MAGER, NpcID.INFERNO_MAGER_FINALWAVE);

		// Jad shows the style at the start of its animation; the prayer is held until the hit.
		add(MobSpec.reactive("JalTok-Jad", 113)
			.reaction(AnimationID.JALTOKJAD_ATTACK_MAGIC, Guard.MAGIC, 1, 2, 3)
			.reaction(AnimationID.JALTOKJAD_ATTACK_RANGED, Guard.RANGE, 1, 2, 3)
			.reaction(AnimationID.JALTOKJAD_ATTACK_MELEE, Guard.MELEE, 1), NpcID.INFERNO_JAD, NpcID.INFERNO_JAD_FINALWAVE);

		// Healers only fight back once they are attacked.
		add(MobSpec.steady("Yt-HurKot", Guard.MELEE, 4, 1, 18).targetingOnly(),
			NpcID.INFERNO_JAD_HEALER, NpcID.INFERNO_JAD_HEALER_FINALWAVE);
	}

	private InfernoMobs()
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
