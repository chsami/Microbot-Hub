package net.runelite.client.plugins.microbot.cgprep;

final class LobbyDeposit
{
	static final String ENTRY_REFUSED = "you cannot keep any items";
	static final int MAX_TRIES = 3;

	private LobbyDeposit()
	{
	}

	static boolean isEntryRefusal(String message)
	{
		return message != null && message.toLowerCase().contains(ENTRY_REFUSED);
	}

	static boolean giveUp(int fails)
	{
		return fails >= MAX_TRIES;
	}

	static boolean isGameText(net.runelite.api.ChatMessageType type)
	{
		return type == net.runelite.api.ChatMessageType.GAMEMESSAGE
			|| type == net.runelite.api.ChatMessageType.SPAM
			|| type == net.runelite.api.ChatMessageType.ENGINE
			|| type == net.runelite.api.ChatMessageType.MESBOX
			|| type == net.runelite.api.ChatMessageType.DIALOG;
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
