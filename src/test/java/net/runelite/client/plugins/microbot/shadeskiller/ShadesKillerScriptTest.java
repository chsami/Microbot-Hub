package net.runelite.client.plugins.microbot.shadeskiller;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shadeskiller.enums.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ShadesKillerScriptTest
{
	@Test
	void lowHpEscapeOnlyFiresWhereThePlayerCanBeInDanger()
	{
		assertTrue(ShadesKillerScript.isLowHpEscapeState(State.FIGHT_SHADES));
		assertTrue(ShadesKillerScript.isLowHpEscapeState(State.WALK_TO_SHADES));
		assertFalse(ShadesKillerScript.isLowHpEscapeState(State.BANKING));
		assertFalse(ShadesKillerScript.isLowHpEscapeState(State.USE_TELEPORT_TO_SHADES));
		assertFalse(ShadesKillerScript.isLowHpEscapeState(State.USE_TELEPORT_TO_BANK));
	}

	@Test
	void teleportIsSeenOnlyAfterALargeMove()
	{
		WorldPoint varrockBank = new WorldPoint(3253, 3420, 0);
		WorldPoint barrows = new WorldPoint(3565, 3314, 0);
		assertTrue(ShadesKillerScript.hasTeleported(varrockBank, barrows));
		assertTrue(ShadesKillerScript.hasTeleported(varrockBank, new WorldPoint(3253, 3420, 1)));
		assertFalse(ShadesKillerScript.hasTeleported(varrockBank, varrockBank));
		assertFalse(ShadesKillerScript.hasTeleported(varrockBank, new WorldPoint(3253 + ShadesKillerScript.TELEPORT_MIN_DISTANCE, 3420, 0)));
		assertTrue(ShadesKillerScript.hasTeleported(varrockBank, new WorldPoint(3254 + ShadesKillerScript.TELEPORT_MIN_DISTANCE, 3420, 0)));
	}

	@Test
	void teleportIsNotSeenWithoutAPosition()
	{
		WorldPoint varrockBank = new WorldPoint(3253, 3420, 0);
		assertFalse(ShadesKillerScript.hasTeleported(null, varrockBank));
		assertFalse(ShadesKillerScript.hasTeleported(varrockBank, null));
	}

	@Test
	void bankWithoutTheSuppliesStopsAfterThreeFailedVisitsAndNamesTheSupply()
	{
		int attempts = 0;
		int visits = 0;
		while (!ShadesKillerScript.shouldStopAfterFailedWithdraws(attempts) && visits < 50)
		{
			attempts = ShadesKillerScript.failedWithdrawAttemptsAfter(attempts, false);
			visits++;
		}
		assertEquals(3, visits);
		assertTrue(ShadesKillerScript.shouldStopAfterFailedWithdraws(attempts));
		assertEquals("a silver key", ShadesKillerScript.missingSupply(false, false, false, "barrows teleport", "varrock teleport", "Monkfish", 6));
		assertEquals("barrows teleport", ShadesKillerScript.missingSupply(true, false, false, "barrows teleport", "varrock teleport", "Monkfish", 6));
		assertEquals("varrock teleport", ShadesKillerScript.missingSupply(true, true, false, "barrows teleport", "varrock teleport", "Monkfish", 6));
		assertEquals("6 x Monkfish", ShadesKillerScript.missingSupply(true, true, true, "barrows teleport", "varrock teleport", "Monkfish", 6));
	}

	@Test
	void successfulWithdrawResetsTheFailedVisitCount()
	{
		int attempts = ShadesKillerScript.failedWithdrawAttemptsAfter(0, false);
		attempts = ShadesKillerScript.failedWithdrawAttemptsAfter(attempts, false);
		assertFalse(ShadesKillerScript.shouldStopAfterFailedWithdraws(attempts));
		attempts = ShadesKillerScript.failedWithdrawAttemptsAfter(attempts, true);
		assertEquals(0, attempts);
		attempts = ShadesKillerScript.failedWithdrawAttemptsAfter(attempts, false);
		assertFalse(ShadesKillerScript.shouldStopAfterFailedWithdraws(attempts));
	}

	@Test
	void missingCoffinIsDecidedOnlyFromAnOpenBank()
	{
		assertEquals(ShadesKillerScript.CoffinStep.SKIP_NO_COFFIN, ShadesKillerScript.coffinStep(false, true, false));
		assertEquals(ShadesKillerScript.CoffinStep.REOPEN_BANK, ShadesKillerScript.coffinStep(false, false, false));
		assertEquals(ShadesKillerScript.CoffinStep.REOPEN_BANK, ShadesKillerScript.coffinStep(false, false, true));
		assertEquals(ShadesKillerScript.CoffinStep.WITHDRAW_FROM_BANK, ShadesKillerScript.coffinStep(false, true, true));
		assertEquals(ShadesKillerScript.CoffinStep.EMPTY, ShadesKillerScript.coffinStep(true, false, false));
		assertEquals(ShadesKillerScript.CoffinStep.EMPTY, ShadesKillerScript.coffinStep(true, true, false));
	}

	@Test
	void coffinDialogThatNeverOpensIsGivenUpAfterThreeClicks()
	{
		int misses = 0;
		int clicks = 0;
		while (!ShadesKillerScript.shouldGiveUpEmptyingCoffin(misses) && clicks < 50)
		{
			misses = ShadesKillerScript.coffinDialogMissesAfter(misses, true, false);
			clicks++;
		}
		assertEquals(3, clicks);
	}

	@Test
	void coffinDialogMissesOnlyCountDispatchedClicksAndResetWhenTheDialogOpens()
	{
		assertEquals(2, ShadesKillerScript.coffinDialogMissesAfter(2, false, false));
		assertFalse(ShadesKillerScript.shouldGiveUpEmptyingCoffin(ShadesKillerScript.coffinDialogMissesAfter(2, false, false)));
		assertEquals(0, ShadesKillerScript.coffinDialogMissesAfter(2, true, true));
	}
}
