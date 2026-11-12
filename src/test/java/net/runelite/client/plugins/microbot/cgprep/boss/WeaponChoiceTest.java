package net.runelite.client.plugins.microbot.cgprep.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 1.8.3: the fight picks from the weapons it has, and melees only from a boss side tile. */
class WeaponChoiceTest
{
	private static FightModel model(Style bossProtects, boolean bow, boolean staff, boolean melee)
	{
		FightObs o = new FightObs();
		o.bossPresent = true;
		o.bx = 4;
		o.by = 4;
		o.px = 6;
		o.py = 11;
		o.bossProtects = bossProtects;
		o.haveBow = bow;
		o.haveStaff = staff;
		o.haveMelee = melee;
		FightModel m = new FightModel();
		m.update(o);
		return m;
	}

	@Test
	void bothWeaponsAsBefore()
	{
		assertEquals(Style.RANGED, model(Style.MAGIC, true, true, true).weaponToUse());
		assertEquals(Style.MAGIC, model(Style.RANGED, true, true, true).weaponToUse());
	}

	@Test
	void noBowAndTheBossBlocksMagic_sceptre()
	{
		assertEquals(Style.MELEE, model(Style.MAGIC, false, true, true).weaponToUse());
		assertEquals(Style.MAGIC, model(Style.RANGED, false, true, true).weaponToUse());
	}

	@Test
	void noStaffAndTheBossBlocksRanged_sceptre()
	{
		assertEquals(Style.MELEE, model(Style.RANGED, true, false, true).weaponToUse());
		assertEquals(Style.RANGED, model(Style.MAGIC, true, false, true).weaponToUse());
	}

	@Test
	void unknownProtectionUsesWhatThereIs()
	{
		assertEquals(Style.RANGED, model(null, true, false, true).weaponToUse());
	}

	@Test
	void meleeTilesAreTheBossSidesOnly()
	{
		assertTrue(Arena.meleeTile(3, 6, 4, 4));   // west side
		assertTrue(Arena.meleeTile(6, 9, 4, 4));   // north side
		assertFalse(Arena.meleeTile(3, 3, 4, 4));  // corner
		assertFalse(Arena.meleeTile(5, 5, 4, 4));  // under it
		assertFalse(Arena.meleeTile(2, 6, 4, 4));  // two out
	}
}
