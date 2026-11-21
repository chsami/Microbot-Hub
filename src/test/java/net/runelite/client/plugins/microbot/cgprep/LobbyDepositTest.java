package net.runelite.client.plugins.microbot.cgprep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.api.ChatMessageType;
import org.junit.jupiter.api.Test;

class LobbyDepositTest
{
	@Test
	void keepsTheLootAndGearWhenTheGameDoesNotRefuse()
	{
		// 1.8.5: no banking every run (looked scripted); runs with loot in the bag get in fine
		assertFalse(LobbyDeposit.needed(false, 24, 5, 0, 4, false, false));
		assertFalse(LobbyDeposit.needed(false, 28, 5, 0, 0, true, false));
	}

	@Test
	void banksAfterTheGameRefusesEntry()
	{
		assertTrue(LobbyDeposit.needed(false, 24, 5, 0, 4, false, true));
		assertTrue(LobbyDeposit.needed(false, 28, 5, 0, 0, true, true));
	}

	@Test
	void goesStraightInWithNothingCarried()
	{
		assertFalse(LobbyDeposit.needed(false, 28, 5, 0, 0, false, true));
		assertFalse(LobbyDeposit.needed(false, 28, 5, 0, 0, false, false));
	}

	@Test
	void opensTheChestFirstWhileThereIsRoom()
	{
		assertFalse(LobbyDeposit.needed(true, 24, 5, 0, 4, false, false));
	}

	@Test
	void makesRoomBeforeTheChestWhenTheBagIsNearlyFull()
	{
		assertTrue(LobbyDeposit.needed(true, 4, 5, 0, 24, false, false));
		assertTrue(LobbyDeposit.needed(true, 7, 5, 2, 21, false, false));
		assertFalse(LobbyDeposit.needed(true, 7, 5, 1, 21, false, false));
	}

	@Test
	void wornItemsOnlyGoInAfterARefusal()
	{
		assertFalse(LobbyDeposit.depositWorn(false, true));
		assertTrue(LobbyDeposit.depositWorn(true, true));
		assertFalse(LobbyDeposit.depositWorn(true, false));
	}

	@Test
	void recognisesTheEntryRefusal()
	{
		assertTrue(LobbyDeposit.isEntryRefusal("You cannot keep any items whilst you are in the Gauntlet. You may deposit your items in the nearby bank deposit box, or store them here."));
		assertTrue(LobbyDeposit.isEntryRefusal("You cannot keep any items whilst you are<br>in the Gauntlet."));
		assertFalse(LobbyDeposit.isEntryRefusal("Your reward awaits you in the nearby chest."));
		assertFalse(LobbyDeposit.isEntryRefusal(null));
	}

	@Test
	void stopsAfterThreeDepositsThatLeaveItemsBehind()
	{
		assertFalse(LobbyDeposit.giveUp(0));
		assertFalse(LobbyDeposit.giveUp(2));
		assertTrue(LobbyDeposit.giveUp(3));
		assertTrue(LobbyDeposit.giveUp(4));
	}

	@Test
	void readsTheRefusalFromGameTextOnly()
	{
		assertTrue(LobbyDeposit.isGameText(ChatMessageType.GAMEMESSAGE));
		assertTrue(LobbyDeposit.isGameText(ChatMessageType.MESBOX));
		assertTrue(LobbyDeposit.isGameText(ChatMessageType.DIALOG));
		assertTrue(LobbyDeposit.isGameText(ChatMessageType.ENGINE));
		assertTrue(LobbyDeposit.isGameText(ChatMessageType.SPAM));
		assertFalse(LobbyDeposit.isGameText(ChatMessageType.PUBLICCHAT));
	}
}
