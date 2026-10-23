package net.runelite.client.plugins.microbot.HueycoatlPrayer;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HueyPrayerBalanceTest
{
	private static final List<String> TRIO = Arrays.asList("Alice", "Bob", "Charlie");

	@Test
	void prayersStayUpOnlyInTheArenaWhileHueyIsAlive()
	{
		assertTrue(HueyPrayerBalance.shouldHoldPrayers(true, true));
		assertFalse(HueyPrayerBalance.shouldHoldPrayers(true, false));
		assertFalse(HueyPrayerBalance.shouldHoldPrayers(false, true));
	}

	@Test
	void defeatedHeadIsArenaButNotAlive()
	{
		assertTrue(HueyPrayerBalance.isLivingForm(NpcID.HUEY_HEAD));
		assertTrue(HueyPrayerBalance.isLivingForm(NpcID.HUEY_TAIL));
		assertTrue(HueyPrayerBalance.isLivingForm(NpcID.HUEY_BODY_PART));
		assertFalse(HueyPrayerBalance.isLivingForm(NpcID.HUEY_HEAD_DEFEATED));
		assertFalse(HueyPrayerBalance.isLivingForm(NpcID.HUEY_HEAD_RESPAWN_PLACEHOLDER));
		assertTrue(HueyPrayerBalance.isArenaPresence(NpcID.HUEY_HEAD_DEFEATED));
		assertTrue(HueyPrayerBalance.isArenaPresence(NpcID.HUEY_SEER));
		assertFalse(HueyPrayerBalance.isArenaPresence(NpcID.HUEY_LOBBY_SEER));
	}

	@Test
	void projectileStillInFlightOnItsEndCycle()
	{
		assertFalse(HueyPrayerBalance.hasLanded(1000, 1000));
		assertTrue(HueyPrayerBalance.hasLanded(1001, 1000));
	}

	@Test
	void sceneDistanceIsTilesInsideOneWorldView()
	{
		assertEquals(4, HueyPrayerBalance.sceneTileDistance(10, 10, 1, 13, 14, 1));
		assertEquals(Integer.MAX_VALUE, HueyPrayerBalance.sceneTileDistance(10, 10, 1, 11, 10, 2));
	}

	@Test
	void staysOnTheUncoveredStyleWhenTeammatesCoverTheOtherTwo()
	{
		Rs2PrayerEnum chosen = HueyPrayerBalance.chooseAutobalance(
			1,
			1,
			0,
			Rs2PrayerEnum.PROTECT_MAGIC,
			Collections.emptyList(),
			TRIO,
			"Alice");

		assertEquals(Rs2PrayerEnum.PROTECT_MAGIC, chosen);
	}

	@Test
	void onlyTheLastNameOnACrowdedStyleMoves()
	{
		List<String> alsoOnMagic = Arrays.asList("Alice", "Bob");

		Rs2PrayerEnum alice = HueyPrayerBalance.chooseAutobalance(
			0, 0, 2, Rs2PrayerEnum.PROTECT_MAGIC, Arrays.asList("Bob", "Charlie"), TRIO, "Alice");
		Rs2PrayerEnum charlie = HueyPrayerBalance.chooseAutobalance(
			0, 0, 2, Rs2PrayerEnum.PROTECT_MAGIC, alsoOnMagic, TRIO, "Charlie");

		assertEquals(Rs2PrayerEnum.PROTECT_MAGIC, alice);
		assertEquals(Rs2PrayerEnum.PROTECT_MELEE, charlie);
	}

	@Test
	void unreadableTeammateOverheadsAreSplitByName()
	{
		assertEquals(Rs2PrayerEnum.PROTECT_MELEE, HueyPrayerBalance.chooseAutobalance(
			0, 0, 0, Rs2PrayerEnum.PROTECT_MAGIC, Collections.emptyList(), TRIO, "Alice"));
		assertEquals(Rs2PrayerEnum.PROTECT_RANGE, HueyPrayerBalance.chooseAutobalance(
			0, 0, 0, Rs2PrayerEnum.PROTECT_MAGIC, Collections.emptyList(), TRIO, "Bob"));
		assertEquals(Rs2PrayerEnum.PROTECT_MAGIC, HueyPrayerBalance.chooseAutobalance(
			0, 0, 0, Rs2PrayerEnum.PROTECT_MAGIC, Collections.emptyList(), TRIO, "Charlie"));
	}

	@Test
	void soloWithNoTeammatesKeepsTheCurrentPrayer()
	{
		assertEquals(Rs2PrayerEnum.PROTECT_RANGE, HueyPrayerBalance.chooseAutobalance(
			0, 0, 0, Rs2PrayerEnum.PROTECT_RANGE, Collections.emptyList(), Collections.singletonList("Alice"), "Alice"));
	}
}
