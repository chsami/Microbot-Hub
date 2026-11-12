package net.runelite.client.plugins.microbot.cgprep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LobbyDepositTest
{
	@Test
	void depositsLootLeftInTheBagOnceTheChestIsEmptied()
	{
		assertTrue(LobbyDeposit.needed(false, 24, 5, 0, 4, false));
	}

	@Test
	void depositsWornItemsBeforeGoingIn()
	{
		assertTrue(LobbyDeposit.needed(false, 28, 5, 0, 0, true));
	}

	@Test
	void goesStraightInWithNothingCarried()
	{
		assertFalse(LobbyDeposit.needed(false, 28, 5, 0, 0, false));
	}

	@Test
	void opensTheChestFirstWhileThereIsRoom()
	{
		assertFalse(LobbyDeposit.needed(true, 24, 5, 0, 4, false));
	}

	@Test
	void makesRoomBeforeTheChestWhenTheBagIsNearlyFull()
	{
		assertTrue(LobbyDeposit.needed(true, 4, 5, 0, 24, false));
		assertTrue(LobbyDeposit.needed(true, 7, 5, 2, 21, false));
		assertFalse(LobbyDeposit.needed(true, 7, 5, 1, 21, false));
	}

	@Test
	void recognisesTheEntryRefusal()
	{
		assertTrue(LobbyDeposit.isEntryRefusal("You cannot keep any items whilst you are in the Gauntlet. You may deposit your items in the nearby bank deposit box, or store them here."));
		assertTrue(LobbyDeposit.isEntryRefusal("You cannot keep any items whilst you are<br>in the Gauntlet."));
		assertFalse(LobbyDeposit.isEntryRefusal("Your reward awaits you in the nearby chest."));
		assertFalse(LobbyDeposit.isEntryRefusal(null));
	}
}
