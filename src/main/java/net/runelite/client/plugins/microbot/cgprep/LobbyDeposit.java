package net.runelite.client.plugins.microbot.cgprep;

final class LobbyDeposit
{
	static final String ENTRY_REFUSED = "you cannot keep any items";

	private LobbyDeposit()
	{
	}

	static boolean isEntryRefusal(String message)
	{
		return message != null && message.toLowerCase().contains(ENTRY_REFUSED);
	}

	static boolean needed(boolean reward, int free, int bankBelowFreeSlots, int chestFails, int carried, boolean wearing)
	{
		if (reward)
		{
			return free < bankBelowFreeSlots || (chestFails >= 2 && free < 8);
		}
		return carried > 0 || wearing;
	}
}
