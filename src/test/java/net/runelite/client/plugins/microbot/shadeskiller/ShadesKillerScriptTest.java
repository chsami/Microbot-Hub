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
	void missingSupplyNamesTheFirstRequirementThatIsNotCarried()
	{
		assertEquals("a silver key", ShadesKillerScript.missingSupply(false, false, false, "barrows teleport", "varrock teleport", "Monkfish", 6));
		assertEquals("barrows teleport", ShadesKillerScript.missingSupply(true, false, false, "barrows teleport", "varrock teleport", "Monkfish", 6));
		assertEquals("varrock teleport", ShadesKillerScript.missingSupply(true, true, false, "barrows teleport", "varrock teleport", "Monkfish", 6));
		assertEquals("6 x Monkfish", ShadesKillerScript.missingSupply(true, true, true, "barrows teleport", "varrock teleport", "Monkfish", 6));
	}

	@Test
	void retryLimitsAreBounded()
	{
		assertEquals(3, ShadesKillerScript.MAX_FAILED_WITHDRAW_ATTEMPTS);
		assertEquals(3, ShadesKillerScript.MAX_COFFIN_DIALOG_MISSES);
	}
}
